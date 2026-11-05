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

        if (remote.has("operating_mode")) {
            updated = updated.copy(operatingMode = OperatingMode.fromStoredValue(remote.getString("operating_mode")))
        }
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
        if (remote.has("copilot_distance_control_enabled")) {
            updated = updated.copy(copilotDistanceControlEnabled = remote.getBoolean("copilot_distance_control_enabled"))
        }
        if (remote.has("copilot_alerts_enabled")) {
            updated = updated.copy(copilotAlertsEnabled = remote.getBoolean("copilot_alerts_enabled"))
        }

        updated = updated.copy(
            watcherSettings = mergeMode(remote, "watcher", updated.watcherSettings),
            spotterSettings = mergeMode(remote, "spotter", updated.spotterSettings),
            copilotSettings = mergeMode(remote, "copilot", updated.copilotSettings),
        )

        return updated
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
