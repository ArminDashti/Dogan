package com.dogan

import android.hardware.SensorManager

/** Accelerometer / linear-acceleration sampling rate for bump detection. */
enum class MotionSamplingRate(val displayName: String) {
    HIGH("High"),
    NORMAL("Normal"),
    LOW("Low");

    fun toStoredValue(): String = name.lowercase()

    fun toSensorDelay(): Int = when (this) {
        HIGH -> SensorManager.SENSOR_DELAY_GAME
        NORMAL -> SensorManager.SENSOR_DELAY_NORMAL
        LOW -> SensorManager.SENSOR_DELAY_UI
    }

    companion object {
        val all = entries.toList()

        fun fromStoredValue(value: String?): MotionSamplingRate =
            entries.firstOrNull {
                it.name.equals(value, ignoreCase = true) || it.toStoredValue() == value?.lowercase()
            } ?: HIGH
    }
}
