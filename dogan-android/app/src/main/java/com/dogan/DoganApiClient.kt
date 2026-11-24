package com.dogan

import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * HTTP client for the Dogan API only (auth, telemetry, sounds, diagnostics, streaming session).
 *
 * Camera frames leave the phone via [submitTelemetry] → `POST {base}/api/v1/telemetry`.
 * Do not call LLM providers from Android; the Dogan API forwards stored frames server-side.
 */
class DoganApiClient(
    private val httpClient: OkHttpClient = createHttpClient(),
    private val deviceId: String,
) {
    @Volatile private var lastSslWarning: String? = null

    fun testConnection(baseUrl: String): Boolean {
        if (!isValidBaseUrl(baseUrl)) return false
        return authenticate(baseUrl)
    }

    /** Authenticate and verify the same bearer works for streaming session creation. */
    fun connectWithApiAndStreaming(baseUrl: String): ConnectProbeResult {
        if (!isValidBaseUrl(baseUrl)) {
            return ConnectProbeResult(false, false, "Invalid server URL")
        }
        val auth = authenticateDetailed(baseUrl)
        if (!auth.success) {
            return ConnectProbeResult(false, false, auth.error ?: "Authentication failed")
        }
        registerDevice(baseUrl)
        val streaming = probeStreamingAvailable(baseUrl)
        return ConnectProbeResult(
            apiOk = true,
            streamingOk = streaming.success,
            error = if (streaming.success) null else streaming.error,
        )
    }

    fun pingHealthWithLatency(baseUrl: String): HealthResult? {
        if (!isValidBaseUrl(baseUrl)) return null
        val start = System.currentTimeMillis()
        val request = Request.Builder()
            .url("$baseUrl/api/v1/health")
            .get()
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - start
                val body = response.body?.string().orEmpty()
                var streamingConfigured = false
                var turnDomain = ""
                if (body.isNotBlank()) {
                    try {
                        val json = JSONObject(body)
                        streamingConfigured = json.optBoolean("streaming_configured", false)
                        turnDomain = json.optString("turn_domain", "")
                    } catch (_: Exception) {
                        Unit
                    }
                }
                HealthResult(
                    success = response.isSuccessful,
                    latencyMs = latency,
                    httpCode = response.code,
                    streamingConfigured = streamingConfigured,
                    turnDomain = turnDomain,
                )
            }
        } catch (e: Exception) {
            logSslWarningIfNeeded(e)
            HealthResult(success = false, latencyMs = System.currentTimeMillis() - start, error = e.message)
        }
    }

    fun authenticateWithResult(baseUrl: String): AuthResult {
        if (!isValidBaseUrl(baseUrl)) return AuthResult(false, error = "Invalid base URL")
        val start = System.currentTimeMillis()
        val detailed = authenticateDetailed(baseUrl)
        return AuthResult(
            success = detailed.success,
            latencyMs = System.currentTimeMillis() - start,
            token = if (detailed.success) SessionCredentials.bearerToken else null,
            error = detailed.error,
        )
    }

    fun listWebRtcConnections(baseUrl: String): List<WebRtcConnectionInfo> {
        if (!ensureAuthenticated(baseUrl)) return emptyList()
        val json = getAuthenticatedObject(baseUrl, "/api/v1/webrtc/connections?device-id=$deviceId")
            ?: return emptyList()
        val array = json.optJSONArray("connections") ?: return emptyList()
        val result = ArrayList<WebRtcConnectionInfo>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            result.add(
                WebRtcConnectionInfo(
                    id = item.optLong("id"),
                    deviceId = item.optString("device_id"),
                    room = item.optString("room"),
                    identity = item.optString("identity"),
                    role = item.optString("role"),
                    status = item.optString("status"),
                    disconnectedAt = item.optString("disconnected_at").takeIf { it.isNotBlank() },
                ),
            )
        }
        return result
    }

    fun submitTelemetry(baseUrl: String, payload: JSONObject): Boolean {
        if (!ensureAuthenticated(baseUrl)) {
            AppLogger.warn("Telemetry", "Upload skipped: not authenticated")
            return false
        }
        val response = postAuthenticatedWithBody(baseUrl, "/api/v1/telemetry", payload.toString())
        if (response.result == PostResult.Success) return true
        val detail = response.body?.take(200)?.ifBlank { null } ?: "HTTP failure"
        val rearB = payload.optString("rear_camera_frame_base64").length
        val frontB = payload.optString("front_camera_frame_base64").length
        AppLogger.warn(
            "Telemetry",
            "Upload failed for device $deviceId: ${response.result} — $detail (body≈${payload.length()}b frames rear=${rearB} front=${frontB})",
        )
        return false
    }

    /** Ensures a row exists in the fleet `devices` table (`POST /api/v1/devices/register`). */
    fun registerDevice(baseUrl: String): Boolean {
        if (!ensureAuthenticated(baseUrl)) return false
        val payload = JSONObject().put("device_id", deviceId).toString()
        return postAuthenticated(baseUrl, "/api/v1/devices/register", payload) == PostResult.Success
    }

    fun fetchSoundsManifest(baseUrl: String): JSONArray? {
        if (!ensureAuthenticated(baseUrl)) return null
        return getAuthenticatedJson(baseUrl, "/api/v1/sounds")
    }

    fun fetchSettings(baseUrl: String): JSONObject? {
        if (!ensureAuthenticated(baseUrl)) return null
        return getAuthenticatedObject(baseUrl, "/api/v1/settings?device_id=$deviceId")
    }

    /** Upsert a single android setting on the server (`PUT /api/v1/settings`). */
    fun putSetting(baseUrl: String, key: String, value: Any?): Boolean {
        if (!ensureAuthenticated(baseUrl)) return false
        val payload = JSONObject()
            .put("platform", "android")
            .put("key", key)
            .put("value", value?.toString() ?: "")
            .toString()
        val first = executePut(baseUrl, "/api/v1/settings", payload, SessionCredentials.bearerToken)
        if (first == PostResult.Success) return true
        if (first != PostResult.Unauthorized) return false
        clearToken()
        if (!authenticate(baseUrl)) return false
        return executePut(baseUrl, "/api/v1/settings", payload, SessionCredentials.bearerToken) == PostResult.Success
    }

    fun downloadFile(baseUrl: String, url: String): ByteArray? {
        if (!ensureAuthenticated(baseUrl)) return null
        val token = SessionCredentials.bearerToken ?: return null
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.bytes()
            }
        } catch (_: Exception) {
            null
        }
    }

    fun createWebRtcSession(baseUrl: String): WebRtcSessionResult? {
        if (!ensureAuthenticated(baseUrl)) return null
        val payload = JSONObject().put("device_id", deviceId).toString()
        val result = postAuthenticatedWithBody(baseUrl, "/api/v1/webrtc/session", payload)
        if (result.body.isNullOrBlank()) return null
        return try {
            val json = JSONObject(result.body!!)
            WebRtcSessionResult(
                sessionId = json.optString("session_id"),
                token = json.optString("token"),
                whipUrl = json.optString("whip_url"),
                websocketPublishUrl = json.optString("websocket_publish_url"),
                path = json.optString("path"),
                url = json.optString("url"),
                room = json.optString("room"),
                identity = json.optString("identity"),
                iceServers = json.optJSONArray("ice_servers"),
            )
        } catch (_: Exception) {
            null
        }
    }

    fun fetchPendingActions(baseUrl: String): JSONArray? {
        if (!ensureAuthenticated(baseUrl)) return null
        // Server expects device-id and returns {"actions":[...]}.
        return getAuthenticatedJson(baseUrl, "/api/v1/actions/pending?device-id=$deviceId")
    }

    /** Mark a pending phone action as finished (`PUT /api/v1/actions/:id/ack`). */
    fun acknowledgeAction(baseUrl: String, actionId: Long, status: String = "done"): Boolean {
        if (actionId <= 0L) return false
        if (!ensureAuthenticated(baseUrl)) return false
        val payload = JSONObject().put("status", status).toString()
        val path = "/api/v1/actions/$actionId/ack"
        val first = executePut(baseUrl, path, payload, SessionCredentials.bearerToken)
        if (first == PostResult.Success) return true
        if (first != PostResult.Unauthorized) return false
        clearToken()
        if (!authenticate(baseUrl)) return false
        return executePut(baseUrl, path, payload, SessionCredentials.bearerToken) == PostResult.Success
    }

    fun submitDiagnosticAudio(
        baseUrl: String,
        wavFile: File,
        metadata: JSONObject,
    ): Boolean {
        if (!ensureAuthenticated(baseUrl)) return false
        val token = SessionCredentials.bearerToken ?: return false
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("metadata", metadata.toString())
            .addFormDataPart(
                "audio",
                wavFile.name,
                wavFile.asRequestBody("audio/wav".toMediaType()),
            )
            .build()
        val request = Request.Builder()
            .url("$baseUrl/api/v1/diagnostic-audio")
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()
        return try {
            httpClient.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    fun getBearerToken(): String? = SessionCredentials.bearerToken

    fun consumeSslWarning(): String? {
        val warning = lastSslWarning
        lastSslWarning = null
        return warning
    }

    private fun postAuthenticated(baseUrl: String, path: String, body: String): PostResult {
        val firstAttempt = executePost(baseUrl, path, body, SessionCredentials.bearerToken)
        if (firstAttempt.result != PostResult.Unauthorized) return firstAttempt.result
        clearToken()
        if (!authenticate(baseUrl)) return PostResult.Failed
        return executePost(baseUrl, path, body, SessionCredentials.bearerToken).result
    }

    private fun postAuthenticatedWithBody(baseUrl: String, path: String, body: String): PostResponse {
        val first = executePost(baseUrl, path, body, SessionCredentials.bearerToken)
        if (first.result != PostResult.Unauthorized) return first
        clearToken()
        if (!authenticate(baseUrl)) return PostResponse(PostResult.Failed, null)
        return executePost(baseUrl, path, body, SessionCredentials.bearerToken)
    }

    private fun getAuthenticatedArray(baseUrl: String, path: String): JSONArray? {
        val token = SessionCredentials.bearerToken ?: return null
        val request = Request.Builder()
            .url("$baseUrl$path")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                JSONArray(body)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getAuthenticatedJson(baseUrl: String, path: String): JSONArray? {
        val json = getAuthenticatedObject(baseUrl, path) ?: return null
        return when {
            json.has("models") -> json.optJSONArray("models")
            json.has("sounds") -> json.optJSONArray("sounds")
            json.optJSONArray("actions") != null -> json.optJSONArray("actions")
            else -> JSONArray(json.toString())
        }
    }

    private fun getAuthenticatedObject(baseUrl: String, path: String): JSONObject? {
        val token = SessionCredentials.bearerToken ?: return null
        val request = Request.Builder()
            .url("$baseUrl$path")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                JSONObject(body)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun pingHealth(baseUrl: String): HealthResult? = pingHealthWithLatency(baseUrl)

    private fun isValidBaseUrl(baseUrl: String): Boolean {
        val trimmed = baseUrl.trim()
        return trimmed.startsWith("http://") || trimmed.startsWith("https://")
    }

    private fun ensureAuthenticated(baseUrl: String): Boolean {
        if (SessionCredentials.hasFreshToken(TOKEN_REFRESH_LEAD_MS)) return true
        return authenticate(baseUrl)
    }

    private fun authenticate(baseUrl: String): Boolean = authenticateDetailed(baseUrl).success

    private fun authenticateDetailed(baseUrl: String): AuthResult {
        val username = SessionCredentials.username
        val password = SessionCredentials.password
        if (username.isBlank() || password.isBlank()) {
            return AuthResult(false, error = "Username and password are required")
        }
        val payload = JSONObject()
            .put("username", username)
            .put("password", password)
            .put("device_id", deviceId)
            .toString()
        val request = Request.Builder()
            .url("$baseUrl/api/v1/auth")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val serverError = parseErrorMessage(responseBody)
                    return AuthResult(
                        false,
                        error = serverError ?: "Authentication failed (HTTP ${response.code})",
                    )
                }
                if (responseBody.isBlank()) {
                    return AuthResult(false, error = "Empty authentication response")
                }
                val json = JSONObject(responseBody)
                val token = json.getString("token")
                val expiresAt = parseExpiresAt(json.optString("expires_at"))
                SessionCredentials.applyAuthToken(token, expiresAt)
                AuthResult(true, token = token)
            }
        } catch (e: Exception) {
            logSslWarningIfNeeded(e)
            AuthResult(false, error = e.message ?: "Authentication request failed")
        }
    }

    private fun probeStreamingAvailable(baseUrl: String): AuthResult {
        return try {
            val health = pingHealthWithLatency(baseUrl)
            when {
                health == null -> AuthResult(false, error = "Health check failed")
                !health.success -> AuthResult(false, error = "Health check HTTP ${health.httpCode}")
                !health.streamingConfigured ->
                    AuthResult(false, error = "MediaMTX streaming is not configured on the server")
                else -> AuthResult(true)
            }
        } catch (e: Exception) {
            AuthResult(false, error = e.message ?: "Streaming probe failed")
        }
    }

    private fun parseErrorMessage(body: String): String? {
        if (body.isBlank()) return null
        return try {
            JSONObject(body).optString("error").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun executePost(baseUrl: String, path: String, body: String, token: String?): PostResponse {
        if (token.isNullOrBlank()) return PostResponse(PostResult.Unauthorized, null)
        val request = Request.Builder()
            .url("$baseUrl$path")
            .header("Authorization", "Bearer $token")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                val result = when {
                    response.isSuccessful -> PostResult.Success
                    response.code == 401 -> PostResult.Unauthorized
                    else -> PostResult.Failed
                }
                PostResponse(result, responseBody)
            }
        } catch (e: Exception) {
            logSslWarningIfNeeded(e)
            PostResponse(PostResult.Failed, null)
        }
    }

    private fun executePut(baseUrl: String, path: String, body: String, token: String?): PostResult {
        if (token.isNullOrBlank()) return PostResult.Unauthorized
        val request = Request.Builder()
            .url("$baseUrl$path")
            .header("Authorization", "Bearer $token")
            .put(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> PostResult.Success
                    response.code == 401 -> PostResult.Unauthorized
                    else -> PostResult.Failed
                }
            }
        } catch (e: Exception) {
            logSslWarningIfNeeded(e)
            PostResult.Failed
        }
    }

    private fun logSslWarningIfNeeded(error: Exception) {
        val message = error.message.orEmpty()
        if (message.contains("SSL", ignoreCase = true) ||
            message.contains("certificate", ignoreCase = true) ||
            message.contains("Trust anchor", ignoreCase = true)
        ) {
            lastSslWarning = message
            AppLogger.warn("SSL", "Certificate warning: $message")
        }
    }

    private fun parseExpiresAt(expiresAt: String): Long {
        if (expiresAt.isBlank()) return System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)
        return try {
            Instant.parse(expiresAt).toEpochMilli()
        } catch (_: Exception) {
            System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)
        }
    }

    private fun clearToken() {
        SessionCredentials.clearToken()
    }

    private enum class PostResult { Success, Unauthorized, Failed }

    data class HealthResult(
        val success: Boolean,
        val latencyMs: Long,
        val httpCode: Int = 0,
        val error: String? = null,
        val streamingConfigured: Boolean = false,
        val turnDomain: String = "",
    )

    data class AuthResult(
        val success: Boolean,
        val latencyMs: Long = 0,
        val token: String? = null,
        val error: String? = null,
    )

    data class ConnectProbeResult(
        val apiOk: Boolean,
        val streamingOk: Boolean,
        val error: String? = null,
    ) {
        val success: Boolean get() = apiOk && streamingOk
    }

    data class WebRtcConnectionInfo(
        val id: Long,
        val deviceId: String,
        val room: String,
        val identity: String,
        val role: String,
        val status: String,
        val disconnectedAt: String?,
    ) {
        val isActive: Boolean
            get() = disconnectedAt.isNullOrBlank() &&
                (status.equals("active", ignoreCase = true) ||
                    status.equals("connected", ignoreCase = true) ||
                    status.isBlank())
    }

    data class WebRtcSessionResult(
        val sessionId: String,
        val token: String,
        val whipUrl: String,
        val websocketPublishUrl: String,
        val path: String,
        val url: String,
        val room: String,
        val identity: String,
        val iceServers: JSONArray?,
    )

    private data class PostResponse(val result: PostResult, val body: String?)

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private const val TOKEN_REFRESH_LEAD_MS = 5 * 60 * 1000L

        private fun createHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .sslSocketFactory(
                    SslWarningTrustManager.createSocketFactory(),
                    object : javax.net.ssl.X509TrustManager {
                        override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) = Unit
                        override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) = Unit
                        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
                    },
                )
                .hostnameVerifier(SslWarningTrustManager.hostnameVerifier)
                .build()
        }
    }
}
