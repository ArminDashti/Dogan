package com.dogan

import android.content.Context
import io.livekit.android.LiveKit
import io.livekit.android.events.DisconnectReason
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.participant.LocalParticipant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/** LiveKit publisher for video-only, audio-only, and video+audio modes. */
class LiveKitStreamer(
    private val context: Context,
    private val apiClient: DoganApiClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val connectMutex = Mutex()
    private val _streaming = MutableStateFlow(false)
    val streaming: StateFlow<Boolean> = _streaming

    @Volatile
    private var room: Room? = null

    private var eventsJob: Job? = null
    private var connectJob: Job? = null
    private var restartJob: Job? = null

    @Volatile
    private var connecting = false

    @Volatile
    private var userStopRequested = false

    @Volatile
    private var sdkReconnecting = false

    @Volatile
    private var lastBaseUrl: String = ""

    @Volatile
    private var lastStreamMode: StreamMode = StreamMode.VIDEO_ONLY

    @Volatile
    private var lastStreamUrl: String = ""

    @Volatile
    private var lastStreamPort: Int = SettingsStore.DEFAULT_STREAM_PORT

    @Volatile
    private var activeConnectKey: String = ""

    init {
        scope.launch { retryTicker() }
    }

    fun isPublishingSame(
        baseUrl: String,
        streamMode: StreamMode,
        streamUrl: String,
    ): Boolean {
        if (!_streaming.value || room?.state != Room.State.CONNECTED) return false
        return connectKey(baseUrl, streamMode, streamUrl) == activeConnectKey
    }

    fun start(
        baseUrl: String,
        streamMode: StreamMode,
        streamUrl: String = "",
        streamPort: Int = SettingsStore.DEFAULT_STREAM_PORT,
    ) {
        if (!userStopRequested && isPublishingSame(baseUrl, streamMode, streamUrl)) {
            return
        }
        userStopRequested = false
        lastBaseUrl = baseUrl
        lastStreamMode = streamMode
        lastStreamUrl = streamUrl
        lastStreamPort = streamPort
        if (connecting) {
            AppLogger.info("LiveKit", "Connect already in progress; reusing attempt")
            return
        }
        restartJob?.cancel()
        restartJob = null
        connectJob?.cancel()
        connectJob = scope.launch { connectInternal(baseUrl, streamMode, streamUrl, streamPort) }
    }

    private suspend fun connectInternal(
        baseUrl: String,
        streamMode: StreamMode,
        streamUrl: String,
        streamPort: Int,
    ) {
        connecting = true
        var pendingRoom: Room? = null
        try {
            withTimeout(CONNECT_TIMEOUT_MS) {
                connectMutex.withLock {
                    stopInternalAwait()
                    val session = withContext(Dispatchers.IO) {
                        apiClient.createWebRtcSession(baseUrl)
                    } ?: throw IllegalStateException("LiveKit session could not be created")
                    if (session.token.isBlank() || session.url.isBlank() || session.room.isBlank()) {
                        throw IllegalStateException("Session missing token, url, or room")
                    }

                    val connectUrl = streamUrl.trim().ifBlank { session.url }
                    val iceServers = withContext(Dispatchers.Default) {
                        LiveKitTurnIceServers.fromJsonArray(session.iceServers)
                    }
                    activeConnectKey = connectKey(baseUrl, streamMode, connectUrl)
                    LiveKitRelayConnect.enableDiagnosticsLogging()

                    val liveKitRoom = LiveKit.create(context.applicationContext)
                    pendingRoom = liveKitRoom
                    wireRoomEvents(liveKitRoom, session.room)
                    AppLogger.info(
                        "LiveKit",
                        "Connecting room=${session.room} url=$connectUrl ice_servers=${iceServers.size}",
                    )
                    liveKitRoom.connect(
                        connectUrl,
                        session.token,
                        LiveKitRelayConnect.connectOptions(connectUrl, streamPort, iceServers),
                    )
                    applyStreamMode(liveKitRoom.localParticipant, streamMode)
                    room = liveKitRoom
                    AppLogger.info("LiveKit", "Streaming started (${streamMode.displayName})")
                }
            }
        } catch (timedOut: TimeoutCancellationException) {
            failConnect(pendingRoom, "LiveKit connect timed out after ${CONNECT_TIMEOUT_MS}ms")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            failConnect(pendingRoom, "${failure.javaClass.simpleName}: ${failure.message}")
        } finally {
            connecting = false
        }
    }

    private suspend fun failConnect(pendingRoom: Room?, message: String) {
        AppLogger.error("LiveKit", "Connect failed: $message")
        activeConnectKey = ""
        sdkReconnecting = false
        if (pendingRoom != null) {
            eventsJob?.cancel()
            eventsJob = null
            withTimeoutOrNull(DISCONNECT_TIMEOUT_MS) { pendingRoom.disconnect() }
            if (room === pendingRoom) room = null
        }
        _streaming.value = false
        LiveKitStatusCache.setStreaming(false)
        LiveKitStatusCache.setError(message)
        scheduleRestartIfNeeded()
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
                        sdkReconnecting = false
                        _streaming.value = false
                        LiveKitStatusCache.setStreaming(false)
                        AppLogger.warn(
                            "LiveKit",
                            "Disconnected room=$roomName reason=$reason error=${event.error?.message}",
                        )
                        if (reason == DisconnectReason.CLIENT_INITIATED) {
                            return@collect
                        }
                        LiveKitStatusCache.setError(
                            event.error?.message ?: "LiveKit disconnected ($reason)",
                        )
                        if (liveKitRoom.state == Room.State.RECONNECTING) {
                            return@collect
                        }
                        scheduleRestartIfNeeded()
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
            attemptReconnect()
        }
    }

    private suspend fun attemptReconnect() {
        if (userStopRequested || sdkReconnecting || connecting || _streaming.value) return
        if (lastBaseUrl.isBlank()) return
        if (room?.state == Room.State.CONNECTED || roomIsBusy()) return
        AppLogger.info("LiveKit", "Restarting publisher after disconnect")
        connectInternal(lastBaseUrl, lastStreamMode, lastStreamUrl, lastStreamPort)
    }

    private fun roomIsBusy(): Boolean = when (room?.state) {
        Room.State.CONNECTING, Room.State.RECONNECTING -> true
        else -> false
    }

    private suspend fun retryTicker() {
        while (true) {
            delay(RETRY_TICK_MS)
            if (userStopRequested || connecting || sdkReconnecting || _streaming.value) continue
            if (lastBaseUrl.isBlank()) continue
            attemptReconnect()
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
        scope.launch {
            connectMutex.withLock { stopInternalAwait() }
        }
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
        sdkReconnecting = false
        if (activeRoom != null) {
            withTimeoutOrNull(DISCONNECT_TIMEOUT_MS) { activeRoom.disconnect() }
        }
        _streaming.value = false
        LiveKitStatusCache.setStreaming(false)
    }

    private companion object {
        const val RECONNECT_DELAY_MS = 10_000L
        const val RETRY_TICK_MS = 15_000L
        const val CONNECT_TIMEOUT_MS = 20_000L
        const val DISCONNECT_TIMEOUT_MS = 5_000L
    }
}
