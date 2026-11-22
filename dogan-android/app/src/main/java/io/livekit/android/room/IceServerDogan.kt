package io.livekit.android.room

import com.dogan.LiveKitIceDiagnostics
import com.dogan.LiveKitTurnIceServers
import livekit.LivekitRtc
import livekit.org.webrtc.PeerConnection

/**
 * Shadow of SDK [toWebrtc] (same JVM signature) so join ICE uses dogan-turn TLS policy on single connect.
 */
fun LivekitRtc.ICEServer.toWebrtc(): PeerConnection.IceServer {
    LiveKitIceDiagnostics.noteJoinIceUrls(urlsList)
    return LiveKitTurnIceServers.fromProto(this)
}