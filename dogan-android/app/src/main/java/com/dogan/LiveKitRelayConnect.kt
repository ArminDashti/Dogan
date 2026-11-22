package com.dogan

import io.livekit.android.ConnectOptions
import io.livekit.android.LiveKit
import io.livekit.android.util.LKLog
import io.livekit.android.util.LoggingLevel
import livekit.org.webrtc.PeerConnection

/**
 * Single-signal join with relay-only ICE servers supplied by the API.
 *
 * The SDK converts join-response ICE with a top-level `toWebrtc` function
 * (`io.livekit.android.room.RTCEngineKt.toWebrtc`, verified in livekit-android 2.27.0 bytecode),
 * so join ICE cannot carry a per-host TLS policy. Supplying `ConnectOptions.rtcConfig` replaces the
 * join ICE list entirely; `ConnectOptions.iceServers` is merged into it.
 */
object LiveKitRelayConnect {
    fun isProductionPath(signalingUrl: String, streamPort: Int): Boolean {
        val normalized = signalingUrl.trim().lowercase()
        return streamPort == 443 ||
            normalized.startsWith("wss://") ||
            normalized.contains(":443")
    }

    fun connectOptions(
        signalingUrl: String,
        streamPort: Int,
        iceServers: List<PeerConnection.IceServer>,
    ): ConnectOptions {
        if (iceServers.isEmpty()) {
            AppLogger.warn(
                "LiveKit",
                "API returned no ICE servers; falling back to SDK join ICE (dogan-turn TLS may be rejected)",
            )
            return ConnectOptions()
        }
        AppLogger.info(
            "LiveKit",
            "ICE servers=${iceServers.size} production=${isProductionPath(signalingUrl, streamPort)} mode=relay",
        )
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceTransportsType = PeerConnection.IceTransportsType.RELAY
        }
        return ConnectOptions(iceServers = iceServers, rtcConfig = rtcConfig)
    }

    private const val LIVEKIT_LOG_TAG = "LiveKit"

    private val iceDiagnosticsPattern =
        Regex("""(?i)ice|candidate|relay|turn|stun|pc connection|peer connection|transport|ssl""")

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
