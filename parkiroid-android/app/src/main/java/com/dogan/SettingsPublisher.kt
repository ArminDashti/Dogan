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

    suspend fun pushOperatingMode(context: Context, mode: OperatingMode) {
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
            "operating_mode" to settings.operatingMode.toStoredValue(),
            "active_camera" to settings.activeCamera.toStoredValue(),
            "telemetry_interval_sec" to settings.telemetryIntervalSec,
            "settings_sync_interval_sec" to settings.settingsSyncIntervalSec,
            "screen_on_interval_min" to settings.screenOnIntervalMin,
            "telemetry_retention_hours" to settings.telemetryRetentionHours,
            "jolt_sensitivity" to settings.joltSensitivity.toStoredValue(),
            "sound_sensitivity" to settings.soundSensitivity.toStoredValue(),
            "custom_jolt_scale" to settings.customJoltScale,
            "custom_sound_threshold" to settings.customSoundThreshold,
            "log_retention_days" to settings.logRetentionDays,
            "alert_duration" to settings.alertDuration.toStoredValue(),
            "copilot_alerts_enabled" to settings.copilotAlertsEnabled,
            "copilot_distance_control_enabled" to settings.copilotDistanceControlEnabled,
        )
        putModeKeys(keys, "watcher", settings.watcherSettings)
        putModeKeys(keys, "copilot", settings.copilotSettings)
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
