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
import kotlinx.coroutines.flow.first
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
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val settingsStore by lazy { SettingsStore(this) }
    private val deviceId by lazy { DeviceIdentity.resolveDeviceId(this) }
    private val apiClient by lazy { DoganApiClient(deviceId = deviceId) }
    private val io = Executors.newSingleThreadExecutor()
    private val uploadExecutor = Executors.newSingleThreadExecutor()
    private val keepAliveExecutor = Executors.newSingleThreadExecutor()
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

    private var running = false
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile private var cachedSettings: AppSettings? = null
    @Volatile private var lastMotionTriggeredCaptureAt = 0L
    @Volatile private var lastScreenWakeAt = 0L

    private lateinit var sensorManager: SensorManager
    private val vehicleMotionDetector = VehicleMotionDetector { peakMps2 ->
        onVehicleBumpDetected(peakMps2)
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        audioCapture = AudioCapture(
            onSpike = { rms ->
                cachedSettings?.let { modeController.onSoundSpike(rms, it) }
            },
            cabinNoiseArchive = cabinNoiseArchive,
        )
        telemetryCollector = TelemetryCollector(
            this, locationTracker, ambientLightSensor, audioCapture, deviceId, resourceMonitor,
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
            }
            else -> {
                if (running) {
                    refreshForModeActivation()
                } else {
                    startCapture()
                }
            }
        }
        return START_STICKY
    }

    private fun startCapture() {
        if (running) return
        running = true
        createChannels()
        startForeground(NOTIF_ID, buildNotification())
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        acquireWakeLock()

        cachedSettings = runBlocking { settingsStore.settingsFlow.first() }
        val settings = cachedSettings ?: return
        SessionCredentials.updateFrom(settings)
        AppLogger.activeModeSection = LogSection.forOperatingMode(settings.operatingMode)

        downloadSounds(settings)
        vehicleMotionDetector.register(sensorManager)
        locationTracker.start(settings.telemetryIntervalMs)
        ambientLightSensor.start()
        audioCapture.start()
        scheduleTelemetryLoop()
        scheduleKeepAliveLoop()
        scheduleUploadLoop()
        scheduleDiagnosticUploadLoop()

        if (ServerConnectionManager.isConnected()) {
            ServerSettingsSync.start(this)
            liveKitStreamer.start(settings.serverBaseUrl, settings.streamMode)
        }

        applySensitivity(settings)
        logModeActivated(settings.operatingMode)
    }

    private fun refreshForModeActivation() {
        cachedSettings = runBlocking { settingsStore.settingsFlow.first() }
        val settings = cachedSettings ?: return
        SessionCredentials.updateFrom(settings)
        AppLogger.activeModeSection = LogSection.forOperatingMode(settings.operatingMode)
        applySensitivity(settings)
        logModeActivated(settings.operatingMode)
        startForeground(NOTIF_ID, buildNotification())
        if (ServerConnectionManager.isConnected() && isValidBaseUrl(settings.serverBaseUrl)) {
            ServerSettingsSync.start(this)
            liveKitStreamer.start(settings.serverBaseUrl, settings.streamMode)
        }
    }

    private fun applySensitivity(settings: AppSettings) {
        vehicleMotionDetector.joltSensitivity = settings.joltSensitivity
        vehicleMotionDetector.customJoltScale = settings.customJoltScale
        audioCapture.soundSensitivity = settings.soundSensitivity
        audioCapture.customSoundThreshold = settings.customSoundThreshold
    }

    private fun logModeActivated(mode: OperatingMode) {
        when (mode) {
            OperatingMode.SPOTTER ->
                AppLogger.info(LogSection.SPOTTER, "Spotter", "Spotter activated")
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

    private fun scheduleTelemetryLoop() {
        io.execute {
            while (running) {
                try {
                    val settings = runBlocking { settingsStore.settingsFlow.first() }
                    cachedSettings = settings
                    SessionCredentials.updateFrom(settings)
                    AppLogger.activeModeSection = LogSection.forOperatingMode(settings.operatingMode)
                    applySensitivity(settings)

                    if (ServerConnectionManager.isConnected() && isValidBaseUrl(settings.serverBaseUrl)) {
                        collectAndEnqueueTelemetry(settings)
                        telemetryDatabase.purgeOlderThan(settings.telemetryRetentionHours)
                        AppLogger.purgeOlderThan(this@CaptureService, settings.logRetentionDays)
                    }

                    Thread.sleep(settings.telemetryIntervalMs)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                } catch (_: Exception) {
                    Thread.sleep(5_000L)
                }
            }
        }
    }

    private fun collectAndEnqueueTelemetry(settings: AppSettings) {
        val includeFrames = shouldIncludeFrames(settings)
        val pendingActionIds = pendingCaptureActionIds(settings)
        val forceCapture = pendingActionIds.isNotEmpty()
        val latch = java.util.concurrent.CountDownLatch(
            when (settings.activeCamera) {
                CameraFacing.BOTH -> 2
                else -> 1
            },
        )
        var rearFile: File? = null
        var frontFile: File? = null

        when (settings.activeCamera) {
            CameraFacing.FRONT -> {
                DoganCamera.captureFromFacing(this, this, CameraFacing.FRONT, settings.sendingImageQuality.jpegQuality, { file ->
                    frontFile = file
                    latch.countDown()
                }, captureCallbackExecutor)
            }
            CameraFacing.REAR -> {
                DoganCamera.captureFromFacing(this, this, CameraFacing.REAR, settings.sendingImageQuality.jpegQuality, { file ->
                    rearFile = file
                    latch.countDown()
                }, captureCallbackExecutor)
            }
            CameraFacing.BOTH -> {
                DoganCamera.captureFromFacing(this, this, CameraFacing.REAR, settings.sendingImageQuality.jpegQuality, { file ->
                    rearFile = file
                    latch.countDown()
                }, captureCallbackExecutor)
                DoganCamera.captureFromFacing(this, this, CameraFacing.FRONT, settings.sendingImageQuality.jpegQuality, { file ->
                    frontFile = file
                    latch.countDown()
                }, captureCallbackExecutor)
            }
        }

        latch.await(15, java.util.concurrent.TimeUnit.SECONDS)

        val health = apiClient.pingHealthWithLatency(settings.serverBaseUrl)
        val latency = health?.latencyMs ?: -1L

        val attachFrames = includeFrames || forceCapture
        val payload = telemetryCollector.collectSnapshot(rearFile, frontFile, latency, attachFrames)
        telemetryDatabase.enqueue(payload)
        if (forceCapture && telemetryHasAttachedFrames(payload)) {
            acknowledgePendingActions(settings.serverBaseUrl, pendingActionIds)
        }
        rearFile?.delete()
        frontFile?.delete()
    }

    private fun shouldIncludeFrames(settings: AppSettings): Boolean {
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

    private fun scheduleUploadLoop() {
        uploadExecutor.execute {
            while (running) {
                try {
                    val settings = cachedSettings ?: runBlocking { settingsStore.settingsFlow.first() }
                    if (ServerConnectionManager.isConnected() && isValidBaseUrl(settings.serverBaseUrl)) {
                        val stats = telemetryUploader.uploadPending(settings.serverBaseUrl)
                        if (stats.uploaded > 0) {
                            AppLogger.info("Telemetry", "Uploaded ${stats.uploaded}, remaining ${stats.remaining}")
                        }
                    }
                    Thread.sleep(2_000L)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                } catch (_: Exception) {
                    Thread.sleep(5_000L)
                }
            }
        }
    }

    private fun scheduleDiagnosticUploadLoop() {
        uploadExecutor.execute {
            while (running) {
                try {
                    val settings = cachedSettings ?: continue
                    if (!ServerConnectionManager.isConnected()) continue
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
                        val ok = apiClient.submitDiagnosticAudio(
                            settings.serverBaseUrl,
                            segment.file,
                            metadata,
                        )
                        if (ok) cabinNoiseArchive.markUploaded(segment.id)
                    }
                    Thread.sleep(30_000L)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                } catch (_: Exception) {
                    Thread.sleep(30_000L)
                }
            }
        }
    }

    private fun scheduleKeepAliveLoop() {
        keepAliveExecutor.execute {
            while (running) {
                try {
                    val settings = runBlocking { settingsStore.settingsFlow.first() }
                    maybeWakeScreen(settings)
                    val checkMs = if (settings.screenOnIntervalMin > 0) {
                        minOf(30_000L, settings.screenOnIntervalMin * 60_000L / 2)
                    } else {
                        30_000L
                    }
                    Thread.sleep(checkMs.coerceAtLeast(5_000L))
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                } catch (_: Exception) {
                    Thread.sleep(30_000L)
                }
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

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Dogan::Capture").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
