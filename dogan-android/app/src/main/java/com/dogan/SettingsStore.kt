package com.dogan

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("dogan_settings")

/** Per-mode settings bundle. */
data class ModeSettings(
    val imageUploadPolicy: ImageUploadPolicy,
)

/** Snapshot of all user-configurable Dogan settings. */
data class AppSettings(
    val apiEndpoint: String,
    val apiPort: Int,
    val streamEndpoint: String,
    val streamPort: Int,
    val username: String,
    val password: String,
    val captureIntervalMs: Long,
    val telemetryIntervalSec: Int,
    val uploadIntervalSec: Int,
    val activeCamera: CameraFacing,
    val operatingMode: OperatingMode,
    val screenOnIntervalMin: Int,
    val sendingImageQuality: ImageQuality,
    val alertVolume: AlertVolume,
    val alertDuration: AlertDuration,
    val streamMode: StreamMode,
    val settingsSyncIntervalSec: Int,
    val telemetryRetentionHours: Int,
    val joltSensitivity: SensitivityLevel,
    val soundSensitivity: SensitivityLevel,
    val customJoltScale: Float,
    val customSoundThreshold: Double,
    val logRetentionDays: Int,
    val soundMonitoringEnabled: Boolean,
    val motionSamplingRate: MotionSamplingRate,
    val ambientLightEnabled: Boolean,
    val watcherGpsAccuracy: GpsAccuracy,
    val copilotGpsAccuracy: GpsAccuracy,
    val copilotHoldWakeLock: Boolean,
    val watcherSettings: ModeSettings,
    val copilotSettings: ModeSettings,
    val copilotAlertVolume: AlertVolume,
    val copilotAlertsEnabled: Boolean,
    val copilotDistanceControlEnabled: Boolean,
    val gpsIntervalSec: Int,
    val batteryIntervalSec: Int,
    val temperatureIntervalSec: Int,
    val noiseIntervalSec: Int,
    val joltIntervalSec: Int,
    val networkTypeIntervalSec: Int,
    val sim1SignalIntervalSec: Int,
    val sim2SignalIntervalSec: Int,
    val latencyIntervalSec: Int,
) {
    val serverBaseUrl: String get() = EndpointUrlBuilder.buildApiBaseUrl(apiEndpoint, apiPort)
    val streamBaseUrl: String get() = EndpointUrlBuilder.buildStreamUrl(streamEndpoint, streamPort)
    val telemetryIntervalMs: Long get() = telemetryIntervalSec * 1000L
    val fastestSensorIntervalMs: Long
        get() = listOf(
            gpsIntervalSec,
            batteryIntervalSec,
            temperatureIntervalSec,
            noiseIntervalSec,
            joltIntervalSec,
            networkTypeIntervalSec,
            sim1SignalIntervalSec,
            sim2SignalIntervalSec,
            latencyIntervalSec,
        ).min() * 1000L
    val uploadIntervalMs: Long get() = uploadIntervalSec * 1000L
    val intervalSec: Float get() = captureIntervalMs / 1000f
    val periodSec: Int get() = (captureIntervalMs / 1000L).toInt().coerceAtLeast(1)

    fun modeSettings(mode: OperatingMode): ModeSettings = when (mode) {
        OperatingMode.WATCHER -> watcherSettings
        OperatingMode.COPILOT -> copilotSettings
        OperatingMode.OFF -> watcherSettings
    }

    fun imageUploadPolicyForMode(mode: OperatingMode): ImageUploadPolicy =
        modeSettings(mode).imageUploadPolicy

    fun gpsAccuracyForMode(mode: OperatingMode): GpsAccuracy = when (mode) {
        OperatingMode.WATCHER, OperatingMode.OFF -> watcherGpsAccuracy
        OperatingMode.COPILOT -> copilotGpsAccuracy
    }
}

/** Persists and exposes Dogan settings via DataStore preferences. */
class SettingsStore(private val context: Context) {
    private val legacyUrlKey = stringPreferencesKey("server_url")
    private val apiEndpointKey = stringPreferencesKey("api_endpoint")
    private val apiPortKey = intPreferencesKey("api_port")
    private val streamEndpointKey = stringPreferencesKey("stream_endpoint")
    private val streamPortKey = intPreferencesKey("stream_port")
    private val usernameKey = stringPreferencesKey("username")
    private val passwordKey = stringPreferencesKey("password")
    private val intervalKey = longPreferencesKey("capture_interval_ms")
    private val telemetryIntervalSecKey = intPreferencesKey("telemetry_interval_sec")
    private val legacyTelemetryIntervalKey = longPreferencesKey("telemetry_interval_ms")
    private val uploadIntervalSecKey = intPreferencesKey("upload_interval_sec")
    private val cameraKey = stringPreferencesKey("active_camera")
    private val operatingModeKey = stringPreferencesKey("operating_mode")
    private val screenOnIntervalMinKey = intPreferencesKey("screen_on_interval_min")
    private val legacyScreenOnIntervalSecKey = intPreferencesKey("screen_on_interval_sec")
    private val sendingQualityKey = stringPreferencesKey("sending_image_quality")
    private val alertVolumeKey = stringPreferencesKey("alert_volume")
    private val alertDurationKey = stringPreferencesKey("alert_duration")
    private val streamModeKey = stringPreferencesKey("stream_mode")
    private val settingsSyncIntervalKey = intPreferencesKey("settings_sync_interval_sec")
    private val telemetryRetentionHoursKey = intPreferencesKey("telemetry_retention_hours")
    private val joltSensitivityKey = stringPreferencesKey("jolt_sensitivity")
    private val soundSensitivityKey = stringPreferencesKey("sound_sensitivity")
    private val customJoltScaleKey = floatPreferencesKey("custom_jolt_scale")
    private val customSoundThresholdKey = floatPreferencesKey("custom_sound_threshold")
    private val logRetentionDaysKey = intPreferencesKey("log_retention_days")
    private val soundMonitoringEnabledKey = booleanPreferencesKey("sound_monitoring_enabled")
    private val motionSamplingRateKey = stringPreferencesKey("motion_sampling_rate")
    private val ambientLightEnabledKey = booleanPreferencesKey("ambient_light_enabled")
    private val watcherGpsAccuracyKey = stringPreferencesKey("watcher_gps_accuracy")
    private val copilotGpsAccuracyKey = stringPreferencesKey("copilot_gps_accuracy")
    private val copilotHoldWakeLockKey = booleanPreferencesKey("copilot_hold_wake_lock")
    private val copilotAlertVolumeKey = stringPreferencesKey("copilot_alert_volume")
    private val copilotAlertsEnabledKey = booleanPreferencesKey("copilot_alerts_enabled")
    private val copilotDistanceControlKey = booleanPreferencesKey("copilot_distance_control_enabled")
    private val gpsIntervalSecKey = intPreferencesKey("gps_interval_sec")
    private val batteryIntervalSecKey = intPreferencesKey("battery_interval_sec")
    private val temperatureIntervalSecKey = intPreferencesKey("temperature_interval_sec")
    private val noiseIntervalSecKey = intPreferencesKey("noise_interval_sec")
    private val joltIntervalSecKey = intPreferencesKey("jolt_interval_sec")
    private val networkTypeIntervalSecKey = intPreferencesKey("network_type_interval_sec")
    private val sim1SignalIntervalSecKey = intPreferencesKey("sim1_signal_interval_sec")
    private val sim2SignalIntervalSecKey = intPreferencesKey("sim2_signal_interval_sec")
    private val latencyIntervalSecKey = intPreferencesKey("latency_interval_sec")
    private fun modeImageUploadKey(mode: String) = stringPreferencesKey("${mode}_image_upload_policy")

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { pref ->
        val legacyUrl = pref[legacyUrlKey]
        val legacyTelemetryMs = pref[legacyTelemetryIntervalKey]

        val apiEndpoint = pref[apiEndpointKey]
            ?: legacyUrl?.let { EndpointUrlBuilder.defaultEndpointFromLegacyUrl(it) }
            ?: DEFAULT_API_ENDPOINT
        val apiPort = pref[apiPortKey]
            ?: legacyUrl?.let { EndpointUrlBuilder.defaultPortFromLegacyUrl(it) }
            ?: DEFAULT_API_PORT

        AppSettings(
            apiEndpoint = apiEndpoint,
            apiPort = apiPort,
            streamEndpoint = pref[streamEndpointKey] ?: DEFAULT_STREAM_ENDPOINT,
            streamPort = pref[streamPortKey] ?: DEFAULT_STREAM_PORT,
            username = normalizeFleetUsername(pref[usernameKey]),
            password = normalizeFleetPassword(pref[usernameKey], pref[passwordKey]),
            captureIntervalMs = normalizeIntervalMs(pref[intervalKey] ?: DEFAULT_INTERVAL_MS),
            telemetryIntervalSec = normalizeTelemetryIntervalSec(
                pref[telemetryIntervalSecKey]
                    ?: legacyTelemetryMs?.let { (it / 1000).toInt() }
                    ?: DEFAULT_TELEMETRY_INTERVAL_SEC,
            ),
            uploadIntervalSec = normalizeUploadIntervalSec(
                pref[uploadIntervalSecKey] ?: DEFAULT_UPLOAD_INTERVAL_SEC,
            ),
            activeCamera = CameraFacing.fromStoredValue(pref[cameraKey]),
            operatingMode = OperatingMode.fromStoredValue(pref[operatingModeKey]),
            screenOnIntervalMin = readScreenOnIntervalMin(pref),
            sendingImageQuality = ImageQuality.fromStoredValue(pref[sendingQualityKey]),
            alertVolume = AlertVolume.fromStoredValue(pref[alertVolumeKey]),
            alertDuration = AlertDuration.fromStoredValue(pref[alertDurationKey]),
            streamMode = StreamMode.fromStoredValue(pref[streamModeKey]),
            settingsSyncIntervalSec = normalizeSettingsSyncIntervalSec(
                pref[settingsSyncIntervalKey] ?: DEFAULT_SETTINGS_SYNC_INTERVAL_SEC,
            ),
            telemetryRetentionHours = normalizeRetentionHours(
                pref[telemetryRetentionHoursKey] ?: DEFAULT_TELEMETRY_RETENTION_HOURS,
            ),
            joltSensitivity = SensitivityLevel.fromStoredValue(pref[joltSensitivityKey]),
            soundSensitivity = SensitivityLevel.fromStoredValue(pref[soundSensitivityKey]),
            customJoltScale = normalizeCustomJoltScale(
                pref[customJoltScaleKey] ?: DEFAULT_CUSTOM_JOLT_SCALE,
            ),
            customSoundThreshold = normalizeCustomSoundThreshold(
                (pref[customSoundThresholdKey] ?: DEFAULT_CUSTOM_SOUND_THRESHOLD.toFloat()).toDouble(),
            ),
            logRetentionDays = normalizeLogRetentionDays(pref[logRetentionDaysKey] ?: DEFAULT_LOG_RETENTION_DAYS),
            soundMonitoringEnabled = pref[soundMonitoringEnabledKey] ?: true,
            motionSamplingRate = MotionSamplingRate.fromStoredValue(pref[motionSamplingRateKey]),
            ambientLightEnabled = pref[ambientLightEnabledKey] ?: true,
            watcherGpsAccuracy = GpsAccuracy.fromStoredValue(
                pref[watcherGpsAccuracyKey],
                GpsAccuracy.BALANCED,
            ),
            copilotGpsAccuracy = GpsAccuracy.fromStoredValue(
                pref[copilotGpsAccuracyKey],
                GpsAccuracy.HIGH,
            ),
            copilotHoldWakeLock = pref[copilotHoldWakeLockKey] ?: true,
            watcherSettings = readModeSettings(pref, "watcher"),
            copilotSettings = readModeSettings(pref, "copilot"),
            copilotAlertVolume = AlertVolume.fromStoredValue(pref[copilotAlertVolumeKey]),
            copilotAlertsEnabled = pref[copilotAlertsEnabledKey] ?: false,
            copilotDistanceControlEnabled = pref[copilotDistanceControlKey] ?: false,
            gpsIntervalSec = normalizeSensorIntervalSec(pref[gpsIntervalSecKey] ?: DEFAULT_GPS_INTERVAL_SEC),
            batteryIntervalSec = normalizeSensorIntervalSec(pref[batteryIntervalSecKey] ?: DEFAULT_BATTERY_INTERVAL_SEC),
            temperatureIntervalSec = normalizeSensorIntervalSec(pref[temperatureIntervalSecKey] ?: DEFAULT_TEMPERATURE_INTERVAL_SEC),
            noiseIntervalSec = normalizeSensorIntervalSec(pref[noiseIntervalSecKey] ?: DEFAULT_NOISE_INTERVAL_SEC),
            joltIntervalSec = normalizeSensorIntervalSec(pref[joltIntervalSecKey] ?: DEFAULT_JOLT_INTERVAL_SEC),
            networkTypeIntervalSec = normalizeSensorIntervalSec(pref[networkTypeIntervalSecKey] ?: DEFAULT_NETWORK_TYPE_INTERVAL_SEC),
            sim1SignalIntervalSec = normalizeSensorIntervalSec(pref[sim1SignalIntervalSecKey] ?: DEFAULT_SIM1_SIGNAL_INTERVAL_SEC),
            sim2SignalIntervalSec = normalizeSensorIntervalSec(pref[sim2SignalIntervalSecKey] ?: DEFAULT_SIM2_SIGNAL_INTERVAL_SEC),
            latencyIntervalSec = normalizeSensorIntervalSec(pref[latencyIntervalSecKey] ?: DEFAULT_LATENCY_INTERVAL_SEC),
        )
    }

    suspend fun save(settings: AppSettings) {
        context.dataStore.edit { pref ->
            pref[apiEndpointKey] = settings.apiEndpoint.trim()
            pref[apiPortKey] = settings.apiPort
            pref[streamEndpointKey] = settings.streamEndpoint.trim()
            pref[streamPortKey] = settings.streamPort
            pref[usernameKey] = settings.username.trim()
            pref[passwordKey] = settings.password
            pref[legacyUrlKey] = settings.serverBaseUrl
            pref[intervalKey] = normalizeIntervalMs(settings.captureIntervalMs)
            pref[telemetryIntervalSecKey] = settings.telemetryIntervalSec
            pref[uploadIntervalSecKey] = normalizeUploadIntervalSec(settings.uploadIntervalSec)
            pref[cameraKey] = settings.activeCamera.toStoredValue()
            pref[operatingModeKey] = settings.operatingMode.toStoredValue()
            pref[screenOnIntervalMinKey] = normalizeScreenOnIntervalMin(settings.screenOnIntervalMin)
            pref.remove(legacyScreenOnIntervalSecKey)
            pref[sendingQualityKey] = settings.sendingImageQuality.toStoredValue()
            pref[alertVolumeKey] = settings.alertVolume.toStoredValue()
            pref[alertDurationKey] = settings.alertDuration.toStoredValue()
            pref[streamModeKey] = settings.streamMode.toStoredValue()
            pref[settingsSyncIntervalKey] = settings.settingsSyncIntervalSec
            pref[telemetryRetentionHoursKey] = settings.telemetryRetentionHours
            pref[joltSensitivityKey] = settings.joltSensitivity.toStoredValue()
            pref[soundSensitivityKey] = settings.soundSensitivity.toStoredValue()
            pref[customJoltScaleKey] = settings.customJoltScale
            pref[customSoundThresholdKey] = settings.customSoundThreshold.toFloat()
            pref[logRetentionDaysKey] = settings.logRetentionDays
            pref[soundMonitoringEnabledKey] = settings.soundMonitoringEnabled
            pref[motionSamplingRateKey] = settings.motionSamplingRate.toStoredValue()
            pref[ambientLightEnabledKey] = settings.ambientLightEnabled
            pref[watcherGpsAccuracyKey] = settings.watcherGpsAccuracy.toStoredValue()
            pref[copilotGpsAccuracyKey] = settings.copilotGpsAccuracy.toStoredValue()
            pref[copilotHoldWakeLockKey] = settings.copilotHoldWakeLock
            pref[copilotAlertVolumeKey] = settings.copilotAlertVolume.toStoredValue()
            pref[copilotAlertsEnabledKey] = settings.copilotAlertsEnabled
            pref[copilotDistanceControlKey] = settings.copilotDistanceControlEnabled
            pref[gpsIntervalSecKey] = normalizeSensorIntervalSec(settings.gpsIntervalSec)
            pref[batteryIntervalSecKey] = normalizeSensorIntervalSec(settings.batteryIntervalSec)
            pref[temperatureIntervalSecKey] = normalizeSensorIntervalSec(settings.temperatureIntervalSec)
            pref[noiseIntervalSecKey] = normalizeSensorIntervalSec(settings.noiseIntervalSec)
            pref[joltIntervalSecKey] = normalizeSensorIntervalSec(settings.joltIntervalSec)
            pref[networkTypeIntervalSecKey] = normalizeSensorIntervalSec(settings.networkTypeIntervalSec)
            pref[sim1SignalIntervalSecKey] = normalizeSensorIntervalSec(settings.sim1SignalIntervalSec)
            pref[sim2SignalIntervalSecKey] = normalizeSensorIntervalSec(settings.sim2SignalIntervalSec)
            pref[latencyIntervalSecKey] = normalizeSensorIntervalSec(settings.latencyIntervalSec)
            writeModeSettings(pref, "watcher", settings.watcherSettings)
            writeModeSettings(pref, "copilot", settings.copilotSettings)
        }
    }

    suspend fun updateActiveCamera(camera: CameraFacing) {
        context.dataStore.edit { pref ->
            pref[cameraKey] = camera.toStoredValue()
        }
    }

    suspend fun updateOperatingMode(mode: OperatingMode) {
        context.dataStore.edit { pref ->
            pref[operatingModeKey] = mode.toStoredValue()
        }
    }

    private fun readModeSettings(pref: Preferences, mode: String): ModeSettings {
        return ModeSettings(
            imageUploadPolicy = ImageUploadPolicy.fromStoredValue(pref[modeImageUploadKey(mode)]),
        )
    }

    private fun writeModeSettings(
        pref: androidx.datastore.preferences.core.MutablePreferences,
        mode: String,
        settings: ModeSettings,
    ) {
        pref[modeImageUploadKey(mode)] = settings.imageUploadPolicy.toStoredValue()
    }

    private fun readScreenOnIntervalMin(pref: Preferences): Int {
        pref[screenOnIntervalMinKey]?.let { return normalizeScreenOnIntervalMin(it) }
        val legacySec = pref[legacyScreenOnIntervalSecKey]
        if (legacySec != null) {
            return normalizeScreenOnIntervalMin((legacySec + 59) / 60)
        }
        return DEFAULT_SCREEN_ON_INTERVAL_MIN
    }

    private fun normalizeFleetUsername(stored: String?): String {
        val raw = stored?.trim().orEmpty()
        return when (raw) {
            "", "admin", "armin" -> DEFAULT_USERNAME
            else -> raw
        }
    }

    private fun normalizeFleetPassword(storedUsername: String?, storedPassword: String?): String {
        val user = storedUsername?.trim().orEmpty()
        val pass = storedPassword.orEmpty()
        return when {
            user == "admin" && (pass.isBlank() || pass == "admin") -> DEFAULT_PASSWORD
            user == "armin" && (pass.isBlank() || pass == "dogan123") -> DEFAULT_PASSWORD
            pass.isBlank() -> DEFAULT_PASSWORD
            else -> pass
        }
    }

    companion object {
        const val DEFAULT_USERNAME = "dogan"
        const val DEFAULT_PASSWORD = "Kp7#mQv2\$nLx9wRd4!bT"
        const val DEFAULT_API_ENDPOINT = "dogan-api.xaigrok.ir"
        const val DEFAULT_API_PORT = 443
        const val DEFAULT_STREAM_ENDPOINT = "dogan-livekit.xaigrok.ir"
        const val DEFAULT_STREAM_PORT = 443
        const val DEFAULT_CUSTOM_JOLT_SCALE = 1.0f
        const val DEFAULT_CUSTOM_SOUND_THRESHOLD = 2500.0
        const val DEFAULT_INTERVAL_MS = 5_000L
        const val DEFAULT_GPS_INTERVAL_SEC = 60
        const val DEFAULT_BATTERY_INTERVAL_SEC = 60
        const val DEFAULT_TEMPERATURE_INTERVAL_SEC = 60
        const val DEFAULT_NOISE_INTERVAL_SEC = 1
        const val DEFAULT_JOLT_INTERVAL_SEC = 1
        const val DEFAULT_NETWORK_TYPE_INTERVAL_SEC = 60
        const val DEFAULT_SIM1_SIGNAL_INTERVAL_SEC = 30
        const val DEFAULT_SIM2_SIGNAL_INTERVAL_SEC = 30
        const val DEFAULT_LATENCY_INTERVAL_SEC = 1
        const val MIN_SENSOR_INTERVAL_SEC = 1
        const val MAX_SENSOR_INTERVAL_SEC = 3600
        const val DEFAULT_TELEMETRY_INTERVAL_SEC = 5
        const val DEFAULT_UPLOAD_INTERVAL_SEC = 2
        const val MIN_INTERVAL_MS = 100L
        const val MAX_INTERVAL_MS = 3_600_000L
        const val MIN_TELEMETRY_INTERVAL_SEC = 1
        const val MAX_TELEMETRY_INTERVAL_SEC = 60
        const val MIN_UPLOAD_INTERVAL_SEC = 1
        const val MAX_UPLOAD_INTERVAL_SEC = 60
        const val DEFAULT_SCREEN_ON_INTERVAL_MIN = 0
        const val MAX_SCREEN_ON_INTERVAL_MIN = 60
        const val DEFAULT_SETTINGS_SYNC_INTERVAL_SEC = 15
        const val MIN_SETTINGS_SYNC_INTERVAL_SEC = 10
        const val MAX_SETTINGS_SYNC_INTERVAL_SEC = 3600
        const val DEFAULT_TELEMETRY_RETENTION_HOURS = 72
        const val DEFAULT_LOG_RETENTION_DAYS = 7

        fun normalizeIntervalMs(intervalMs: Long): Long =
            intervalMs.coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS)

        fun normalizeSensorIntervalSec(seconds: Int): Int =
            seconds.coerceIn(MIN_SENSOR_INTERVAL_SEC, MAX_SENSOR_INTERVAL_SEC)

        fun normalizeTelemetryIntervalSec(seconds: Int): Int =
            seconds.coerceIn(MIN_TELEMETRY_INTERVAL_SEC, MAX_TELEMETRY_INTERVAL_SEC)

        fun normalizeUploadIntervalSec(seconds: Int): Int =
            seconds.coerceIn(MIN_UPLOAD_INTERVAL_SEC, MAX_UPLOAD_INTERVAL_SEC)

        fun normalizeScreenOnIntervalMin(minutes: Int): Int =
            minutes.coerceIn(0, MAX_SCREEN_ON_INTERVAL_MIN)

        fun normalizeSettingsSyncIntervalSec(seconds: Int): Int =
            seconds.coerceIn(MIN_SETTINGS_SYNC_INTERVAL_SEC, MAX_SETTINGS_SYNC_INTERVAL_SEC)

        fun normalizeRetentionHours(hours: Int): Int =
            hours.coerceIn(1, 720)

        fun normalizeLogRetentionDays(days: Int): Int =
            days.coerceIn(1, 365)

        fun normalizeCustomJoltScale(scale: Float): Float =
            scale.coerceIn(0.1f, 5.0f)

        fun normalizeCustomSoundThreshold(threshold: Double): Double =
            threshold.coerceIn(100.0, 20_000.0)
    }
}
