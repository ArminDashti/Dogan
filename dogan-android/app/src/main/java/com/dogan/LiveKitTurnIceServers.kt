package com.dogan

import livekit.org.webrtc.PeerConnection
import org.json.JSONArray

/**
 * Builds explicit WebRTC ICE servers from the API `ice_servers` list.
 *
 * The coturn edge at [DOGAN_TURN_HOST] terminates TLS with a Let's Encrypt certificate that the
 * SDK's bundled libwebrtc trust store rejects, so certificate checks are relaxed for that host only.
 */
object LiveKitTurnIceServers {
    const val DOGAN_TURN_HOST = "dogan-turn.xaigrok.ir"

    fun fromJsonArray(array: JSONArray?): List<PeerConnection.IceServer> {
        if (array == null) return emptyList()
        val servers = ArrayList<PeerConnection.IceServer>(array.length())
        for (index in 0 until array.length()) {
            val entry = array.optJSONObject(index) ?: continue
            val urlArray = entry.optJSONArray("urls") ?: continue
            val urls = ArrayList<String>(urlArray.length())
            for (urlIndex in 0 until urlArray.length()) {
                val url = urlArray.optString(urlIndex).trim()
                if (url.isNotEmpty()) urls.add(url)
            }
            if (urls.isEmpty()) continue
            servers.add(build(urls, entry.optString("username"), entry.optString("credential")))
        }
        return servers
    }

    fun build(urls: List<String>, username: String, credential: String): PeerConnection.IceServer {
        val builder = PeerConnection.IceServer.builder(urls)
        if (username.isNotEmpty()) builder.setUsername(username)
        if (credential.isNotEmpty()) builder.setPassword(credential)
        if (urls.any { isDoganTurnUrl(it) }) {
            builder.setTlsCertPolicy(
                PeerConnection.TlsCertPolicy.TLS_CERT_POLICY_INSECURE_NO_CHECK,
            )
        }
        return builder.createIceServer()
    }

    internal fun isDoganTurnUrl(url: String): Boolean {
        return url.contains(DOGAN_TURN_HOST, ignoreCase = true)
    }
}
