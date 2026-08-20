// ============================================================
// FILE: android/app/src/main/java/com/xrc/sensors/CameraCapture.kt
// ============================================================
package com.xrc.sensors

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.YuvImage
import android.hardware.camera2.*
import android.os.Environment
import android.util.Base64
import android.util.Log
import android.view.Surface
import android.view.SurfaceView
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * CameraCapture — captures still photos and streams frames.
 *
 * Requires CAMERA permission.
 * Uses Camera2 API for maximum compatibility (Android 5+).
 */
class CameraCapture(private val context: Context) {
    companion object {
        private const val TAG = "CameraCapture"
        private const val TIMEOUT_MS = 5000L
    }

    private var cameraManager: CameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraDevice: CameraDevice? = null
    private var cameraSession: CameraCaptureSession? = null
    private var isStreaming = false
    private var onFrameCallback: ((String) -> Unit)? = null

    /**
     * Capture a single still image (front camera preferred).
     * Returns base64-encoded JPEG.
     */
    fun captureStill(cameraId: String? = null): String? {
        if (!hasPermission()) return null

        return try {
            val id = cameraId ?: getFrontCameraId() ?: getBackCameraId() ?: return null
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val configs = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return null
            val outputSizes = configs.getOutputSizes(ImageFormat.JPEG) ?: return null
            val size = outputSizes.firstOrNull() ?: return null

            val semaphore = Semaphore(0)
            var result: ByteArray? = null

            val stateCallback = object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    val surfaceTexture = android.graphics.SurfaceTexture(10)
                    surfaceTexture.setDefaultBufferSize(size.width, size.height)
                    val surface = Surface(surfaceTexture)

                    val captureRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                    captureRequest.addTarget(surface)

                    val outputConfiguration = OutputConfiguration(surface)
                    val sessionConfig = SessionConfiguration(
                        SessionConfiguration.SESSION_REGULAR,
                        listOf(outputConfiguration),
                        context.mainExecutor,
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                cameraSession = session
                                session.capture(captureRequest.build(),
                                    object : CameraCaptureSession.CaptureCallback() {
                                        override fun onCaptureCompleted(
                                            session: CameraCaptureSession,
                                            request: CaptureRequest,
                                            result: TotalCaptureResult
                                        ) {
                                            // Read pixel buffer from surface
                                            val buffer = surfaceTexture.detachNextFrame() ?: return@let
                                            val bitmap = surfaceTextureToBitmap(surfaceTexture, size.width, size.height)
                                            val jpegBytes = bitmapToJpeg(bitmap)
                                            result = jpegBytes
                                            semaphore.release()
                                        }
                                    }, null)
                            }
                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                semaphore.release()
                            }
                        }
                    )
                    sessionConfig.setSessionParameters(captureRequest.build())
                    camera.createCaptureSession(sessionConfig)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    semaphore.release()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    semaphore.release()
                }
            }

            cameraManager.openCamera(id, stateCallback, null)
            semaphore.tryAcquire(TIMEOUT_MS, TimeUnit.MILLISECONDS)

            cameraDevice?.close()
            cameraDevice = null

            result?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
        } catch (e: Exception) {
            Log.w(TAG, "Still capture failed: ${e.message}")
            null
        }
    }

    /**
     * Start streaming camera frames as base64 JPEGs.
     */
    fun startStreaming(onFrame: (String) -> Unit) {
        if (isStreaming) return
        if (!hasPermission()) return

        onFrameCallback = onFrame
        isStreaming = true

        Thread {
            try {
                val id = getFrontCameraId() ?: getBackCameraId() ?: return@Thread
                while (isStreaming) {
                    val frame = captureStill(id) ?: continue
                    onFrameCallback?.invoke(frame)
                    Thread.sleep(200) // 5 FPS
                }
            } catch (e: Exception) {
                Log.w(TAG, "Streaming stopped: ${e.message}")
                isStreaming = false
            }
        }.start()
    }

    fun stopStreaming() {
        isStreaming = false
        onFrameCallback = null
        cameraDevice?.close()
        cameraDevice = null
    }

    /**
     * Save a captured frame to file.
     */
    fun saveToFile(base64Jpeg: String, filename: String? = null): String? {
        return try {
            val data = Base64.decode(base64Jpeg, Base64.DEFAULT)
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return null
            val file = File(dir, filename ?: "capture_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { it.write(data) }
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun getFrontCameraId(): String? {
        for (id in cameraManager.cameraIdList) {
            val chars = cameraManager.getCameraCharacteristics(id)
            val facing = chars.get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_FRONT) return id
        }
        return null
    }

    private fun getBackCameraId(): String? {
        for (id in cameraManager.cameraIdList) {
            val chars = cameraManager.getCameraCharacteristics(id)
            val facing = chars.get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_BACK) return id
        }
        return null
    }

    private fun surfaceTextureToBitmap(st: android.graphics.SurfaceTexture, width: Int, height: Int): Bitmap {
        st.getTransformMatrix(FloatArray(16))
        val buffer = android.opengl.GLES20.glReadPixels(0, 0, width, height,
            android.opengl.GLES20.GL_RGBA, android.opengl.GLES20.GL_UNSIGNED_BYTE, java.nio.ByteBuffer.allocate(width * height * 4))
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }

    private fun bitmapToJpeg(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
        return stream.toByteArray()
    }

    private fun hasPermission(): Boolean {
        return context.checkSelfPermission(Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun release() {
        stopStreaming()
    }
}
