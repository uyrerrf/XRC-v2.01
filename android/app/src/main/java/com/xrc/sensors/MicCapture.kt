// ============================================================
// FILE: android/app/src/main/java/com/xrc/sensors/MicCapture.kt
// ============================================================
package com.xrc.sensors

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Environment
import android.util.Base64
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * MicCapture — records audio from the device microphone.
 *
 * Requires RECORD_AUDIO permission.
 * Captures raw PCM or AMR-WB encoded audio.
 */
class MicCapture(private val context: Context) {
    companion object {
        private const val TAG = "MicCapture"
        private const val SAMPLE_RATE = 16000
        private const val CHUNK_SIZE = 4096
    }

    private var isCapturing = false
    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null
    private var captureBuffer = mutableListOf<Byte>()

    /**
     * Start microphone capture.
     * @param durationSec Max recording duration (0 = unlimited)
     */
    fun startCapture(durationSec: Int = 30) {
        if (isCapturing) return
        if (!hasPermission()) return

        isCapturing = true
        captureBuffer.clear()

        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize.coerceAtLeast(CHUNK_SIZE)
        )

        audioRecord?.startRecording()

        captureThread = Thread {
            val buffer = ByteArray(CHUNK_SIZE)
            val startTime = System.currentTimeMillis()
            val maxDurationMs = if (durationSec > 0) durationSec * 1000L else Long.MAX_VALUE

            while (isCapturing && (System.currentTimeMillis() - startTime) < maxDurationMs) {
                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (bytesRead > 0) {
                    synchronized(captureBuffer) {
                        captureBuffer.addAll(buffer.take(bytesRead))
                    }
                }
            }
            stopCapture()
        }
        captureThread?.start()
    }

    /**
     * Stop capture and return base64-encoded audio data.
     */
    fun stopCapture(): String? {
        isCapturing = false
        captureThread?.join(3000)
        captureThread = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {}
        audioRecord = null

        synchronized(captureBuffer) {
            if (captureBuffer.isEmpty()) return null
            val audioData = captureBuffer.toByteArray()
            return Base64.encodeToString(audioData, Base64.NO_WRAP)
        }
    }

    /**
     * Save captured audio to file.
     */
    fun saveToFile(filename: String = "capture_${System.currentTimeMillis()}.pcm"): String? {
        synchronized(captureBuffer) {
            if (captureBuffer.isEmpty()) return null
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: return null
            val file = File(dir, filename)
            FileOutputStream(file).use { fos ->
                fos.write(captureBuffer.toByteArray())
            }
            return file.absolutePath
        }
    }

    fun isCapturing(): Boolean = isCapturing

    private fun hasPermission(): Boolean {
        return context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun release() {
        stopCapture()
    }
}
