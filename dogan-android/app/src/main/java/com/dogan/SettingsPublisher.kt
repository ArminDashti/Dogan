package com.dogan

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Saves settings locally and pushes changed keys to the server when connected
 * via `PUT /api/v1/settings`.
 */
object SettingsPublisher {
    suspend fun saveAndPush(
        context: Context,
        mutate: (AppSettings) -> AppSettings,
        changedKeys: Map<String, Any?>,
    ) {
        val appContext = context.applicationContext
        val store = SettingsStore(appContext)
        val updated = mutate(store.settingsFlow.first())
        store.save(updated)
        SessionCredentials.updateFrom(updated)
        if (!ServerConnectionManager.isConnected() || changedKeys.isEmpty()) return
        val apiClient = DoganApiClient(deviceId = DeviceIdentity.resolveDeviceId(appContext))
        withContext(Dispatchers.IO) {
            for ((key, value) in changedKeys) {
                val ok = apiClient.putSetting(updated.serverBaseUrl, key, value)
                if (!ok) {
                    AppLogger.warn("Settings", "Failed to push setting $key")
                }
            }
        }
    }

    suspend fun saveFullAndPushAll(context: Context, settings: AppSettings) {
        val appContext = context.applicationContext
        val store = SettingsStore(appContext)
        store.save(settings)
        SessionCredentials.updateFrom(settings)
        if (!ServerConnectionManager.isConnected()) return
        val apiClient = DoganApiClient(deviceId = DeviceIdentity.resolveDeviceId(appContext))
        withContext(Dispatchers.IO) {
            pushSnapshot(apiClient, settings)
        }
    }

    suspend fun saveOperatingMode(context: Context, mode: OperatingMode) {
        saveAndPush(
            context,
            mutate = { it.copy(operatingMode = mode) },
            changedKeys = mapOf("operating_mode" to mode.toStoredValue()),
        )
    }

    private fun pushSnapshot(apiClient: DoganApiClient, settings: AppSettings) {
        val keys = linkedMapOf<String, Any?>(
            "api_endpoint" to settings.apiEndpoint,
            "api_port" to settings.apiPort,
            "stream_endpoint" to settings.streamEndpoint,
            "stream_port" to settings.streamPort,
            "active_camera" to settings.activeCamera.toStoredValue(),
            "telemetry_interval_sec" to settings.telemetryIntervalSec,
            "gps_interval_sec" to settings.gpsIntervalSec,
            "battery_interval_sec" to settings.batteryIntervalSec,
            "temperature_interval_sec" to settings.temperatureIntervalSec,
            "noise_interval_sec" to settings.noiseIntervalSec,
            "jolt_interval_sec" to settings.joltIntervalSec,
            "network_type_interval_sec" to settings.networkTypeIntervalSec,
            "sim1_signal_interval_sec" to settings.sim1SignalIntervalSec,
            "sim2_signal_interval_sec" to settings.sim2SignalIntervalSec,
            "latency_interval_sec" to settings.latencyIntervalSec,
            "upload_interval_sec" to settings.uploadIntervalSec,
            "settings_sync_interval_sec" to settings.settingsSyncIntervalSec,
            "screen_on_interval_min" to settings.screenOnIntervalMin,
            "telemetry_retention_hours" to settings.telemetryRetentionHours,
            "jolt_sensitivity" to settings.joltSensitivity.toStoredValue(),
            "sound_sensitivity" to settings.soundSensitivity.toStoredValue(),
            "custom_jolt_scale" to settings.customJoltScale,
            "custom_sound_threshold" to settings.customSoundThreshold,
            "log_retention_days" to settings.logRetentionDays,
            "sound_monitoring_enabled" to settings.soundMonitoringEnabled,
            "motion_sampling_rate" to settings.motionSamplingRate.toStoredValue(),
            "ambient_light_enabled" to settings.ambientLightEnabled,
            "watcher_gps_accuracy" to settings.watcherGpsAccuracy.toStoredValue(),
        )
        putModeKeys(keys, "watcher", settings.watcherSettings)
        for ((key, value) in keys) {
            if (!apiClient.putSetting(settings.serverBaseUrl, key, value)) {
                AppLogger.warn("Settings", "Failed to push setting $key")
            }
        }
    }

    private fun putModeKeys(keys: MutableMap<String, Any?>, prefix: String, mode: ModeSettings) {
        keys["${prefix}_image_upload_policy"] = mode.imageUploadPolicy.toStoredValue()
    }
}
