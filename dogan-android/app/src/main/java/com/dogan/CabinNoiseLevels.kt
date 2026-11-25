package com.dogan

import kotlin.math.log10

/** Converts microphone PCM RMS to a decibel-scale value for server metrics charts. */
object CabinNoiseLevels {
    private const val PCM_FULL_SCALE = 32768.0
    private const val SPL_OFFSET_DB = 94.0

    fun rmsToDecibels(rms: Double): Double {
        if (rms <= 0.0) {
            return 0.0
        }
        return 20.0 * log10(rms / PCM_FULL_SCALE) + SPL_OFFSET_DB
    }
}