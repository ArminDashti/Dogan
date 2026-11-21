package com.dogan

import livekit.LivekitRtc
import livekit.org.webrtc.PeerConnection

/**
 * Converts LiveKit signal join [LivekitRtc.ICEServer] entries to WebRTC [PeerConnection.IceServer] instances.
 *
 * SDK 2.27.x uses TLS_CERT_POLICY_SECURE by default; liblkjingle's trust store lacks ISRG roots, so
 * turns://dogan-turn.xaigrok.ir:443 (Let's Encrypt) fails. We relax verification only for that host.
 */
object LiveKitTurnIceServers {
    const val DOGAN_TURN_HOST = "dogan-turn.xaigrok.ir"

    fun fromJoinResponse(join: LivekitRtc.JoinResponse): List<PeerConnection.IceServer> {
        return join.iceServersList.map { fromProto(it) }
    }

    fun fromProto(server: LivekitRtc.ICEServer): PeerConnection.IceServer {
        val builder = PeerConnection.IceServer.builder(server.urlsList)
            .setUsername(server.username ?: "")
            .setPassword(server.credential ?: "")
        if (server.urlsList.any { isDoganTurnUrl(it) }) {
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