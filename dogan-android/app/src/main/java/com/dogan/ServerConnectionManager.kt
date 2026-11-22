package com.dogan

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Runtime server connection state, separate from persisted settings. */
enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    FAILED,
}

object ServerConnectionManager {
    private val _status = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    @Volatile
    var lastError: String? = null
        private set

    fun connect(context: Context, settings: AppSettings): Boolean {
        _status.value = ConnectionStatus.CONNECTING
        lastError = null
        StreamStatusCache.setError(null)
        SessionCredentials.updateFrom(settings)
        AppLogger.info("Server", "Connecting to ${settings.serverBaseUrl}")

        if (settings.username.isBlank() || settings.password.isBlank()) {
            _status.value = ConnectionStatus.FAILED
            lastError = "Username and password are required"
            AppLogger.error("Server", lastError!!)
            return false
        }

        val deviceId = DeviceIdentity.resolveDeviceId(context)
        val client = DoganApiClient(deviceId = deviceId)
        val probe = client.connectWithApiAndStreaming(settings.serverBaseUrl)

        if (!probe.apiOk) {
            _status.value = ConnectionStatus.FAILED
            lastError = probe.error ?: "Authentication or health check failed"
            StreamStatusCache.clear()
            AppLogger.error("Server", lastError!!)
            client.consumeSslWarning()?.let { warning ->
                AppLogger.warn("SSL", warning)
                if (lastError?.contains("SSL", ignoreCase = true) != true) {
                    lastError = listOfNotNull(lastError, warning).joinToString(" — ")
                }
            }
            return false
        }

        _status.value = ConnectionStatus.CONNECTED
        AppLogger.info("Server", "API connected")
        ServerSettingsSync.start(context)
        refreshPing(context, settings)
        refreshStreamingStatus(client, settings.serverBaseUrl, probe.streamingOk, probe.error)
        client.consumeSslWarning()?.let { warning ->
            AppLogger.warn("SSL", warning)
        }
        if (!probe.streamingOk) {
            lastError = probe.error ?: "Streaming is not available"
            AppLogger.warn("Server", lastError!!)
        }
        return true
    }

    fun disconnect() {
        ServerSettingsSync.stop()
        _status.value = ConnectionStatus.DISCONNECTED
        lastError = null
        SessionCredentials.clearToken()
        PingLatencyCache.clear()
        StreamStatusCache.clear()
        AppLogger.info("Server", "Disconnected")
    }

    fun isConnected(): Boolean = _status.value == ConnectionStatus.CONNECTED

    fun refreshPing(context: Context, settings: AppSettings) {
        Thread {
            val host = EndpointUrlBuilder.parseHostFromUrl(settings.serverBaseUrl)
            val icmp = PingHelper.pingAverageMs(host, 8)
            if (icmp >= 0) {
                PingLatencyCache.update(icmp)
                return@Thread
            }
            SessionCredentials.updateFrom(settings)
            val client = DoganApiClient(deviceId = DeviceIdentity.resolveDeviceId(context))
            val health = client.pingHealthWithLatency(settings.serverBaseUrl)
            if (health?.success == true) {
                PingLatencyCache.update(health.latencyMs)
            }
        }.start()
    }

    fun refreshStreamingConnections(context: Context, settings: AppSettings) {
        if (!isConnected()) return
        Thread {
            SessionCredentials.updateFrom(settings)
            val client = DoganApiClient(deviceId = DeviceIdentity.resolveDeviceId(context))
            refreshStreamingStatus(client, settings.serverBaseUrl, streamingOk = null, error = null)
        }.start()
    }

    private fun refreshStreamingStatus(
        client: DoganApiClient,
        baseUrl: String,
        streamingOk: Boolean?,
        error: String?,
    ) {
        if (streamingOk == false) {
            StreamStatusCache.setError(error)
        }
        val connections = client.listWebRtcConnections(baseUrl)
        val active = connections.count { it.isActive }
        StreamStatusCache.setActiveSessionCount(active)
        if (streamingOk == true || StreamStatusCache.streaming.value || active > 0) {
            StreamStatusCache.setError(null)
        } else if (error != null) {
            StreamStatusCache.setError(error)
        }
    }
}
