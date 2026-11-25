package com.dogan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** Downscales and recompresses still JPEGs before telemetry upload. */
object FrameUploadEncoder {
    const val TELEMETRY_MAX_LONG_EDGE = 640

    fun prepareUploadBytes(file: File, quality: ImageQuality): ByteArray {
        if (!file.exists() || file.length() == 0L) {
            return ByteArray(0)
        }
        val targetLongEdge = quality.maxDimension?.coerceAtMost(TELEMETRY_MAX_LONG_EDGE)
            ?: TELEMETRY_MAX_LONG_EDGE
        val decoded = BitmapFactory.decodeFile(file.absolutePath) ?: return file.readBytes()
        val longEdge = max(decoded.width, decoded.height)
        val scaled = if (longEdge <= targetLongEdge) {
            decoded
        } else {
            val scale = targetLongEdge.toFloat() / longEdge.toFloat()
            val width = max(1, (decoded.width * scale).roundToInt())
            val height = max(1, (decoded.height * scale).roundToInt())
            Bitmap.createScaledBitmap(decoded, width, height, true).also {
                if (it !== decoded) {
                    decoded.recycle()
                }
            }
        }
        val jpegQuality = quality.jpegQuality.coerceIn(50, 85)
        val output = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality, output)
        if (scaled !== decoded) {
            scaled.recycle()
        } else {
            decoded.recycle()
        }
        return output.toByteArray()
    }
}