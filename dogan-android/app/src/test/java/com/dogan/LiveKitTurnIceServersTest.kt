package com.dogan

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import livekit.org.webrtc.PeerConnection

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

    @Test
    fun fromJsonArray_buildsTurnServerWithCredentials() {
        val array = JSONArray()
            .put(
                JSONObject()
                    .put("urls", JSONArray().put("turns:dogan-turn.xaigrok.ir:443?transport=tcp"))
                    .put("username", "1893456000:dogan")
                    .put("credential", "abc123"),
            )
        val servers = LiveKitTurnIceServers.fromJsonArray(array)
        assertEquals(1, servers.size)
        assertEquals("1893456000:dogan", servers[0].username)
        assertEquals("abc123", servers[0].password)
        assertEquals(
            PeerConnection.TlsCertPolicy.TLS_CERT_POLICY_INSECURE_NO_CHECK,
            servers[0].tlsCertPolicy,
        )
    }

    @Test
    fun fromJsonArray_keepsSecureTlsForOtherHosts() {
        val array = JSONArray()
            .put(
                JSONObject()
                    .put("urls", JSONArray().put("turn:turn.example.com:5349?transport=tcp"))
                    .put("username", "user")
                    .put("credential", "secret"),
            )
        val servers = LiveKitTurnIceServers.fromJsonArray(array)
        assertEquals(1, servers.size)
        assertEquals(
            PeerConnection.TlsCertPolicy.TLS_CERT_POLICY_SECURE,
            servers[0].tlsCertPolicy,
        )
    }

    @Test
    fun fromJsonArray_emptyArrayYieldsNoServers() {
        assertTrue(LiveKitTurnIceServers.fromJsonArray(null).isEmpty())
        assertTrue(LiveKitTurnIceServers.fromJsonArray(JSONArray()).isEmpty())
    }
}
