package com.dogan

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Polls the Dogan server for device settings and merges them into local storage. */
object ServerSettingsSync {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var syncJob: Job? = null

    fun start(context: Context) {
        if (syncJob?.isActive == true) return
        val appContext = context.applicationContext
        syncJob = scope.launch {
            val settingsStore = SettingsStore(appContext)
            val deviceId = DeviceIdentity.resolveDeviceId(appContext)
            val apiClient = DoganApiClient(deviceId = deviceId)

            while (isActive && ServerConnectionManager.isConnected()) {
                val local = settingsStore.settingsFlow.first()
                SessionCredentials.updateFrom(local)
                val intervalSec = local.settingsSyncIntervalSec.coerceAtLeast(
                    SettingsStore.MIN_SETTINGS_SYNC_INTERVAL_SEC,
                )

                if (isValidBaseUrl(local.serverBaseUrl)) {
                    val remote = apiClient.fetchSettings(local.serverBaseUrl)
                    if (remote != null) {
                        val merged = mergeSettings(local, remote)
                        if (merged != local) {
                            settingsStore.save(merged)
                            AppLogger.info("SettingsSync", "Applied server settings")
                        }
                    }
                }

                delay(intervalSec * 1000L)
            }
        }
    }

    fun stop() {
        syncJob?.cancel()
        syncJob = null
    }

    fun restart(context: Context) {
        stop()
        start(context)
    }

    private fun mergeSettings(local: AppSettings, remote: JSONObject): AppSettings {
        var updated = local

        if (remote.has("capture_interval_ms")) {
            updated = updated.copy(
                captureIntervalMs = SettingsStore.normalizeIntervalMs(remote.getLong("capture_interval_ms")),
            )
        }
        if (remote.has("telemetry_interval_sec")) {
            updated = updated.copy(
                telemetryIntervalSec = SettingsStore.normalizeTelemetryIntervalSec(remote.getInt("telemetry_interval_sec")),
            )
        } else if (remote.has("telemetry_interval_ms")) {
            updated = updated.copy(
                telemetryIntervalSec = SettingsStore.normalizeTelemetryIntervalSec(
                    (remote.getLong("telemetry_interval_ms") / 1000).toInt(),
                ),
            )
        }
        if (remote.has("upload_interval_sec")) {
            updated = updated.copy(
                uploadIntervalSec = SettingsStore.normalizeUploadIntervalSec(remote.getInt("upload_interval_sec")),
            )
        }
        if (remote.has("api_endpoint")) {
            updated = updated.copy(apiEndpoint = remote.getString("api_endpoint"))
        }
        if (remote.has("api_port")) {
            updated = updated.copy(apiPort = remote.getInt("api_port"))
        }
        if (remote.has("stream_endpoint")) {
            updated = updated.copy(streamEndpoint = remote.getString("stream_endpoint"))
        }
        if (remote.has("stream_port")) {
            updated = updated.copy(streamPort = remote.getInt("stream_port"))
        }
        if (remote.has("active_camera")) {
            updated = updated.copy(activeCamera = CameraFacing.fromStoredValue(remote.getString("active_camera")))
        }
        if (remote.has("screen_on_interval_min")) {
            updated = updated.copy(
                screenOnIntervalMin = SettingsStore.normalizeScreenOnIntervalMin(
                    remote.getInt("screen_on_interval_min"),
                ),
            )
        }
        if (remote.has("telemetry_retention_hours")) {
            updated = updated.copy(
                telemetryRetentionHours = SettingsStore.normalizeRetentionHours(remote.getInt("telemetry_retention_hours")),
            )
        }
        if (remote.has("jolt_sensitivity")) {
            updated = updated.copy(joltSensitivity = SensitivityLevel.fromStoredValue(remote.getString("jolt_sensitivity")))
        }
        if (remote.has("sound_sensitivity")) {
            updated = updated.copy(soundSensitivity = SensitivityLevel.fromStoredValue(remote.getString("sound_sensitivity")))
        }
        if (remote.has("custom_jolt_scale")) {
            updated = updated.copy(
                customJoltScale = SettingsStore.normalizeCustomJoltScale(
                    remote.getDouble("custom_jolt_scale").toFloat(),
                ),
            )
        }
        if (remote.has("custom_sound_threshold")) {
            updated = updated.copy(
                customSoundThreshold = SettingsStore.normalizeCustomSoundThreshold(
                    remote.getDouble("custom_sound_threshold"),
                ),
            )
        }
        if (remote.has("log_retention_days")) {
            updated = updated.copy(
                logRetentionDays = SettingsStore.normalizeLogRetentionDays(remote.getInt("log_retention_days")),
            )
        }
        if (remote.has("stream_mode")) {
            updated = updated.copy(streamMode = StreamMode.fromStoredValue(remote.getString("stream_mode")))
        }
        if (remote.has("settings_sync_interval_sec")) {
            updated = updated.copy(
                settingsSyncIntervalSec = SettingsStore.normalizeSettingsSyncIntervalSec(
                    remote.getInt("settings_sync_interval_sec"),
                ),
            )
        }
        if (remote.has("sound_monitoring_enabled")) {
            updated = updated.copy(soundMonitoringEnabled = remote.getBoolean("sound_monitoring_enabled"))
        }
        if (remote.has("motion_sampling_rate")) {
            updated = updated.copy(
                motionSamplingRate = MotionSamplingRate.fromStoredValue(remote.getString("motion_sampling_rate")),
            )
        }
        if (remote.has("ambient_light_enabled")) {
            updated = updated.copy(ambientLightEnabled = remote.getBoolean("ambient_light_enabled"))
        }
        if (remote.has("watcher_gps_accuracy")) {
            updated = updated.copy(
                watcherGpsAccuracy = GpsAccuracy.fromStoredValue(
                    remote.getString("watcher_gps_accuracy"),
                    GpsAccuracy.BALANCED,
                ),
            )
        }
        updated = updated.copy(
            watcherSettings = mergeMode(remote, "watcher", updated.watcherSettings),
            gpsIntervalSec = readSensorInterval(remote, "gps_interval_sec", updated.gpsIntervalSec),
            batteryIntervalSec = readSensorInterval(remote, "battery_interval_sec", updated.batteryIntervalSec),
            temperatureIntervalSec = readSensorInterval(remote, "temperature_interval_sec", updated.temperatureIntervalSec),
            noiseIntervalSec = readSensorInterval(remote, "noise_interval_sec", updated.noiseIntervalSec),
            joltIntervalSec = readSensorInterval(remote, "jolt_interval_sec", updated.joltIntervalSec),
            networkTypeIntervalSec = readSensorInterval(remote, "network_type_interval_sec", updated.networkTypeIntervalSec),
            sim1SignalIntervalSec = readSensorInterval(remote, "sim1_signal_interval_sec", updated.sim1SignalIntervalSec),
            sim2SignalIntervalSec = readSensorInterval(remote, "sim2_signal_interval_sec", updated.sim2SignalIntervalSec),
            latencyIntervalSec = readSensorInterval(remote, "latency_interval_sec", updated.latencyIntervalSec),
        )

        return updated
    }

    private fun readSensorInterval(remote: JSONObject, key: String, current: Int): Int {
        if (!remote.has(key)) return current
        return SettingsStore.normalizeSensorIntervalSec(remote.optInt(key, current))
    }

    private fun mergeMode(remote: JSONObject, prefix: String, current: ModeSettings): ModeSettings {
        var mode = current
        if (remote.has("${prefix}_image_upload_policy")) {
            mode = mode.copy(
                imageUploadPolicy = ImageUploadPolicy.fromStoredValue(remote.getString("${prefix}_image_upload_policy")),
            )
        }
        return mode
    }

    private fun isValidBaseUrl(baseUrl: String): Boolean {
        val trimmed = baseUrl.trim()
        return trimmed.startsWith("http://") || trimmed.startsWith("https://")
    }
}
