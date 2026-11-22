package com.dogan

import android.content.Context
import java.util.concurrent.TimeUnit
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Capturer
import org.webrtc.Camera2Enumerator
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

class WhipStreamer(
    context: Context,
    private val apiClient: DoganApiClient,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val connectMutex = Mutex()
    private val _streaming = MutableStateFlow(false)
    val streaming: StateFlow<Boolean> = _streaming

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .sslSocketFactory(
            SslWarningTrustManager.createSocketFactory(),
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) = Unit
                override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) = Unit
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
            },
        )
        .hostnameVerifier(SslWarningTrustManager.hostnameVerifier)
        .build()

    private var eglBase: EglBase? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoCapturer: Camera2Capturer? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var whipLocation: String? = null
    private var whipToken: String? = null
    private var iceGatheringComplete = CompletableDeferred<Unit>()

    private var connectJob: Job? = null
    private var restartJob: Job? = null

    @Volatile
    private var userStopRequested = false

    @Volatile
    private var reconnecting = false

    private var lastBaseUrl: String = ""
    private var lastStreamMode: StreamMode = StreamMode.VIDEO_ONLY
    private var lastStreamUrl: String = ""
    private var lastStreamPort: Int = SettingsStore.DEFAULT_STREAM_PORT
    private var activeConnectKey: String = ""

    fun isPublishingSame(
        baseUrl: String,
        streamMode: StreamMode,
        streamUrl: String,
    ): Boolean {
        if (!_streaming.value) return false
        return connectKey(baseUrl, streamMode, streamUrl) == activeConnectKey
    }

    fun start(
        baseUrl: String,
        streamMode: StreamMode,
        streamUrl: String = "",
        streamPort: Int = SettingsStore.DEFAULT_STREAM_PORT,
    ) {
        if (isPublishingSame(baseUrl, streamMode, streamUrl) && !userStopRequested) return
        userStopRequested = false
        reconnecting = false
        restartJob?.cancel()
        restartJob = null
        lastBaseUrl = baseUrl
        lastStreamMode = streamMode
        lastStreamUrl = streamUrl
        lastStreamPort = streamPort
        connectJob?.cancel()
        connectJob = scope.launch { connectInternal(baseUrl, streamMode, streamUrl, streamPort) }
    }

    fun stop() {
        userStopRequested = true
        reconnecting = false
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

    private suspend fun connectInternal(
        baseUrl: String,
        streamMode: StreamMode,
        streamUrl: String,
        streamPort: Int,
    ) {
        connectMutex.withLock {
            stopInternalUnsafe()
            if (userStopRequested) return@withLock
            val session = withContext(Dispatchers.IO) {
                apiClient.createWebRtcSession(baseUrl)
            }
            if (session == null || session.token.isBlank()) {
                AppLogger.error("Streaming", "Failed to create WHIP session")
                StreamStatusCache.setStreaming(false)
                StreamStatusCache.setError("Streaming session could not be created")
                scheduleRestartIfNeeded()
                return@withLock
            }
            val whipUrl = resolveWhipUrl(session, streamUrl)
            if (whipUrl.isBlank()) {
                AppLogger.error("Streaming", "Session missing WHIP URL")
                StreamStatusCache.setStreaming(false)
                StreamStatusCache.setError("Streaming session has no WHIP URL")
                scheduleRestartIfNeeded()
                return@withLock
            }
            activeConnectKey = connectKey(baseUrl, streamMode, whipUrl)
            whipToken = session.token
            try {
                initializeFactory(appContext)
                val egl = EglBase.create().also { eglBase = it }
                val factory = createPeerConnectionFactory(egl).also { peerConnectionFactory = it }
                val rtcConfig = WhipIceConfig.rtcConfiguration(session.iceServers, whipUrl, streamPort)
                val connection = factory.createPeerConnection(rtcConfig, createObserver())
                    ?: throw IllegalStateException("PeerConnection could not be created")
                peerConnection = connection
                attachTracks(factory, egl, connection, streamMode)

                val offer = createOfferSdp(connection)
                iceGatheringComplete = CompletableDeferred()
                setLocalSdp(connection, offer)
                if (withTimeoutOrNull(ICE_GATHERING_TIMEOUT_MS) { iceGatheringComplete.await() } == null) {
                    AppLogger.warn("Streaming", "ICE gathering timed out after ${ICE_GATHERING_TIMEOUT_MS}ms")
                }
                if (userStopRequested) return@withLock

                val localSdp = connection.localDescription
                    ?: throw IllegalStateException("Local SDP is unavailable")
                val answerSdp = publishOffer(whipUrl, session.token, localSdp.description)
                setRemoteSdp(connection, SessionDescription(SessionDescription.Type.ANSWER, answerSdp))
                if (userStopRequested) return@withLock

                reconnecting = false
                _streaming.value = true
                StreamStatusCache.setStreaming(true)
                StreamStatusCache.setError(null)
                AppLogger.info(
                    "Streaming",
                    "WHIP publishing path=${session.path} url=$whipUrl mode=${streamMode.displayName}",
                )
            } catch (e: Exception) {
                AppLogger.error("Streaming", "WHIP connect failed: ${e.message}")
                _streaming.value = false
                StreamStatusCache.setStreaming(false)
                StreamStatusCache.setError(e.message ?: "Streaming connect failed")
                scheduleRestartIfNeeded()
            }
        }
    }

    private fun connectKey(baseUrl: String, streamMode: StreamMode, streamUrl: String): String {
        return "$baseUrl|$streamMode|$streamUrl"
    }

    private fun resolveWhipUrl(
        session: DoganApiClient.WebRtcSessionResult,
        streamUrl: String,
    ): String {
        val apiUrl = session.whipUrl.ifBlank { session.url }.trim()
        val configured = streamUrl.trim()
        if (apiUrl.isBlank() || configured.isBlank()) return apiUrl
        return try {
            val api = apiUrl.toHttpUrl()
            configured.toHttpUrl().newBuilder()
                .encodedPath(api.encodedPath)
                .query(api.query)
                .fragment(api.fragment)
                .build()
                .toString()
        } catch (_: Exception) {
            apiUrl
        }
    }

    private fun initializeFactory(appContext: Context) {
        if (factoryInitialized) return
        synchronized(this) {
            if (!factoryInitialized) {
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(appContext)
                        .createInitializationOptions(),
                )
                factoryInitialized = true
            }
        }
    }

    private fun createPeerConnectionFactory(egl: EglBase): PeerConnectionFactory {
        return PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
            .createPeerConnectionFactory()
    }

    private fun attachTracks(
        factory: PeerConnectionFactory,
        egl: EglBase,
        connection: PeerConnection,
        streamMode: StreamMode,
    ) {
        if (streamMode != StreamMode.AUDIO_ONLY) {
            val enumerator = Camera2Enumerator(appContext)
            val cameraName = enumerator.deviceNames.firstOrNull { !enumerator.isFrontFacing(it) }
                ?: enumerator.deviceNames.firstOrNull()
                ?: throw IllegalStateException("No camera available")
            // CameraX still-capture in CaptureService remains active alongside this Camera2 capturer, preserving the previous publisher behavior.
            val textureHelper = SurfaceTextureHelper.create(CAPTURE_THREAD, egl.eglBaseContext)
                ?: throw IllegalStateException("Capture surface could not be created")
            surfaceTextureHelper = textureHelper
            val source = factory.createVideoSource(false)
            videoSource = source
            val track = factory.createVideoTrack(VIDEO_TRACK_ID, source)
            videoTrack = track
            val capturer = Camera2Capturer(appContext, cameraName, null)
            videoCapturer = capturer
            capturer.initialize(textureHelper, appContext, source.capturerObserver)
            capturer.startCapture(CAPTURE_WIDTH, CAPTURE_HEIGHT, CAPTURE_FPS)
            connection.addTransceiver(
                track,
                RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY),
            )
        }
        if (streamMode != StreamMode.VIDEO_ONLY) {
            val source = factory.createAudioSource(MediaConstraints())
            audioSource = source
            val track = factory.createAudioTrack(AUDIO_TRACK_ID, source)
            audioTrack = track
            connection.addTransceiver(
                track,
                RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY),
            )
        }
    }

    private fun createObserver(): PeerConnection.Observer {
        return object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) = Unit

            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
                if (state == PeerConnection.IceGatheringState.COMPLETE) {
                    iceGatheringComplete.complete(Unit)
                }
            }

            override fun onIceCandidate(candidate: IceCandidate) = Unit

            override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) = Unit

            override fun onAddStream(mediaStream: MediaStream) = Unit

            override fun onRemoveStream(mediaStream: MediaStream) = Unit

            override fun onDataChannel(dataChannel: DataChannel) = Unit

            override fun onRenegotiationNeeded() = Unit

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                if (newState == PeerConnection.PeerConnectionState.CONNECTED) {
                    reconnecting = false
                    _streaming.value = true
                    StreamStatusCache.setStreaming(true)
                    StreamStatusCache.setError(null)
                    return
                }
                if (newState == PeerConnection.PeerConnectionState.FAILED ||
                    newState == PeerConnection.PeerConnectionState.DISCONNECTED
                ) {
                    _streaming.value = false
                    StreamStatusCache.setStreaming(false)
                    StreamStatusCache.setError("Streaming connection $newState")
                    scheduleRestartIfNeeded()
                }
            }
        }
    }

    private suspend fun createOfferSdp(connection: PeerConnection): SessionDescription =
        suspendCancellableCoroutine { continuation ->
            connection.createOffer(
                object : SdpObserver {
                    override fun onCreateSuccess(description: SessionDescription) {
                        if (continuation.isActive) continuation.resume(description)
                    }

                    override fun onCreateFailure(error: String) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(IllegalStateException(error))
                        }
                    }

                    override fun onSetSuccess() = Unit

                    override fun onSetFailure(error: String) = Unit
                },
                MediaConstraints(),
            )
        }

    private suspend fun setLocalSdp(connection: PeerConnection, description: SessionDescription) =
        suspendCancellableCoroutine { continuation ->
            connection.setLocalDescription(
                object : SdpObserver {
                    override fun onCreateSuccess(description: SessionDescription) = Unit

                    override fun onCreateFailure(error: String) = Unit

                    override fun onSetSuccess() {
                        if (continuation.isActive) continuation.resume(Unit)
                    }

                    override fun onSetFailure(error: String) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(IllegalStateException(error))
                        }
                    }
                },
                description,
            )
        }

    private suspend fun setRemoteSdp(connection: PeerConnection, description: SessionDescription) =
        suspendCancellableCoroutine { continuation ->
            connection.setRemoteDescription(
                object : SdpObserver {
                    override fun onCreateSuccess(description: SessionDescription) = Unit

                    override fun onCreateFailure(error: String) = Unit

                    override fun onSetSuccess() {
                        if (continuation.isActive) continuation.resume(Unit)
                    }

                    override fun onSetFailure(error: String) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(IllegalStateException(error))
                        }
                    }
                },
                description,
            )
        }

    private suspend fun publishOffer(
        whipUrl: String,
        token: String,
        offerSdp: String,
    ): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(whipUrl)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/sdp")
            .post(offerSdp.toRequestBody(SDP_MEDIA_TYPE))
            .build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("WHIP POST failed (HTTP ${response.code}): ${body.take(200)}")
            }
            if (body.isBlank()) throw IllegalStateException("WHIP POST returned an empty SDP answer")
            whipLocation = response.header("Location")?.let { resolveLocation(whipUrl, it) }
            body
        }
    }

    private fun resolveLocation(baseUrl: String, location: String): String {
        return try {
            baseUrl.toHttpUrl().resolve(location)?.toString() ?: location
        } catch (_: Exception) {
            location
        }
    }

    private fun scheduleRestartIfNeeded() {
        if (userStopRequested || reconnecting || lastBaseUrl.isBlank()) return
        reconnecting = true
        restartJob?.cancel()
        restartJob = scope.launch {
            delay(RECONNECT_DELAY_MS)
            if (userStopRequested || lastBaseUrl.isBlank()) {
                reconnecting = false
                return@launch
            }
            if (peerConnection?.connectionState() == PeerConnection.PeerConnectionState.CONNECTED) {
                reconnecting = false
                return@launch
            }
            reconnecting = false
            AppLogger.info("Streaming", "Restarting WHIP publisher after disconnect")
            connectInternal(lastBaseUrl, lastStreamMode, lastStreamUrl, lastStreamPort)
        }
    }

    private suspend fun stopInternalAwait() = connectMutex.withLock {
        stopInternalUnsafe()
    }

    private suspend fun stopInternalUnsafe() {
        val location = whipLocation
        val token = whipToken
        whipLocation = null
        whipToken = null
        if (!location.isNullOrBlank() && !token.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(location)
                    .header("Authorization", "Bearer $token")
                    .delete()
                    .build()
                try {
                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            AppLogger.warn("Streaming", "WHIP DELETE failed (HTTP ${response.code})")
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.warn("Streaming", "WHIP DELETE failed: ${e.message}")
                }
            }
        }
        // Dispose the PeerConnection (and its senders) before the tracks/sources it references.
        peerConnection?.dispose()
        peerConnection = null
        try {
            videoCapturer?.stopCapture()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (e: Exception) {
            AppLogger.warn("Streaming", "Capture stop failed: ${e.message}")
        }
        try {
            videoCapturer?.dispose()
        } catch (_: Exception) {
            Unit
        }
        videoCapturer = null
        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null
        videoTrack?.dispose()
        videoTrack = null
        videoSource?.dispose()
        videoSource = null
        audioTrack?.dispose()
        audioTrack = null
        audioSource?.dispose()
        audioSource = null
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
        eglBase?.release()
        eglBase = null
        iceGatheringComplete.complete(Unit)
        activeConnectKey = ""
        reconnecting = false
        _streaming.value = false
        StreamStatusCache.setStreaming(false)
    }

    private companion object {
        const val RECONNECT_DELAY_MS = 30_000L
        const val ICE_GATHERING_TIMEOUT_MS = 3_000L
        const val CAPTURE_THREAD = "CaptureThread"
        const val CAPTURE_WIDTH = 1280
        const val CAPTURE_HEIGHT = 720
        const val CAPTURE_FPS = 30
        const val VIDEO_TRACK_ID = "dogan-video"
        const val AUDIO_TRACK_ID = "dogan-audio"
        val SDP_MEDIA_TYPE = "application/sdp".toMediaType()

        @Volatile
        var factoryInitialized = false
    }
}
