package com.dogan

import io.livekit.android.ConnectOptions
import io.livekit.android.LiveKit
import io.livekit.android.util.LKLog
import io.livekit.android.util.LoggingLevel
import livekit.org.webrtc.PeerConnection

/**
 * LiveKit Android SDK 2.27.x merges server ICE/TURN from the join response with [ConnectOptions.rtcConfig].
 * When [PeerConnection.RTCConfiguration.iceServers] is empty, [io.livekit.android.room.RTCEngine.makeRTCConfig]
 * fills in the server-provided list (it does not wipe TURN creds from the token).
 */
object LiveKitRelayConnect {
    private val relayOnlyRtc: PeerConnection.RTCConfiguration =
        PeerConnection.RTCConfiguration(emptyList()).apply {
            iceTransportsType = PeerConnection.IceTransportsType.RELAY
        }

    fun connectOptions(): ConnectOptions = ConnectOptions(rtcConfig = relayOnlyRtc)

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