package com.dogan

import io.livekit.android.ConnectOptions
import io.livekit.android.LiveKit
import io.livekit.android.util.LKLog
import io.livekit.android.util.LoggingLevel

/**
 * Single signal join: leave [ConnectOptions.rtcConfig] null so SDK merges join ICE (with dogan-turn TLS
 * via [io.livekit.android.room.toWebrtc]). Production wss should use relay candidates from TURN.
 */
object LiveKitRelayConnect {
    fun isProductionPath(signalingUrl: String, streamPort: Int): Boolean {
        val normalized = signalingUrl.trim().lowercase()
        return streamPort == 443 ||
            normalized.startsWith("wss://") ||
            normalized.contains(":443")
    }

    fun connectOptions(signalingUrl: String, streamPort: Int): ConnectOptions {
        return ConnectOptions()
    }

    private const val LIVEKIT_LOG_TAG = "LiveKit"

    private val iceDiagnosticsPattern =
        Regex("""(?i)ice|candidate|relay|turn|stun|pc connection|peer connection|transport|ssl""")

    fun enableDiagnosticsLogging() {
        LiveKitIceDiagnostics.reset()
        LiveKit.loggingLevel = LoggingLevel.INFO
        LKLog.logger = object : LKLog.Logger {
            override fun log(priority: LoggingLevel, t: Throwable?, message: String) {
                if (!iceDiagnosticsPattern.containsMatchIn(message)) {
                    return
                }
                LiveKitIceDiagnostics.logIceLogLine(message)
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