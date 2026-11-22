package com.dogan

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.time.Instant

/** Collects the unified telemetry snapshot. */
class TelemetryCollector(
    private val context: Context,
    private val locationTracker: LocationTracker,
    private val ambientLightSensor: AmbientLightSensor,
    private val audioCapture: AudioCapture,
    private val vehicleMotionDetector: VehicleMotionDetector,
    private val deviceId: String,
    private val resourceMonitor: DeviceResourceMonitor,
) {
    private var cachedGps = JSONObject().put("latitude", 0.0).put("longitude", 0.0)
    private var cachedGpsQuality = ""
    private var cachedSpeedKmh = 0.0
    private var cachedBatteryPct = 0
    private var cachedBatteryTemp = 0.0
    private var cachedNoise = 0.0
    private var cachedNetworkType = ""
    private var cachedSignalDbm = -1
    private var cachedLatencyMs = -1L
    private var gpsAt = 0L
    private var batteryAt = 0L
    private var temperatureAt = 0L
    private var noiseAt = 0L
    private var joltAt = 0L
    private var cachedJolt = 0.0
    private var networkTypeAt = 0L
    private var sim1At = 0L
    private var latencyAt = 0L

    fun collectSnapshot(
        rearFrameFile: File?,
        frontFrameFile: File?,
        serverLatencyMs: Long,
        includeFrames: Boolean,
        settings: AppSettings,
        nowMs: Long = System.currentTimeMillis(),
    ): JSONObject {
        if (due(gpsAt, nowMs, settings.gpsIntervalSec)) {
            val loc = locationTracker.lastLocation
            cachedGps = JSONObject()
            if (loc != null) {
                cachedGps.put("latitude", loc.latitude)
                cachedGps.put("longitude", loc.longitude)
            } else {
                cachedGps.put("latitude", 0.0)
                cachedGps.put("longitude", 0.0)
            }
            cachedGpsQuality = locationTracker.gpsSignalQuality()
            cachedSpeedKmh = locationTracker.speedKmh().toDouble()
            gpsAt = nowMs
        }
        if (due(batteryAt, nowMs, settings.batteryIntervalSec)) {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            cachedBatteryPct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            batteryAt = nowMs
        }
        if (due(temperatureAt, nowMs, settings.temperatureIntervalSec)) {
            val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            cachedBatteryTemp = (batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)?.div(10f) ?: 0f).toDouble()
            temperatureAt = nowMs
        }
        if (due(noiseAt, nowMs, settings.noiseIntervalSec)) {
            cachedNoise = audioCapture.currentRms
            noiseAt = nowMs
        }
        if (due(joltAt, nowMs, settings.joltIntervalSec)) {
            cachedJolt = vehicleMotionDetector.lastMagnitudeMps2.toDouble()
            joltAt = nowMs
        }
        if (due(networkTypeAt, nowMs, settings.networkTypeIntervalSec)) {
            cachedNetworkType = NetworkInfoCollector.getNetworkType(context)
            networkTypeAt = nowMs
        }
        if (due(sim1At, nowMs, settings.sim1SignalIntervalSec)) {
            cachedSignalDbm = NetworkInfoCollector.getSignalStrengthDbm(context)
            sim1At = nowMs
        }
        if (due(latencyAt, nowMs, settings.latencyIntervalSec)) {
            cachedLatencyMs = serverLatencyMs
            latencyAt = nowMs
        }

        return JSONObject()
            .put("device_id", deviceId)
            .put("recorded_at", Instant.now().toString())
            .put("gps_location", cachedGps)
            .put("gps_signal_quality", cachedGpsQuality)
            .put("speed_kmh", cachedSpeedKmh)
            .put("network_signal_strength_dbm", cachedSignalDbm)
            .put("network_type", cachedNetworkType)
            .put("cabin_noise_rms", cachedNoise)
            .put("jolt", cachedJolt)
            .put("battery_temperature_celsius", cachedBatteryTemp)
            .put("battery_percentage", cachedBatteryPct)
            .put("rear_camera_frame_base64", if (includeFrames) encodeFile(rearFrameFile) else "")
            .put("front_camera_frame_base64", if (includeFrames) encodeFile(frontFrameFile) else "")
            .put("ambient_light_lux", ambientLightSensor.lux.toDouble())
            .put("server_latency_ms", cachedLatencyMs)
            .put("device_ip_address", NetworkInfoCollector.getLocalIpAddress())
            .put("cpu_usage_percent", resourceMonitor.cpuUsagePercent())
            .put("ram_usage_percent", resourceMonitor.ramUsagePercent())
    }

    private fun due(lastAt: Long, nowMs: Long, intervalSec: Int): Boolean {
        if (lastAt == 0L) return true
        return nowMs - lastAt >= intervalSec.coerceAtLeast(1) * 1000L
    }

    private fun encodeFile(file: File?): String {
        if (file == null || !file.exists()) return ""
        return Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
    }
}
