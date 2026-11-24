package com.dogan

import android.content.Context
import android.media.MediaCodec
import com.pedro.common.AudioCodec
import com.pedro.common.VideoCodec
import com.pedro.common.socket.base.SocketType
import com.pedro.encoder.input.sources.audio.MicrophoneSource
import com.pedro.encoder.input.sources.audio.NoAudioSource
import com.pedro.encoder.input.sources.video.Camera2Source
import com.pedro.encoder.input.sources.video.NoVideoSource
import com.pedro.library.base.StreamBase
import com.pedro.library.util.streamclient.StreamBaseClient
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WebSocketStreamer(
    private val context: Context,
    private val apiClient: DoganApiClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectMutex = Mutex()
    private val httpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val _streaming = MutableStateFlow(false)
    val streaming: StateFlow<Boolean> = _streaming.asStateFlow()

    private var connectJob: Job? = null
    private var restartJob: Job? = null
    private var webSocket: WebSocket? = null
    private var mediaStream: WebSocketMediaStream? = null
    private var userStopRequested = false
    private var lastBaseUrl = ""
    private var lastStreamMode = StreamMode.VIDEO_AUDIO
    private var activeConnectKey = ""

    fun isPublishingSame(baseUrl: String, streamMode: StreamMode): Boolean {
        return _streaming.value && connectKey(baseUrl, streamMode) == activeConnectKey
    }

    fun start(baseUrl: String, streamMode: StreamMode) {
        if (isPublishingSame(baseUrl, streamMode) && !userStopRequested) return
        userStopRequested = false
        restartJob?.cancel()
        restartJob = null
        lastBaseUrl = baseUrl
        lastStreamMode = streamMode
        connectJob?.cancel()
        connectJob = scope.launch { connectInternal(baseUrl, streamMode) }
    }

    fun stop() {
        userStopRequested = true
        restartJob?.cancel()
        restartJob = null
        connectJob?.cancel()
        connectJob = null
        scope.launch {
            connectMutex.withLock { stopInternal() }
        }
    }

    fun release() {
        stop()
        scope.cancel()
    }

    private suspend fun connectInternal(baseUrl: String, streamMode: StreamMode) {
        connectMutex.withLock {
            stopInternal()
            if (userStopRequested) return

            try {
                val session = withContext(Dispatchers.IO) {
                    apiClient.createWebRtcSession(baseUrl)
                } ?: throw IllegalStateException("Streaming session could not be created")
                if (session.token.isBlank() || session.websocketPublishUrl.isBlank()) {
                    throw IllegalStateException("Streaming session has no WebSocket publish URL")
                }

                val bridgeReady = CompletableDeferred<Unit>()
                val socket = openWebSocket(
                    session.websocketPublishUrl,
                    session.token,
                    bridgeReady,
                )
                webSocket = socket
                val stream = WebSocketMediaStream(
                    context,
                    streamMode,
                    socket,
                ) { error ->
                    bridgeReady.completeExceptionally(error)
                    onConnectionFailure(error)
                }
                mediaStream = stream
                stream.start()
                withTimeout(BRIDGE_READY_TIMEOUT_MS) { bridgeReady.await() }

                activeConnectKey = connectKey(baseUrl, streamMode)
                _streaming.value = true
                StreamStatusCache.setStreaming(true)
                StreamStatusCache.setError(null)
                AppLogger.info(
                    "Streaming",
                    "WebSocket publishing path=${session.path} mode=${streamMode.displayName}",
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                stopInternal()
                onConnectionFailure(error)
            }
        }
    }

    private suspend fun openWebSocket(
        publishUrl: String,
        token: String,
        bridgeReady: CompletableDeferred<Unit>,
    ): WebSocket = suspendCancellableCoroutine { continuation ->
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (continuation.isActive) continuation.resume(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                when (JSONObject(text).optString("type")) {
                    "ready" -> bridgeReady.complete(Unit)
                    "error" -> bridgeReady.completeExceptionally(
                        IllegalStateException("Media bridge rejected the stream"),
                    )
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (continuation.isActive) continuation.resumeWithException(t)
                bridgeReady.completeExceptionally(t)
                if (this@WebSocketStreamer.webSocket === webSocket) {
                    onConnectionFailure(t)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!userStopRequested && this@WebSocketStreamer.webSocket === webSocket) {
                    onConnectionFailure(IllegalStateException("Streaming connection closed"))
                }
            }
        }
        val request = Request.Builder()
            .url(publishUrl)
            .header("Authorization", "Bearer $token")
            .build()
        val socket = httpClient.newWebSocket(request, listener)
        continuation.invokeOnCancellation { socket.cancel() }
    }

    private suspend fun stopInternal() {
        mediaStream?.let { stream ->
            runCatching { stream.release() }
        }
        mediaStream = null
        val socket = webSocket
        webSocket = null
        socket?.close(1000, "stopped")
        _streaming.value = false
        activeConnectKey = ""
        StreamStatusCache.setStreaming(false)
    }

    private fun onConnectionFailure(error: Throwable) {
        if (userStopRequested) return
        val message = error.message ?: "WebSocket streaming failed"
        _streaming.value = false
        StreamStatusCache.setStreaming(false)
        StreamStatusCache.setError(message)
        AppLogger.error("Streaming", message)
        scheduleRestartIfNeeded()
    }

    private fun scheduleRestartIfNeeded() {
        if (userStopRequested || restartJob?.isActive == true || lastBaseUrl.isBlank()) return
        restartJob = scope.launch {
            delay(RECONNECT_DELAY_MS)
            if (!userStopRequested && lastBaseUrl.isNotBlank()) {
                connectInternal(lastBaseUrl, lastStreamMode)
            }
        }
    }

    private fun connectKey(baseUrl: String, streamMode: StreamMode): String {
        return "$baseUrl|$streamMode"
    }

    private class WebSocketMediaStream(
        context: Context,
        private val streamMode: StreamMode,
        private val socket: WebSocket,
        private val onFailure: (Throwable) -> Unit,
    ) : StreamBase(
        context,
        if (streamMode == StreamMode.AUDIO_ONLY) NoVideoSource() else Camera2Source(context),
        if (streamMode == StreamMode.VIDEO_ONLY) NoAudioSource() else MicrophoneSource(),
    ) {
        private val videoEnabled = streamMode != StreamMode.AUDIO_ONLY
        private val audioEnabled = streamMode != StreamMode.VIDEO_ONLY
        private var configurationSent = false

        fun start() {
            setVideoCodec(VideoCodec.H264)
            setAudioCodec(AudioCodec.AAC)
            check(prepareVideo(VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_BITRATE, VIDEO_FPS)) {
                "Unable to prepare H.264 video encoder"
            }
            check(prepareAudio(AUDIO_SAMPLE_RATE, false, AUDIO_BITRATE, true, true)) {
                "Unable to prepare AAC audio encoder"
            }
            startStream("")
        }

        override fun onAudioInfoImp(sampleRate: Int, isStereo: Boolean) = Unit

        override fun startStreamImp(endPoint: String) {
            if (!videoEnabled) {
                sendConfiguration()
            }
        }

        override fun stopStreamImp() = Unit

        override fun onVideoInfoImp(sps: ByteBuffer, pps: ByteBuffer?, vps: ByteBuffer?) {
            if (!videoEnabled || pps == null) return
            configurationSent = socket.send(
                WebSocketFrameProtocol.encodeConfiguration(
                    videoEnabled,
                    audioEnabled,
                    WebSocketFrameProtocol.copyRemaining(sps),
                    WebSocketFrameProtocol.copyRemaining(pps),
                ),
            )
            if (!configurationSent) onFailure(IllegalStateException("Unable to send stream configuration"))
        }

        override fun getVideoDataImp(videoBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
            if (!videoEnabled || !configurationSent ||
                info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
            ) return
            sendFrame(
                WebSocketFrameProtocol.VIDEO_FRAME,
                info.presentationTimeUs,
                videoBuffer,
                info,
            )
        }

        override fun getAudioDataImp(audioBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
            if (!audioEnabled || !configurationSent ||
                info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
            ) return
            sendFrame(
                WebSocketFrameProtocol.AUDIO_FRAME,
                info.presentationTimeUs,
                audioBuffer,
                info,
            )
        }

        override fun getStreamClient(): StreamBaseClient = NoopStreamBaseClient

        override fun setVideoCodecImp(codec: VideoCodec) = Unit

        override fun setAudioCodecImp(codec: AudioCodec) = Unit

        private fun sendConfiguration() {
            configurationSent = socket.send(
                WebSocketFrameProtocol.encodeConfiguration(videoEnabled, audioEnabled),
            )
            if (!configurationSent) onFailure(IllegalStateException("Unable to send stream configuration"))
        }

        private fun sendFrame(
            type: Byte,
            timestampUs: Long,
            buffer: ByteBuffer,
            info: MediaCodec.BufferInfo,
        ) {
            val payload = WebSocketFrameProtocol.copyBuffer(buffer, info.offset, info.size)
            val frame = WebSocketFrameProtocol.encodeFrame(type, timestampUs, payload)
            if (!socket.send(ByteString.of(*frame))) {
                onFailure(IllegalStateException("Unable to send encoded media frame"))
            }
        }

        private companion object {
            const val VIDEO_WIDTH = 1280
            const val VIDEO_HEIGHT = 720
            const val VIDEO_BITRATE = 1_000_000
            const val VIDEO_FPS = 30
            const val AUDIO_SAMPLE_RATE = 44100
            const val AUDIO_BITRATE = 64_000
        }
    }

    private object NoopStreamBaseClient : StreamBaseClient() {
        override fun setAuthorization(user: String?, password: String?) = Unit
        override fun reTry(delay: Long, reason: String, backupUrl: String?): Boolean = false
        override fun setReTries(reTries: Int) = Unit
        override fun hasCongestion(percentUsed: Float): Boolean = false
        override fun setLogs(enabled: Boolean) = Unit
        override fun setCheckServerAlive(enabled: Boolean) = Unit
        override fun resizeCache(newSize: Int) = Unit
        override fun clearCache() = Unit
        override fun getCacheSize(): Int = 0
        override fun getItemsInCache(): Int = 0
        override fun getQueueBytesOut(): Long = 0
        override fun getSentAudioFrames(): Long = 0
        override fun getSentVideoFrames(): Long = 0
        override fun getBytesSend(): Long = 0
        override fun getDroppedAudioFrames(): Long = 0
        override fun getDroppedVideoFrames(): Long = 0
        override fun resetSentAudioFrames() = Unit
        override fun resetSentVideoFrames() = Unit
        override fun resetDroppedAudioFrames() = Unit
        override fun resetDroppedVideoFrames() = Unit
        override fun resetBytesSend() = Unit
        override fun setOnlyAudio(onlyAudio: Boolean) = Unit
        override fun setOnlyVideo(onlyVideo: Boolean) = Unit
        override fun setBitrateExponentialFactor(factor: Float) = Unit
        override fun getBitrateExponentialFactor(): Float = 1f
        override fun setSocketType(type: SocketType) = Unit
        override fun setSocketTimeout(timeout: Long) = Unit
        override fun setDelay(millis: Long) = Unit
    }

    private companion object {
        const val RECONNECT_DELAY_MS = 30_000L
        const val BRIDGE_READY_TIMEOUT_MS = 20_000L
    }
}
