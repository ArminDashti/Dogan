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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** LiveKit publisher for video-only, audio-only, and video+audio modes. */
class LiveKitStreamer(
    private val context: Context,
    private val apiClient: DoganApiClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val connectMutex = Mutex()
    private val _streaming = MutableStateFlow(false)
    val streaming: StateFlow<Boolean> = _streaming

    private var room: Room? = null
    private var eventsJob: Job? = null
    private var connectJob: Job? = null
    private var restartJob: Job? = null

    @Volatile
    private var userStopRequested = false

    @Volatile
    private var sdkReconnecting = false

    private var lastBaseUrl: String = ""
    private var lastStreamMode: StreamMode = StreamMode.VIDEO_ONLY
    private var lastStreamUrl: String = ""

    private var activeConnectKey: String = ""

    fun isPublishingSame(
        baseUrl: String,
        streamMode: StreamMode,
        streamUrl: String,
    ): Boolean {
        if (!_streaming.value && room?.state != Room.State.CONNECTED) return false
        return connectKey(baseUrl, streamMode, streamUrl) == activeConnectKey
    }

    fun start(
        baseUrl: String,
        streamMode: StreamMode,
        streamUrl: String = "",
        streamPort: Int = SettingsStore.DEFAULT_STREAM_PORT,
    ) {
        if (isPublishingSame(baseUrl, streamMode, streamUrl) && !userStopRequested) {
            return
        }
        userStopRequested = false
        restartJob?.cancel()
        restartJob = null
        lastBaseUrl = baseUrl
        lastStreamMode = streamMode
        lastStreamUrl = streamUrl
        lastStreamPort = streamPort
        connectJob?.cancel()
        connectJob = scope.launch { connectInternal(baseUrl, streamMode, streamUrl, streamPort) }
    }

    @Volatile
    private var lastStreamPort: Int = SettingsStore.DEFAULT_STREAM_PORT

    private suspend fun connectInternal(
        baseUrl: String,
        streamMode: StreamMode,
        streamUrl: String,
        streamPort: Int,
    ) {
        connectMutex.withLock {
            stopInternalAwait()
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

            val connectUrl = streamUrl.trim().ifBlank { session.url }
            activeConnectKey = connectKey(baseUrl, streamMode, connectUrl)
            try {
                LiveKitRelayConnect.enableDiagnosticsLogging()
                val joinIceServers = runCatching {
                    if (streamPort == 443 || connectUrl.trim().lowercase().startsWith("wss://")) {
                        LiveKitJoinIce.fetchIceServersFromJoin(connectUrl, session.token)
                    } else {
                        emptyList()
                    }
                }.onFailure { error ->
                    AppLogger.warn(
                        "LiveKit",
                        "Join ICE prefetch failed (will use SDK defaults): ${error.message}",
                    )
                }.getOrDefault(emptyList())

                if (joinIceServers.isNotEmpty()) {
                    val turnHosts = joinIceServers.flatMap { it.urls }
                        .filter { LiveKitTurnIceServers.isDoganTurnUrl(it) }
                    AppLogger.info(
                        "LiveKit",
                        "Using ${joinIceServers.size} join ICE server(s); dogan-turn entries=${turnHosts.size}",
                    )
                }

                val liveKitRoom = LiveKit.create(context.applicationContext)
                wireRoomEvents(liveKitRoom, session.room)

                AppLogger.info(
                    "LiveKit",
                    "Connecting room=${session.room} url=$connectUrl ice=relay-or-all",
                )
                liveKitRoom.connect(
                    connectUrl,
                    session.token,
                    LiveKitRelayConnect.connectOptions(connectUrl, streamPort, joinIceServers),
                )
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
    }

    private fun connectKey(baseUrl: String, streamMode: StreamMode, streamUrl: String): String {
        return "$baseUrl|$streamMode|$streamUrl"
    }

    private fun wireRoomEvents(liveKitRoom: Room, roomName: String) {
        eventsJob?.cancel()
        eventsJob = scope.launch {
            liveKitRoom.events.collect { event ->
                when (event) {
                    is RoomEvent.Connected -> {
                        sdkReconnecting = false
                        _streaming.value = true
                        LiveKitStatusCache.setStreaming(true)
                        LiveKitStatusCache.setError(null)
                        AppLogger.info(
                            "LiveKit",
                            "Connected room=$roomName state=${liveKitRoom.state}",
                        )
                    }

                    is RoomEvent.Reconnecting -> {
                        sdkReconnecting = true
                        restartJob?.cancel()
                        AppLogger.warn(
                            "LiveKit",
                            "Reconnecting room=$roomName state=${liveKitRoom.state}",
                        )
                    }

                    is RoomEvent.Reconnected -> {
                        sdkReconnecting = false
                        _streaming.value = true
                        LiveKitStatusCache.setStreaming(true)
                        LiveKitStatusCache.setError(null)
                        AppLogger.info(
                            "LiveKit",
                            "Reconnected room=$roomName state=${liveKitRoom.state}",
                        )
                    }

                    is RoomEvent.FailedToConnect -> {
                        sdkReconnecting = false
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
                        if (sdkReconnecting || liveKitRoom.state == Room.State.RECONNECTING) {
                            return@collect
                        }
                        sdkReconnecting = false
                        _streaming.value = false
                        LiveKitStatusCache.setStreaming(false)
                        if (reason != DisconnectReason.CLIENT_INITIATED) {
                            LiveKitStatusCache.setError(
                                event.error?.message ?: "LiveKit disconnected ($reason)",
                            )
                            scheduleRestartIfNeeded()
                        }
                    }

                    else -> Unit
                }
            }
        }
    }

    private fun scheduleRestartIfNeeded() {
        if (userStopRequested || sdkReconnecting || lastBaseUrl.isBlank()) {
            return
        }
        restartJob?.cancel()
        restartJob = scope.launch {
            delay(RECONNECT_DELAY_MS)
            if (userStopRequested || sdkReconnecting || lastBaseUrl.isBlank()) {
                return@launch
            }
            AppLogger.info("LiveKit", "Restarting publisher after disconnect")
            connectInternal(lastBaseUrl, lastStreamMode, lastStreamUrl, lastStreamPort)
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
        sdkReconnecting = false
        restartJob?.cancel()
        restartJob = null
        connectJob?.cancel()
        connectJob = null
        scope.launch { stopInternalAwait() }
    }

    fun release() {
        stop()
        scope.cancel()
    }

    private suspend fun stopInternalAwait() {
        eventsJob?.cancel()
        eventsJob = null
        val activeRoom = room
        room = null
        activeConnectKey = ""
        activeRoom?.disconnect()
        _streaming.value = false
        LiveKitStatusCache.setStreaming(false)
    }

    private companion object {
        const val RECONNECT_DELAY_MS = 30_000L
    }
}