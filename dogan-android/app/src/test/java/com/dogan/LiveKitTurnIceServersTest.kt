package com.dogan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveKitTurnIceServersTest {
    @Test
    fun isDoganTurnUrl_matchesTurnsHost() {
        assertTrue(
            LiveKitTurnIceServers.isDoganTurnUrl(
                "turns:dogan-turn.xaigrok.ir:443?transport=tcp",
            ),
        )
        assertFalse(LiveKitTurnIceServers.isDoganTurnUrl("turn:other.example.com:5349"))
    }
}