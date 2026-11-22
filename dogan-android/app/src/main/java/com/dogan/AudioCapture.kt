package com.dogan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlin.math.sqrt

/** Samples microphone amplitude and feeds cabin noise archive. */
class AudioCapture(
    private val context: Context,
    private val onSpike: ((Double) -> Unit)? = null,
    private val cabinNoiseArchive: CabinNoiseArchive? = null,
) {
    private val releaseLock = Any()

    @Volatile
    private var audioRecord: AudioRecord? = null

    @Volatile
    private var running = false

    @Volatile
    private var starting = false

    @Volatile
    private var stopRequested = false

    @Volatile
    private var thread: Thread? = null

    @Volatile
    private var bufferSize = 0

    @Volatile
    var currentRms: Double = 0.0
        private set

    @Volatile
    var soundSensitivity: SensitivityLevel = SensitivityLevel.MEDIUM

    @Volatile
    var customSoundThreshold: Double = SettingsStore.DEFAULT_CUSTOM_SOUND_THRESHOLD

    fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    /** Non-blocking: recorder setup and read loop run on a dedicated thread. */
    fun start() {
        if (running || starting) return
        if (!hasRecordPermission()) {
            AppLogger.warn("Audio", "RECORD_AUDIO not granted; cabin noise sampling disabled")
            return
        }
        starting = true
        stopRequested = false
        thread = Thread({ captureLoop() }, "dogan-audio-capture").also {
            it.isDaemon = true
            it.start()
        }
    }

    private fun captureLoop() {
        try {
            val recorder = createRecorder() ?: return
            audioRecord = recorder
            recorder.startRecording()
            if (stopRequested) return
            running = true
            cabinNoiseArchive?.startNewSegment()
            AppLogger.info("Audio", "Sound monitoring started")
            readSamples(recorder)
        } catch (denied: SecurityException) {
            AppLogger.error("Audio", "Microphone permission denied: ${denied.message}")
        } catch (invalid: IllegalStateException) {
            AppLogger.error("Audio", "Microphone unavailable: ${invalid.message}")
        } catch (failure: Throwable) {
            AppLogger.error(
                "Audio",
                "Audio capture stopped: ${failure.javaClass.simpleName}: ${failure.message}",
            )
        } finally {
            running = false
            starting = false
            releaseRecorder()
            cabinNoiseArchive?.stop()
        }
    }

    private fun createRecorder(): AudioRecord? {
        val sampleRate = CabinNoiseArchive.SAMPLE_RATE
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer == AudioRecord.ERROR_BAD_VALUE || minBuffer == AudioRecord.ERROR) {
            AppLogger.error("Audio", "Microphone buffer size unavailable (telemetry noise will stay 0)")
            return null
        }
        bufferSize = minBuffer

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuffer * 2,
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            AppLogger.error(
                "Audio",
                "AudioRecord not initialized (missing RECORD_AUDIO or mic in use); cabin_noise_rms will stay 0",
            )
            recorder.release()
            return null
        }
        return recorder
    }

    private fun readSamples(recorder: AudioRecord) {
        val buffer = ShortArray(bufferSize)
        var lastSpikeAt = 0L
        while (running && !stopRequested) {
            val read = try {
                recorder.read(buffer, 0, buffer.size)
            } catch (_: Throwable) {
                break
            }
            if (read <= 0) continue
            val rms = computeRms(buffer, read)
            currentRms = rms
            cabinNoiseArchive?.writePcmSamples(buffer, read, rms)
            val now = System.currentTimeMillis()
            if (rms > loudThreshold() && now - lastSpikeAt > 5_000L) {
                lastSpikeAt = now
                onSpike?.invoke(rms)
                AppLogger.info("Audio", "Sound level spike detected (rms=%.2f)".format(rms))
            }
        }
    }

    fun stop() {
        val wasActive = running || starting
        stopRequested = true
        running = false
        starting = false
        thread?.interrupt()
        thread = null
        releaseRecorder()
        cabinNoiseArchive?.stop()
        currentRms = 0.0
        if (wasActive) AppLogger.info("Audio", "Sound monitoring stopped")
    }

    private fun releaseRecorder() {
        synchronized(releaseLock) {
            val recorder = audioRecord ?: return
            audioRecord = null
            try {
                recorder.stop()
            } catch (_: Throwable) {
            }
            try {
                recorder.release()
            } catch (_: Throwable) {
            }
        }
    }

    private fun computeRms(buffer: ShortArray, length: Int): Double {
        var sum = 0.0
        for (i in 0 until length) {
            val sample = buffer[i].toDouble()
            sum += sample * sample
        }
        return sqrt(sum / length)
    }

    private fun loudThreshold(): Double = when (soundSensitivity) {
        SensitivityLevel.LOW -> 4000.0
        SensitivityLevel.MEDIUM -> 2500.0
        SensitivityLevel.HIGH -> 1500.0
        SensitivityLevel.CUSTOM -> customSoundThreshold
    }
}
