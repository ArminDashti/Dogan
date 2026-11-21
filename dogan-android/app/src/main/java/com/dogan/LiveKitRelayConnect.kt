package com.dogan

import io.livekit.android.ConnectOptions
import io.livekit.android.LiveKit
import io.livekit.android.util.LKLog
import io.livekit.android.util.LoggingLevel
import livekit.org.webrtc.PeerConnection

/**
 * Relay-only RTC config for production wss. When [joinIceServers] is supplied (from signal join),
 * pass them via [ConnectOptions.iceServers] so TURN creds and TLS overrides apply; never use REST
 * URL-only ICE without join credentials.
 */
object LiveKitRelayConnect {
    private val relayOnlyRtc: PeerConnection.RTCConfiguration =
        PeerConnection.RTCConfiguration(emptyList()).apply {
            iceTransportsType = PeerConnection.IceTransportsType.RELAY
        }

    private val allTransportsRtc: PeerConnection.RTCConfiguration =
        PeerConnection.RTCConfiguration(emptyList()).apply {
            iceTransportsType = PeerConnection.IceTransportsType.ALL
        }

    /**
     * Use relay-only on production-style signaling (wss / port 443) where UDP host candidates fail.
     * Use all transports for LAN/emulator hosts (e.g. 10.0.2.2:7880).
     */
    fun connectOptions(
        signalingUrl: String,
        streamPort: Int,
        joinIceServers: List<PeerConnection.IceServer>? = null,
    ): ConnectOptions {
        val normalized = signalingUrl.trim().lowercase()
        val productionPath = streamPort == 443 ||
            normalized.startsWith("wss://") ||
            normalized.contains(":443")
        val rtc = if (productionPath) relayOnlyRtc else allTransportsRtc
        val useJoinIce = productionPath && !joinIceServers.isNullOrEmpty()
        return if (useJoinIce) {
            // Non-empty iceServers: RTCEngine.makeRTCConfig uses these instead of default toWebrtc() (no TLS override).
            ConnectOptions(rtcConfig = rtc, iceServers = joinIceServers)
        } else {
            ConnectOptions(rtcConfig = rtc)
        }
    }

    private const val LIVEKIT_LOG_TAG = "LiveKit"

    private val iceDiagnosticsPattern =
        Regex("""(?i)ice|candidate|relay|turn|stun|pc connection|peer connection|transport""")

    fun enableDiagnosticsLogging() {
        LiveKit.loggingLevel = LoggingLevel.INFO
        LKLog.logger = object : LKLog.Logger {
            override fun log(priority: LoggingLevel, t: Throwable?, message: String) {
                if (!iceDiagnosticsPattern.containsMatchIn(message)) {
                    return
                }
                val line = if (t != null) {
                    "$message (${t.javaClass.simpleName}: ${t.message})"
                } else {
                    message
                }
                when (priority) {
                    LoggingLevel.WARN, LoggingLevel.ERROR, LoggingLevel.WTF ->
                        AppLogger.warn(LIVEKIT_LOG_TAG, line)

                    else -> AppLogger.info(LIVEKIT_LOG_TAG, line)
                }
            }
        }
    }
}