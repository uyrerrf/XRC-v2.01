// ============================================================
// FILE: android/app/src/main/java/com/xrc/vnc/VNCController.kt
// ============================================================
package com.xrc.vnc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Base64
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import java.io.ByteArrayOutputStream

/**
 * VNCController — screen capture and remote control engine.
 *
 * Uses MediaProjection API to capture screen frames.
 * On Android 12+, requires user consent via intent.
 *
 * Features:
 * - Screen capture (single frame)
 * - Continuous streaming with configurable quality
 * - VNC frame encoding as JPEG base64
 * - Resolution scaling for bandwidth optimization
 */
class VNCController(private val context: Context) {
    companion object {
        private const val TAG = "VNCController"
        private const val VIRTUAL_DISPLAY_NAME = "xrc_vnc_display"
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureHandler: Handler? = null
    private var captureThread: HandlerThread? = null
    private var isCapturing = false
    private var currentQuality = 70
    private var scaledWidth = 720
    private var scaledHeight = 0
    private var onFrame: ((String) -> Unit)? = null

    private val displayMetrics: DisplayMetrics
        get() = context.resources.displayMetrics

    /**
     * Initialize with a MediaProjection instance.
     * Call this after user grants projection permission.
     */
    fun initialize(projection: MediaProjection) {
        mediaProjection = projection
        Log.i(TAG, "VNC controller initialized with MediaProjection")
    }

    /**
     * Start screen capture.
     * @param quality JPEG quality (1-100)
     */
    fun startScreenCapture(quality: Int = 70) {
        if (isCapturing) return
        if (mediaProjection == null) {
            Log.w(TAG, "MediaProjection not initialized")
            return
        }

        currentQuality = quality.coerceIn(1, 100)
        val metrics = displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        // Scale for bandwidth efficiency
        scaledWidth = minOf(width, 720)
        scaledHeight = (height * scaledWidth) / width

        // Start handler thread
        captureThread = HandlerThread("vnc-capture").apply { start() }
        captureHandler = Handler(captureThread!!.looper)

        // Create ImageReader
        imageReader = ImageReader.newInstance(
            width, height,
            PixelFormat.RGBA_8888,
            2
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                processFrame(image)
                image.close()
            }
        }, captureHandler!!)

        // Create virtual display
        virtualDisplay = mediaProjection!!.createVirtualDisplay(
            VIRTUAL_DISPLAY_NAME,
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null, null
        )

        isCapturing = true
        Log.i(TAG, "Screen capture started: ${width}x${height} @ ${density}dpi")
    }

    /**
     * Stop screen capture.
     */
    fun stopScreenCapture() {
        isCapturing = false
        try {
            virtualDisplay?.release()
            imageReader?.close()
            captureThread?.quitSafely()
        } catch (e: Exception) {
            Log.w(TAG, "Stop capture: ${e.message}")
        }
        virtualDisplay = null
        imageReader = null
        captureHandler = null
        captureThread = null
        Log.i(TAG, "Screen capture stopped")
    }

    /**
     * Get current frame as base64 JPEG.
     */
    fun getCurrentFrame(): String? {
        if (!isCapturing) return null
        var result: String? = null
        val latch = java.util.concurrent.CountDownLatch(1)

        captureHandler?.post {
            try {
                val reader = imageReader
                if (reader != null) {
                    val image = reader.acquireLatestImage()
                    if (image != null) {
                        result = imageToJpegBase64(image)
                        image.close()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Frame capture: ${e.message}")
            }
            latch.countDown()
        }

        try { latch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS) } catch (e: Exception) {}
        return result
    }

    /**
     * Start streaming frames to callback.
     */
    fun startStreaming(onFrameCallback: (String) -> Unit) {
        onFrame = onFrameCallback
        if (!isCapturing) {
            startScreenCapture(currentQuality)
        }
    }

    /**
     * Stop streaming.
     */
    fun stopStreaming() {
        onFrame = null
        stopScreenCapture()
    }

    /**
     * Set JPEG quality.
     */
    fun setQuality(quality: Int) {
        currentQuality = quality.coerceIn(1, 100)
    }

    /**
     * Check if capture is active.
     */
    fun isActive(): Boolean = isCapturing

    /**
     * Release all resources.
     */
    fun release() {
        stopScreenCapture()
        mediaProjection?.stop()
        mediaProjection = null
    }

    private fun processFrame(image: Image) {
        val jpegBase64 = imageToJpegBase64(image)
        if (jpegBase64 != null) {
            onFrame?.invoke(jpegBase64)
        }
    }

    private fun imageToJpegBase64(image: Image): String? {
        return try {
            val planes = image.planes
            if (planes.isEmpty()) return null

            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride

            val width = image.width
            val height = image.height

            // Convert RGBA_8888 to Bitmap
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(buffer)

            // Scale down if needed
            val scaled = if (width > scaledWidth) {
                Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
            } else bitmap

            // Compress to JPEG
            val stream = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, currentQuality, stream)
            val jpegBytes = stream.toByteArray()

            // Encode to base64
            Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.w(TAG, "Image to JPEG failed: ${e.message}")
            null
        }
    }

    /**
     * Get dimensions for VNC display.
     */
    fun getDisplayInfo(): Map<String, Any> {
        return mapOf(
            "width" to displayMetrics.widthPixels,
            "height" to displayMetrics.heightPixels,
            "density" to displayMetrics.densityDpi,
            "scaled_width" to scaledWidth,
            "scaled_height" to scaledHeight
        )
    }
}
