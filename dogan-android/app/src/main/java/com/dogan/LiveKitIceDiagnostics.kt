package com.dogan

import livekit.org.webrtc.PeerConnection

/** Logs ICE URL hosts from join (no credentials). */
object LiveKitIceDiagnostics {
    private val doganTurnHost = LiveKitTurnIceServers.DOGAN_TURN_HOST
    private val urlHosts = linkedSetOf<String>()

    fun reset() {
        urlHosts.clear()
    }

    fun noteJoinIceUrls(urls: List<String>) {
        urls.forEach { url ->
            urlHosts.add(url.substringBefore('?').substringBefore('/'))
        }
    }

    fun logJoinIceSummary() {
        val doganTurnUrls = urlHosts.count { it.contains(doganTurnHost, ignoreCase = true) }
        AppLogger.info(
            "LiveKit",
            "Join ICE url_hosts=${urlHosts.size} dogan-turn_host_matches=$doganTurnUrls",
        )
    }

    fun logConfiguredIceServers(iceServers: List<PeerConnection.IceServer>) {
        val doganTurnEntries = iceServers.count { server ->
            server.urls.any { LiveKitTurnIceServers.isDoganTurnUrl(it) }
        }
        val insecureTls = iceServers.count { server ->
            server.urls.any { LiveKitTurnIceServers.isDoganTurnUrl(it) } &&
                server.tlsCertPolicy == PeerConnection.TlsCertPolicy.TLS_CERT_POLICY_INSECURE_NO_CHECK
        }
        AppLogger.info(
            "LiveKit",
            "ICE servers=$iceServers.size dogan-turn=$doganTurnEntries tls_insecure=$insecureTls hosts=${urlHosts.sorted()}",
        )
    }

    fun logIceLogLine(message: String) {
        val lowered = message.lowercase()
        when {
            lowered.contains("selected candidate pair") ||
                lowered.contains("candidate pair selected") ->
                AppLogger.info("LiveKit", "ICE selected: $message")

            lowered.contains("failed") && lowered.contains("candidate") ->
                AppLogger.warn("LiveKit", "ICE candidate failed: $message")

            lowered.contains("check candidate pair") ||
                lowered.contains("connection check") ->
                AppLogger.info("LiveKit", "ICE check: $message")
        }
    }
}