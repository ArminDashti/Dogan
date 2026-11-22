package com.dogan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/** Foreground service orchestrating modes, telemetry, and streaming. */
class CaptureService : Service(), LifecycleOwner {
    companion object {
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        const val ACTION_MODE_ACTIVATED = "mode_activated"

        private const val CHANNEL = "dogan_capture_channel"
        private const val MOTION_CHANNEL = "dogan_motion_channel"
        private const val NOTIF_ID = 81
        private const val WORK_WAKE_LOCK_MS = 30_000L
        private const val DEFAULT_TELEMETRY_INTERVAL_MS = 1_000L
        private const val ERROR_RETRY_DELAY_MS = 5_000L
        private const val MIN_LOOP_DELAY_MS = 250L
        private const val DIAGNOSTIC_UPLOAD_INTERVAL_MS = 30_000L
        private const val KEEP_ALIVE_INTERVAL_MS = 5_000L
        private const val WATCHDOG_INTERVAL_MS = 15_000L
        private const val WATCHDOG_GRACE_MS = 30_000L
        private const val HEARTBEAT_LOG_INTERVAL_MS = 60_000L
        private const val CAPTURE_LATCH_TIMEOUT_SEC = 15L
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val settingsStore by lazy { SettingsStore(this) }
    private val deviceId by lazy { DeviceIdentity.resolveDeviceId(this) }
    private val apiClient by lazy { DoganApiClient(deviceId = deviceId) }
    private val io = Executors.newSingleThreadExecutor()
    private val captureCallbackExecutor = Executors.newSingleThreadExecutor()

    private val telemetryDatabase by lazy { TelemetryDatabase(this) }
    private val cabinNoiseArchive by lazy { CabinNoiseArchive(this) }
    private val locationTracker by lazy { LocationTracker(this) }
    private val ambientLightSensor by lazy { AmbientLightSensor(this) }
    private val soundDownloadManager by lazy { SoundDownloadManager(this, apiClient) }
    private val alertManager by lazy { AlertManager(this, soundDownloadManager) }
    private val watcherEngine by lazy { WatcherEngine(alertManager) }
    private val modeController by lazy { ModeController(watcherEngine) }
    private val telemetryUploader by lazy { TelemetryUploader(apiClient, telemetryDatabase) }
    private val liveKitStreamer by lazy { LiveKitStreamer(this, apiClient) }
    private val resourceMonitor by lazy { DeviceResourceMonitor(this) }

    private lateinit var audioCapture: AudioCapture
    private lateinit var telemetryCollector: TelemetryCollector

    @Volatile
    private var running = false
    private var wakeLock: PowerManager.WakeLock? = null

    private val loopLock = Any()
    private var loopScope: CoroutineScope? = null
    private var telemetryJob: Job? = null
    private var uploadJob: Job? = null
    private var diagnosticUploadJob: Job? = null
    private var keepAliveJob: Job? = null
    private var watchdogJob: Job? = null

    @Volatile
    private var lastTelemetryCycleAt = 0L

    @Volatile
    private var lastHeartbeatLogAt = 0L

    private val loopExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable is CancellationException) return@CoroutineExceptionHandler
        AppLogger.error(
            "Capture",
            "Loop crashed: ${throwable.javaClass.simpleName}: ${throwable.message}",
        )
    }

    @Volatile private var cachedSettings: AppSettings? = null
    @Volatile private var lastMotionTriggeredCaptureAt = 0L
    @Volatile private var lastScreenWakeAt = 0L
    @Volatile private var lastWatcherFrameAt = 0L
    @Volatile private var lastLatencySampleAt = 0L
    @Volatile private var lastDisconnectedLogAt = 0L
    @Volatile private var holdWakeLockIndefinitely = false

    private lateinit var sensorManager: SensorManager
    private val vehicleMotionDetector = VehicleMotionDetector { peakMps2 ->
        onVehicleBumpDetected(peakMps2)
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        audioCapture = AudioCapture(
            this,
            onSpike = { rms ->
                cachedSettings?.let { modeController.onSoundSpike(rms, it) }
            },
            cabinNoiseArchive = cabinNoiseArchive,
        )
        telemetryCollector = TelemetryCollector(
            this,
            locationTracker,
            ambientLightSensor,
            audioCapture,
            vehicleMotionDetector,
            deviceId,
            resourceMonitor,
        )
        AppLogger.init(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopCapture()
            ACTION_MODE_ACTIVATED -> {
                if (running) {
                    refreshForModeActivation()
                } else {
                    startCapture()
                }
                ensureLoops()
            }
            else -> {
                if (running) {
                    refreshForModeActivation()
                } else {
                    startCapture()
                }
                ensureLoops()
            }
        }
        return START_STICKY
    }

    private fun startCapture() {
        if (running) {
            ensureLoops()
            return
        }
        running = true
        createChannels()
        startForeground(NOTIF_ID, buildNotification())
        lifecycleRegistry.currentState = Lifecycle.State.STARTED

        cachedSettings = runBlocking { settingsStore.settingsFlow.first() }
        ensureLoops()
        val settings = cachedSettings ?: run {
            AppLogger.error("Capture", "Settings unavailable; telemetry loops still supervised")
            return
        }
        SessionCredentials.updateFrom(settings)
        AppLogger.activeModeSection = LogSection.forOperatingMode(settings.operatingMode)
        configureWakeLockForMode(settings)
        ensureServerConnected(settings)

        downloadSounds(settings)
        vehicleMotionDetector.register(sensorManager, settings.motionSamplingRate)
        startLocationTracking(settings)
        applyAmbientLightSafely(settings)
        applySoundMonitoringSafely(settings)

        if (ServerConnectionManager.isConnected()) {
            ServerSettingsSync.start(this)
            maybeStartLiveKit(settings)
        }

        applySensitivity(settings)
        logModeActivated(settings.operatingMode)
    }

    private fun refreshForModeActivation() {
        cachedSettings = runBlocking { settingsStore.settingsFlow.first() }
        ensureLoops()
        val settings = cachedSettings ?: return
        SessionCredentials.updateFrom(settings)
        AppLogger.activeModeSection = LogSection.forOperatingMode(settings.operatingMode)
        configureWakeLockForMode(settings)
        ensureServerConnected(settings)
        vehicleMotionDetector.register(sensorManager, settings.motionSamplingRate)
        startLocationTracking(settings)
        applyAmbientLightSafely(settings)
        applySoundMonitoringSafely(settings)
        applySensitivity(settings)
        logModeActivated(settings.operatingMode)
        startForeground(NOTIF_ID, buildNotification())
        if (ServerConnectionManager.isConnected() && isValidBaseUrl(settings.serverBaseUrl)) {
            ServerSettingsSync.start(this)
            maybeStartLiveKit(settings)
        }
    }

    private fun startLocationTracking(settings: AppSettings) {
        locationTracker.start(
            settings.gpsIntervalSec * 1000L,
            settings.gpsAccuracyForMode(settings.operatingMode).toPriority(),
        )
    }

    private fun maybeStartLiveKit(settings: AppSettings) {
        // LiveKit publisher is Watchman-only; Copilot/OFF never stream.
        if (settings.operatingMode != OperatingMode.WATCHER) {
            liveKitStreamer.stop()
            return
        }
        if (ServerConnectionManager.isConnected() && isValidBaseUrl(settings.serverBaseUrl)) {
            if (liveKitStreamer.isPublishingSame(
                    settings.serverBaseUrl,
                    settings.streamMode,
                    settings.streamBaseUrl,
                )
            ) {
                return
            }
            liveKitStreamer.start(
                settings.serverBaseUrl,
                settings.streamMode,
                settings.streamBaseUrl,
                settings.streamPort,
            )
        }
    }

    private fun applyAmbientLight(settings: AppSettings) {
        if (settings.ambientLightEnabled) {
            ambientLightSensor.start()
        } else {
            ambientLightSensor.stop()
        }
    }

    private fun applyAmbientLightSafely(settings: AppSettings) {
        try {
            applyAmbientLight(settings)
        } catch (failure: Throwable) {
            AppLogger.warn(
                "Capture",
                "Ambient light update failed: ${failure.javaClass.simpleName}: ${failure.message}",
            )
        }
    }

    private fun applySoundMonitoring(settings: AppSettings) {
        if (shouldSampleNoiseTelemetry(settings)) {
            audioCapture.start()
        } else {
            audioCapture.stop()
        }
    }

    private fun applySoundMonitoringSafely(settings: AppSettings) {
        try {
            applySoundMonitoring(settings)
        } catch (failure: Throwable) {
            AppLogger.warn(
                "Capture",
                "Sound monitoring update failed: ${failure.javaClass.simpleName}: ${failure.message}",
            )
        }
    }

    private fun shouldSampleNoiseTelemetry(settings: AppSettings): Boolean {
        if (!audioCapture.hasRecordPermission()) return false
        if (settings.soundMonitoringEnabled) return true
        return settings.operatingMode == OperatingMode.WATCHER && settings.noiseIntervalSec > 0
    }

    private fun applySensitivity(settings: AppSettings) {
        vehicleMotionDetector.joltSensitivity = settings.joltSensitivity
        vehicleMotionDetector.customJoltScale = settings.customJoltScale
        audioCapture.soundSensitivity = settings.soundSensitivity
        audioCapture.customSoundThreshold = settings.customSoundThreshold
    }

    private fun logModeActivated(mode: OperatingMode) {
        when (mode) {
            OperatingMode.WATCHER ->
                AppLogger.info(LogSection.WATCHMAN, "Watchman", "Watchman activated")
            OperatingMode.COPILOT ->
                AppLogger.info("Capture", "Copilot activated")
            OperatingMode.OFF ->
                AppLogger.info("Capture", "Monitoring stopped")
        }
    }

    private fun stopCapture() {
        running = false
        synchronized(loopLock) {
            loopScope?.cancel()
            loopScope = null
            telemetryJob = null
            uploadJob = null
            diagnosticUploadJob = null
            keepAliveJob = null
            watchdogJob = null
        }
        AppLogger.activeModeSection = null
        DoganCamera.clear()
        vehicleMotionDetector.unregister(sensorManager)
        locationTracker.stop()
        ambientLightSensor.stop()
        audioCapture.stop()
        liveKitStreamer.stop()
        alertManager.release()
        wakeLock?.release()
        wakeLock = null
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        AppLogger.info("Capture", "Monitoring stopped")
    }

    private fun downloadSounds(settings: AppSettings) {
        io.execute {
            if (ServerConnectionManager.isConnected()) {
                soundDownloadManager.fetchAndDownloadAll(settings.serverBaseUrl)
            }
        }
    }

    private fun ensureLoopScope(): CoroutineScope {
        synchronized(loopLock) {
            loopScope?.let { return it }
            val created = CoroutineScope(
                SupervisorJob() + Dispatchers.IO + loopExceptionHandler,
            )
            loopScope = created
            return created
        }
    }

    private fun ensureLoops() {
        if (!running) return
        val scope = ensureLoopScope()
        synchronized(loopLock) {
            if (telemetryJob?.isActive != true) telemetryJob = scope.launch { runTelemetryLoop() }
            if (uploadJob?.isActive != true) uploadJob = scope.launch { runUploadLoop() }
            if (diagnosticUploadJob?.isActive != true) {
                diagnosticUploadJob = scope.launch { runDiagnosticUploadLoop() }
            }
            if (keepAliveJob?.isActive != true) keepAliveJob = scope.launch { runKeepAliveLoop() }
            if (watchdogJob?.isActive != true) watchdogJob = scope.launch { runWatchdog() }
        }
    }

    private suspend fun runTelemetryLoop() {
        while (running) {
            lastTelemetryCycleAt = System.currentTimeMillis()
            var sleepMs = DEFAULT_TELEMETRY_INTERVAL_MS
            try {
                val settings = runBlocking { settingsStore.settingsFlow.first() }
                cachedSettings = settings
                SessionCredentials.updateFrom(settings)
                AppLogger.activeModeSection = LogSection.forOperatingMode(settings.operatingMode)
                applySensitivity(settings)
                configureWakeLockForMode(settings)
                sleepMs = settings.fastestSensorIntervalMs

                if (ServerConnectionManager.isConnected() && isValidBaseUrl(settings.serverBaseUrl)) {
                    collectTelemetryCycle(settings)
                    telemetryDatabase.purgeOlderThan(settings.telemetryRetentionHours)
                    AppLogger.purgeOlderThan(this@CaptureService, settings.logRetentionDays)
                } else if (settings.operatingMode != OperatingMode.OFF) {
                    val now = System.currentTimeMillis()
                    if (now - lastDisconnectedLogAt >= 60_000L) {
                        lastDisconnectedLogAt = now
                        val reason = if (!isValidBaseUrl(settings.serverBaseUrl)) {
                            "invalid API URL ${settings.serverBaseUrl}"
                        } else {
                            "not connected — open Connectivity and Connect"
                        }
                        AppLogger.warn("Telemetry", "Skipping capture/upload: $reason")
                    }
                }

                applyAmbientLightSafely(settings)
                applySoundMonitoringSafely(settings)
                val heartbeatNow = System.currentTimeMillis()
                if (heartbeatNow - lastHeartbeatLogAt >= HEARTBEAT_LOG_INTERVAL_MS) {
                    lastHeartbeatLogAt = heartbeatNow
                    AppLogger.info(
                        "Telemetry",
                        "Cycle heartbeat mode=${settings.operatingMode.name} interval=${sleepMs}ms",
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (outOfMemory: OutOfMemoryError) {
                AppLogger.warn("Telemetry", "Cycle out of memory: ${outOfMemory.message}")
                sleepMs = ERROR_RETRY_DELAY_MS
            } catch (failure: Throwable) {
                AppLogger.error(
                    "Telemetry",
                    "Cycle failed: ${failure.javaClass.simpleName}: ${failure.message}",
                )
                sleepMs = ERROR_RETRY_DELAY_MS
            }
            delay(sleepMs.coerceAtLeast(MIN_LOOP_DELAY_MS))
        }
    }

    private fun collectTelemetryCycle(settings: AppSettings) {
        try {
            collectAndEnqueueTelemetry(settings, dropFrames = false)
        } catch (outOfMemory: OutOfMemoryError) {
            AppLogger.warn(
                "Telemetry",
                "Out of memory while embedding frames; retrying snapshot without frames",
            )
            collectAndEnqueueTelemetry(settings, dropFrames = true)
        }
    }

    private fun collectAndEnqueueTelemetry(settings: AppSettings, dropFrames: Boolean) {
        withTimedWakeLock {
            val includeFrames = !dropFrames && shouldIncludeFrames(settings)
            val pendingActionIds = if (dropFrames) emptyList() else pendingCaptureActionIds(settings)
            val forceCapture = pendingActionIds.isNotEmpty()
            val needFrames = !dropFrames && (includeFrames || forceCapture)
            var takeStill = needFrames
            if (takeStill && !forceCapture && settings.operatingMode == OperatingMode.WATCHER) {
                val now = System.currentTimeMillis()
                if (now - lastWatcherFrameAt < settings.captureIntervalMs) {
                    takeStill = false
                }
            }

            var rearFile: File? = null
            var frontFile: File? = null
            try {
                if (takeStill) {
                    val latch = java.util.concurrent.CountDownLatch(
                        when (settings.activeCamera) {
                            CameraFacing.BOTH -> 2
                            else -> 1
                        },
                    )
                    when (settings.activeCamera) {
                        CameraFacing.FRONT -> {
                            DoganCamera.captureFromFacing(
                                this, this, CameraFacing.FRONT, settings.sendingImageQuality.jpegQuality,
                                { file ->
                                    frontFile = file
                                    latch.countDown()
                                },
                                captureCallbackExecutor,
                            )
                        }
                        CameraFacing.REAR -> {
                            DoganCamera.captureFromFacing(
                                this, this, CameraFacing.REAR, settings.sendingImageQuality.jpegQuality,
                                { file ->
                                    rearFile = file
                                    latch.countDown()
                                },
                                captureCallbackExecutor,
                            )
                        }
                        CameraFacing.BOTH -> {
                            DoganCamera.captureFromFacing(
                                this, this, CameraFacing.REAR, settings.sendingImageQuality.jpegQuality,
                                { file ->
                                    rearFile = file
                                    latch.countDown()
                                },
                                captureCallbackExecutor,
                            )
                            DoganCamera.captureFromFacing(
                                this, this, CameraFacing.FRONT, settings.sendingImageQuality.jpegQuality,
                                { file ->
                                    frontFile = file
                                    latch.countDown()
                                },
                                captureCallbackExecutor,
                            )
                        }
                    }
                    latch.await(CAPTURE_LATCH_TIMEOUT_SEC, java.util.concurrent.TimeUnit.SECONDS)
                    if (settings.operatingMode == OperatingMode.WATCHER &&
                        (rearFile != null || frontFile != null)
                    ) {
                        lastWatcherFrameAt = System.currentTimeMillis()
                    }
                    if (takeStill && rearFile == null && frontFile == null) {
                        AppLogger.warn(
                            "Capture",
                            "Still capture requested but no JPEG (${settings.activeCamera}, policy=${settings.imageUploadPolicyForMode(settings.operatingMode)})",
                        )
                    }
                }

                val now = System.currentTimeMillis()
                val latency = if (lastLatencySampleAt == 0L ||
                    now - lastLatencySampleAt >= settings.latencyIntervalSec * 1000L
                ) {
                    lastLatencySampleAt = now
                    apiClient.pingHealthWithLatency(settings.serverBaseUrl)?.latencyMs ?: -1L
                } else {
                    -1L
                }

                val embedFrames = takeStill && (rearFile != null || frontFile != null)
                val payload = telemetryCollector.collectSnapshot(
                    rearFile,
                    frontFile,
                    latency,
                    embedFrames,
                    settings,
                )
                val rearLen = payload.optString("rear_camera_frame_base64").length
                val frontLen = payload.optString("front_camera_frame_base64").length
                if (embedFrames) {
                    AppLogger.info(
                        "Capture",
                        "Enqueue telemetry with frames rear=${rearLen}b front=${frontLen}b",
                    )
                }
                telemetryDatabase.enqueue(payload)
                if (forceCapture && telemetryHasAttachedFrames(payload)) {
                    acknowledgePendingActions(settings.serverBaseUrl, pendingActionIds)
                }
            } finally {
                rearFile?.delete()
                frontFile?.delete()
            }
        }
    }

    private fun shouldIncludeFrames(settings: AppSettings): Boolean {
        if (settings.operatingMode == OperatingMode.WATCHER) return true
        return settings.imageUploadPolicyForMode(settings.operatingMode) == ImageUploadPolicy.AUTO
    }

    private fun pendingCaptureActionIds(settings: AppSettings): List<Long> {
        val actions = apiClient.fetchPendingActions(settings.serverBaseUrl) ?: return emptyList()
        val actionIds = ArrayList<Long>(actions.length())
        for (index in 0 until actions.length()) {
            val action = actions.optJSONObject(index) ?: continue
            val actionId = action.optLong("id", 0L)
            if (actionId > 0L) {
                actionIds.add(actionId)
            }
        }
        return actionIds
    }

    private fun telemetryHasAttachedFrames(payload: JSONObject): Boolean {
        return payload.optString("rear_camera_frame_base64").isNotBlank() ||
            payload.optString("front_camera_frame_base64").isNotBlank()
    }

    private fun acknowledgePendingActions(baseUrl: String, actionIds: List<Long>) {
        for (actionId in actionIds) {
            if (!apiClient.acknowledgeAction(baseUrl, actionId)) {
                AppLogger.warn("Actions", "Failed to acknowledge action $actionId")
            }
        }
    }

    private suspend fun runUploadLoop() {
        while (running) {
            var sleepMs = DEFAULT_TELEMETRY_INTERVAL_MS
            try {
                val settings = cachedSettings ?: runBlocking { settingsStore.settingsFlow.first() }
                sleepMs = settings.uploadIntervalMs.coerceAtLeast(
                    SettingsStore.MIN_UPLOAD_INTERVAL_SEC * 1000L,
                )
                if (ServerConnectionManager.isConnected() && isValidBaseUrl(settings.serverBaseUrl)) {
                    withTimedWakeLock {
                        val stats = telemetryUploader.uploadPending(settings.serverBaseUrl)
                        if (stats.uploaded > 0 || stats.failed > 0) {
                            AppLogger.info(
                                "Telemetry",
                                "Upload batch ok=${stats.uploaded} failed=${stats.failed} remaining=${stats.remaining}",
                            )
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                AppLogger.warn(
                    "Telemetry",
                    "Upload loop failed: ${failure.javaClass.simpleName}: ${failure.message}",
                )
                sleepMs = ERROR_RETRY_DELAY_MS
            }
            delay(sleepMs.coerceAtLeast(MIN_LOOP_DELAY_MS))
        }
    }

    private suspend fun runDiagnosticUploadLoop() {
        while (running) {
            var sleepMs = DIAGNOSTIC_UPLOAD_INTERVAL_MS
            try {
                val settings = cachedSettings
                if (settings != null && ServerConnectionManager.isConnected()) {
                    for (segment in cabinNoiseArchive.getPendingUploads()) {
                        if (!segment.file.exists()) continue
                        val metadata = JSONObject()
                            .put("segment_id", segment.id)
                            .put("start_ms", segment.startMs)
                            .put("end_ms", segment.endMs)
                            .put("rms_peak", segment.rmsPeak)
                            .put("linked_alert_id", segment.linkedAlertId)
                            .put("mode", segment.mode)
                            .put("device_id", deviceId)
                        val uploaded = apiClient.submitDiagnosticAudio(
                            settings.serverBaseUrl,
                            segment.file,
                            metadata,
                        )
                        if (uploaded) cabinNoiseArchive.markUploaded(segment.id)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                AppLogger.warn(
                    "Diagnostics",
                    "Audio upload loop failed: ${failure.javaClass.simpleName}: ${failure.message}",
                )
                sleepMs = ERROR_RETRY_DELAY_MS
            }
            delay(sleepMs.coerceAtLeast(MIN_LOOP_DELAY_MS))
        }
    }

    private suspend fun runKeepAliveLoop() {
        while (running) {
            var sleepMs = KEEP_ALIVE_INTERVAL_MS
            try {
                val settings = runBlocking { settingsStore.settingsFlow.first() }
                maybeWakeScreen(settings)
                sleepMs = if (settings.screenOnIntervalMin > 0) {
                    minOf(30_000L, settings.screenOnIntervalMin * 60_000L / 2)
                } else {
                    30_000L
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                AppLogger.warn(
                    "KeepAlive",
                    "Keep-alive loop failed: ${failure.javaClass.simpleName}: ${failure.message}",
                )
                sleepMs = 30_000L
            }
            delay(sleepMs.coerceAtLeast(KEEP_ALIVE_INTERVAL_MS))
        }
    }

    private suspend fun runWatchdog() {
        while (running) {
            delay(WATCHDOG_INTERVAL_MS)
            if (!running) return
            val settings = cachedSettings ?: continue
            val interval = settings.fastestSensorIntervalMs.coerceAtLeast(MIN_LOOP_DELAY_MS)
            val stalledFor = System.currentTimeMillis() - lastTelemetryCycleAt
            if (stalledFor <= interval * 3 + WATCHDOG_GRACE_MS) continue
            AppLogger.warn(
                "Telemetry",
                "Watchdog: no telemetry cycle for ${stalledFor}ms; restarting loop",
            )
            synchronized(loopLock) {
                telemetryJob?.cancel()
                telemetryJob = ensureLoopScope().launch { runTelemetryLoop() }
            }
        }
    }

    private fun maybeWakeScreen(settings: AppSettings) {
        if (settings.screenOnIntervalMin <= 0) return
        val now = System.currentTimeMillis()
        val intervalMs = settings.screenOnIntervalMin * 60_000L
        if (now - lastScreenWakeAt < intervalMs) return
        lastScreenWakeAt = now
        ScreenWakeHelper.wakeScreen(this)
        AppLogger.info("KeepAlive", "Screen wake pulse (${settings.screenOnIntervalMin} min interval)")
    }

    private fun isValidBaseUrl(baseUrl: String): Boolean {
        val trimmed = baseUrl.trim()
        return trimmed.startsWith("http://") || trimmed.startsWith("https://")
    }

    private fun ensureServerConnected(settings: AppSettings) {
        if (ServerConnectionManager.isConnected()) return
        if (settings.operatingMode == OperatingMode.OFF) return
        if (!isValidBaseUrl(settings.serverBaseUrl)) {
            AppLogger.warn("Server", "Cannot auto-connect: bad API URL ${settings.serverBaseUrl}")
            return
        }
        if (settings.username.isBlank() || settings.password.isBlank()) {
            AppLogger.warn("Server", "Cannot auto-connect: missing credentials")
            return
        }
        val connected = ServerConnectionManager.connect(applicationContext, settings)
        if (connected) {
            AppLogger.info("Server", "Auto-connected for ${settings.operatingMode.displayName}")
            ServerSettingsSync.start(this)
            maybeStartLiveKit(settings)
        } else {
            AppLogger.error("Server", "Auto-connect failed: ${ServerConnectionManager.lastError}")
        }
    }

    private fun onVehicleBumpDetected(peakAccelerationMps2: Float) {
        io.execute {
            val now = System.currentTimeMillis()
            if (now - lastMotionTriggeredCaptureAt < VehicleMotionDetector.COOLDOWN_MS) return@execute
            lastMotionTriggeredCaptureAt = now
            val settings = cachedSettings ?: return@execute
            modeController.onBump(settings, peakAccelerationMps2)
        }
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Dogan Capture", NotificationManager.IMPORTANCE_LOW),
            )
            nm.createNotificationChannel(
                NotificationChannel(MOTION_CHANNEL, "Motion Alerts", NotificationManager.IMPORTANCE_HIGH),
            )
            nm.createNotificationChannel(
                NotificationChannel("dogan_alerts", "Dogan Alerts", NotificationManager.IMPORTANCE_HIGH),
            )
        }
    }

    private fun buildNotification(): Notification {
        val settings = cachedSettings
        val modeLabel = settings?.operatingMode?.displayName ?: ""
        val contentText = modeLabel.ifBlank { getString(R.string.dogan_running) }
        return NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle(getString(R.string.dogan_running))
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
    }

    private fun configureWakeLockForMode(settings: AppSettings) {
        holdWakeLockIndefinitely =
            settings.operatingMode == OperatingMode.COPILOT && settings.copilotHoldWakeLock
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (wakeLock == null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Dogan::Capture").apply {
                setReferenceCounted(false)
            }
        }
        val lock = wakeLock ?: return
        if (holdWakeLockIndefinitely) {
            if (!lock.isHeld) {
                lock.acquire()
            }
        } else if (lock.isHeld) {
            lock.release()
        }
    }

    private fun withTimedWakeLock(timeoutMs: Long = WORK_WAKE_LOCK_MS, block: () -> Unit) {
        val lock = wakeLock
        if (lock == null || holdWakeLockIndefinitely) {
            block()
            return
        }
        val alreadyHeld = lock.isHeld
        if (!alreadyHeld) {
            lock.acquire(timeoutMs)
        }
        try {
            block()
        } finally {
            if (!alreadyHeld && lock.isHeld) {
                lock.release()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
