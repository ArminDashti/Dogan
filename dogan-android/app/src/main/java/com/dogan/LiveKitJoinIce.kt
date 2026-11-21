package com.dogan

import io.livekit.android.ConnectOptions
import io.livekit.android.RoomOptions
import io.livekit.android.util.toWebsocketUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import livekit.LivekitRtc
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * One-shot signal join to read TURN credentials from [LivekitRtc.JoinResponse.iceServersList].
 * Used because SDK [io.livekit.android.room.RTCEngine] converts join ICE without [PeerConnection.TlsCertPolicy].
 */
object LiveKitJoinIce {
    private const val JOIN_TIMEOUT_MS = 15_000L

    suspend fun fetchIceServersFromJoin(
        signalingUrl: String,
        accessToken: String,
    ): List<livekit.org.webrtc.PeerConnection.IceServer> {
        return withContext(Dispatchers.IO) {
            withTimeout(JOIN_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    val wsUrl = buildRtcWebSocketUrl(signalingUrl)
                    val client = OkHttpClient.Builder()
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(15, TimeUnit.SECONDS)
                        .build()
                    val request = Request.Builder()
                        .url(wsUrl)
                        .addHeader("Authorization", "Bearer $accessToken")
                        .build()
                    val socket = client.newWebSocket(
                        request,
                        object : WebSocketListener() {
                            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                                try {
                                    val response = LivekitRtc.SignalResponse.parseFrom(bytes.toByteArray())
                                    if (!response.hasJoin()) return
                                    webSocket.close(1000, "ice prefetch complete")
                                    val ice = LiveKitTurnIceServers.fromJoinResponse(response.join)
                                    if (ice.isEmpty()) {
                                        cont.resumeWithException(IllegalStateException("join had no ice servers"))
                                    } else {
                                        cont.resume(ice)
                                    }
                                } catch (e: Exception) {
                                    webSocket.close(1000, "ice prefetch failed")
                                    cont.resumeWithException(e)
                                }
                            }

                            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                                cont.resumeWithException(t)
                            }
                        },
                    )
                    cont.invokeOnCancellation { socket.cancel() }
                }
            }
        }
    }

    private fun buildRtcWebSocketUrl(signalingUrl: String): String {
        val connectOptions = ConnectOptions()
        val roomOptions = RoomOptions()
        val base = signalingUrl.trim().trimEnd('/').toWebsocketUrl()
        val autoSubscribe = if (connectOptions.autoSubscribe) 1 else 0
        val adaptiveStream = if (roomOptions.adaptiveStream) 1 else 0
        return buildString {
            append(base)
            append("/rtc?protocol=")
            append(connectOptions.protocolVersion.value)
            append("&auto_subscribe=")
            append(autoSubscribe)
            append("&adaptive_stream=")
            append(adaptiveStream)
            append("&sdk=android")
            append("&version=dogan")
            append("&os=android")
            append("&os_version=")
            append(android.os.Build.VERSION.RELEASE)
            append("&device_model=")
            append(android.os.Build.MODEL)
            append("&client_protocol=")
            append(connectOptions.clientProtocol.value)
        }
    }
}