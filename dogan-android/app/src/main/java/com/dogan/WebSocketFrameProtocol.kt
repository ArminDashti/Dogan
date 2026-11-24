package com.dogan

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

internal object WebSocketFrameProtocol {
    const val VIDEO_FRAME: Byte = 1
    const val AUDIO_FRAME: Byte = 2
    const val HEADER_SIZE = 9

    fun encodeFrame(type: Byte, timestampUs: Long, payload: ByteArray): ByteArray {
        require(type == VIDEO_FRAME || type == AUDIO_FRAME)
        require(timestampUs >= 0)
        return ByteBuffer.allocate(HEADER_SIZE + payload.size)
            .order(ByteOrder.BIG_ENDIAN)
            .put(type)
            .putLong(timestampUs)
            .put(payload)
            .array()
    }

    fun encodeConfiguration(
        videoEnabled: Boolean,
        audioEnabled: Boolean,
        sps: ByteArray? = null,
        pps: ByteArray? = null,
    ): String {
        val videoConfiguration = if (videoEnabled) {
            val encodedSps = Base64.getEncoder().encodeToString(requireNotNull(sps))
            val encodedPps = Base64.getEncoder().encodeToString(requireNotNull(pps))
            ",\"sps\":\"$encodedSps\",\"pps\":\"$encodedPps\""
        } else {
            ""
        }
        return """{"type":"config","version":1,"video_enabled":$videoEnabled,"audio_enabled":$audioEnabled$videoConfiguration,"sample_rate":44100,"channels":1}"""
    }

    fun copyBuffer(buffer: ByteBuffer, offset: Int, size: Int): ByteArray {
        require(offset >= 0 && size > 0 && offset + size <= buffer.limit())
        return buffer.duplicate().apply {
            position(offset)
            limit(offset + size)
        }.let { source -> ByteArray(size).also(source::get) }
    }

    fun copyRemaining(buffer: ByteBuffer): ByteArray {
        return buffer.duplicate().let { source ->
            ByteArray(source.remaining()).also(source::get)
        }
    }
}
