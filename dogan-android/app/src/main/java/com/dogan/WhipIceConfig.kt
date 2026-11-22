package com.dogan

import org.json.JSONArray
import org.webrtc.PeerConnection

object WhipIceConfig {
    fun rtcConfiguration(
        iceServers: JSONArray?,
        whipUrl: String,
        streamPort: Int,
    ): PeerConnection.RTCConfiguration {
        val servers = ArrayList<PeerConnection.IceServer>()
        if (iceServers != null) {
            for (index in 0 until iceServers.length()) {
                val item = iceServers.optJSONObject(index) ?: continue
                val urls = when (val raw = item.opt("urls")) {
                    is String -> listOf(raw)
                    is JSONArray -> (0 until raw.length()).mapNotNull { position ->
                        raw.optString(position).takeIf { it.isNotBlank() }
                    }
                    else -> emptyList()
                }
                val username = item.optString("username", "")
                val credential = item.optString("credential", "")
                val validUrls = urls.filter { it.isNotBlank() }
                if (validUrls.isEmpty()) continue
                val builder = PeerConnection.IceServer.builder(validUrls)
                if (username.isNotBlank()) builder.setUsername(username)
                if (credential.isNotBlank()) builder.setPassword(credential)
                servers.add(builder.createIceServer())
            }
        }
        val normalized = whipUrl.trim().lowercase()
        val productionPath = streamPort == 443 ||
            normalized.startsWith("https://") ||
            normalized.startsWith("wss://") ||
            normalized.contains(":443")
        // Relay-only ICE on the public 443 path needs a TURN server from the API.
        val hasTurn = servers.any { server ->
            server.urls.any { it.startsWith("turn:") || it.startsWith("turns:") }
        }
        return PeerConnection.RTCConfiguration(servers).apply {
            iceTransportsType = if (productionPath && hasTurn) {
                PeerConnection.IceTransportsType.RELAY
            } else {
                PeerConnection.IceTransportsType.ALL
            }
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
    }
}
