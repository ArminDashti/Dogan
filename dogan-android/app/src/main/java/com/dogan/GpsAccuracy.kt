package com.dogan

import com.google.android.gms.location.Priority

/** Fused location accuracy vs battery trade-off. */
enum class GpsAccuracy(val displayName: String) {
    HIGH("High"),
    BALANCED("Balanced");

    fun toStoredValue(): String = name.lowercase()

    fun toPriority(): Int = when (this) {
        HIGH -> Priority.PRIORITY_HIGH_ACCURACY
        BALANCED -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
    }

    companion object {
        val all = entries.toList()

        fun fromStoredValue(value: String?): GpsAccuracy =
            entries.firstOrNull {
                it.name.equals(value, ignoreCase = true) || it.toStoredValue() == value?.lowercase()
            } ?: BALANCED

        fun fromStoredValue(value: String?, default: GpsAccuracy): GpsAccuracy =
            entries.firstOrNull {
                it.name.equals(value, ignoreCase = true) || it.toStoredValue() == value?.lowercase()
            } ?: default
    }
}
