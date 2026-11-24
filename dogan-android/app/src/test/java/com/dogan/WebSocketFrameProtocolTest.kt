package com.dogan

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class WebSocketFrameProtocolTest {
    @Test
    fun encodesBigEndianTimestampAndPayload() {
        val frame = WebSocketFrameProtocol.encodeFrame(
            WebSocketFrameProtocol.VIDEO_FRAME,
            123456L,
            byteArrayOf(0x65, 0x01),
        )

        val decoded = ByteBuffer.wrap(frame).order(ByteOrder.BIG_ENDIAN)
        assertEquals(WebSocketFrameProtocol.VIDEO_FRAME, decoded.get())
        assertEquals(123456L, decoded.long)
        val payload = ByteArray(decoded.remaining())
        decoded.get(payload)
        assertArrayEquals(byteArrayOf(0x65, 0x01), payload)
    }

    @Test
    fun configurationMatchesApiBridgeWireFormat() {
        val configuration = WebSocketFrameProtocol.encodeConfiguration(
            videoEnabled = true,
            audioEnabled = true,
            sps = byteArrayOf(0x67, 0x64),
            pps = byteArrayOf(0x68, 0x01),
        )

        assertEquals(
            """{"type":"config","version":1,"video_enabled":true,"audio_enabled":true,"sps":"Z2Q=","pps":"aAE=","sample_rate":44100,"channels":1}""",
            configuration,
        )
    }
}
