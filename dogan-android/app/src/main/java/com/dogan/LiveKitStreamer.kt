package com.dogan

import android.content.Context
import io.livekit.android.LiveKit
import io.livekit.android.events.DisconnectReason
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.participant.LocalParticipant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** LiveKit publisher for video-only, audio-only, and video+audio modes. */
class LiveKitStreamer(
    private val context: Context,
    private val apiClient: DoganApiClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _streaming = MutableStateFlow(false)
    val streaming: StateFlow<Boolean> = _streaming

    private var room: Room? = null
    private var eventsJob: Job? = null
    private var restartJob: Job? = null

    @Volatile
    private var userStopRequested = false

    private var lastBaseUrl: String = ""
    private var lastStreamMode: StreamMode = StreamMode.VIDEO_ONLY
    private var lastStreamUrl: String = ""

    fun start(baseUrl: String, streamMode: StreamMode, streamUrl: String = "") {
        userStopRequested = false
        restartJob?.cancel()
        restartJob = null
        lastBaseUrl = baseUrl
        lastStreamMode = streamMode
        lastStreamUrl = streamUrl
        scope.launch { connectInternal(baseUrl, streamMode, streamUrl) }
    }

    private suspend fun connectInternal(baseUrl: String, streamMode: StreamMode, streamUrl: String) {
        stopInternal(reconnecting = false)
        val session = withContext(Dispatchers.IO) {
            apiClient.createWebRtcSession(baseUrl)
        } ?: run {
            AppLogger.error("LiveKit", "Failed to create session")
            LiveKitStatusCache.setStreaming(false)
            LiveKitStatusCache.setError("LiveKit session could not be created")
            scheduleRestartIfNeeded()
            return
        }
        if (session.token.isBlank() || session.url.isBlank() || session.room.isBlank()) {
            AppLogger.error("LiveKit", "Session missing token, url, or room")
            scheduleRestartIfNeeded()
            return
        }
        // Prefer Connectivity stream host:port (e.g. 10.0.2.2:7880) over API public URL
        // so emulator can reach host LiveKit when tokens advertise localhost.
        val connectUrl = streamUrl.trim().ifBlank { session.url }

        try {
            LiveKitRelayConnect.enableDiagnosticsLogging()
            val liveKitRoom = LiveKit.create(context.applicationContext)
            wireRoomEvents(liveKitRoom, session.room)

            AppLogger.info(
                "LiveKit",
                "Connecting (relay-only ICE) room=${session.room} url=$connectUrl",
            )
            liveKitRoom.connect(connectUrl, session.token, LiveKitRelayConnect.connectOptions())
            applyStreamMode(liveKitRoom.localParticipant, streamMode)
            room = liveKitRoom
            AppLogger.info("LiveKit", "Streaming started (${streamMode.displayName})")
        } catch (e: Exception) {
            AppLogger.error("LiveKit", "Connect failed: ${e.message}")
            _streaming.value = false
            LiveKitStatusCache.setStreaming(false)
            LiveKitStatusCache.setError(e.message ?: "LiveKit connect failed")
            scheduleRestartIfNeeded()
        }
    }

    private fun wireRoomEvents(liveKitRoom: Room, roomName: String) {
        eventsJob?.cancel()
        eventsJob = scope.launch {
            liveKitRoom.events.collect { event ->
                when (event) {
                    is RoomEvent.Connected -> {
                        _streaming.value = true
                        LiveKitStatusCache.setStreaming(true)
                        LiveKitStatusCache.setError(null)
                        AppLogger.info(
                            "LiveKit",
                            "Connected room=$roomName state=${liveKitRoom.state}",
                        )
                    }

                    is RoomEvent.Reconnecting -> {
                        AppLogger.warn(
                            "LiveKit",
                            "Reconnecting room=$roomName state=${liveKitRoom.state}",
                        )
                    }

                    is RoomEvent.Reconnected -> {
                        _streaming.value = true
                        LiveKitStatusCache.setStreaming(true)
                        LiveKitStatusCache.setError(null)
                        AppLogger.info(
                            "LiveKit",
                            "Reconnected room=$roomName state=${liveKitRoom.state}",
                        )
                    }

                    is RoomEvent.FailedToConnect -> {
                        _streaming.value = false
                        LiveKitStatusCache.setStreaming(false)
                        LiveKitStatusCache.setError(event.error.message ?: "LiveKit connect failed")
                        AppLogger.error(
                            "LiveKit",
                            "Failed to connect room=$roomName: ${event.error.message}",
                        )
                        scheduleRestartIfNeeded()
                    }

                    is RoomEvent.Disconnected -> {
                        val reason = event.reason
                        AppLogger.warn(
                            "LiveKit",
                            "Disconnected room=$roomName reason=$reason error=${event.error?.message}",
                        )
                        if (liveKitRoom.state != Room.State.RECONNECTING) {
                            _streaming.value = false
                            LiveKitStatusCache.setStreaming(false)
                            if (reason != DisconnectReason.CLIENT_INITIATED) {
                                LiveKitStatusCache.setError(
                                    event.error?.message ?: "LiveKit disconnected ($reason)",
                                )
                                scheduleRestartIfNeeded()
                            }
                        }
                    }

                    else -> Unit
                }
            }
        }
    }

    private fun scheduleRestartIfNeeded() {
        if (userStopRequested || lastBaseUrl.isBlank()) {
            return
        }
        restartJob?.cancel()
        restartJob = scope.launch {
            delay(RECONNECT_DELAY_MS)
            if (userStopRequested || lastBaseUrl.isBlank()) {
                return@launch
            }
            AppLogger.info("LiveKit", "Restarting publisher after disconnect")
            connectInternal(lastBaseUrl, lastStreamMode, lastStreamUrl)
        }
    }

    private suspend fun applyStreamMode(participant: LocalParticipant, streamMode: StreamMode) {
        when (streamMode) {
            StreamMode.VIDEO_ONLY -> {
                participant.setCameraEnabled(true)
                participant.setMicrophoneEnabled(false)
            }
            StreamMode.AUDIO_ONLY -> {
                participant.setCameraEnabled(false)
                participant.setMicrophoneEnabled(true)
            }
            StreamMode.VIDEO_AUDIO -> {
                participant.setCameraEnabled(true)
                participant.setMicrophoneEnabled(true)
            }
        }
    }

    fun stop() {
        userStopRequested = true
        restartJob?.cancel()
        restartJob = null
        scope.launch { stopInternal(reconnecting = false) }
    }

    fun release() {
        stop()
        scope.cancel()
    }

    private suspend fun stopInternal(reconnecting: Boolean) {
        eventsJob?.cancel()
        eventsJob = null
        if (!reconnecting) {
            room?.disconnect()
        }
        room = null
        if (!reconnecting) {
            _streaming.value = false
            LiveKitStatusCache.setStreaming(false)
        }
    }

    private companion object {
        const val RECONNECT_DELAY_MS = 5_000L
    }
}