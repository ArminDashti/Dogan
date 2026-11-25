package com.dogan

import org.junit.Assert.assertEquals
import org.junit.Test

class CabinNoiseLevelsTest {
    @Test
    fun mapsTypicalCabinRmsNearAlertThreshold() {
        val quiet = CabinNoiseLevels.rmsToDecibels(100.0)
        val loud = CabinNoiseLevels.rmsToDecibels(2500.0)
        assertEquals(true, quiet in 35.0..55.0)
        assertEquals(true, loud in 65.0..80.0)
    }
}