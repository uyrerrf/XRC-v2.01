package com.xrc.sensors

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Environment
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * CameraCapture — captures still photos using Camera2 API.
 *
 * Uses ImageReader for reliable JPEG capture.
 */
class CameraCapture(private val context: Context) {
    companion object {
        private const val TAG = "CameraCapture"
        private const val TIMEOUT_MS = 5000L
    }

    private val cameraManager: CameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraDevice: CameraDevice? = null
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

            val semaphore = Semaphore(0)
            var result: ByteArray? = null

            val stateCallback = object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    captureStillImage(camera, id, semaphore) { bytes ->
                        result = bytes
                    }
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

    private fun captureStillImage(
        camera: CameraDevice,
        cameraId: String,
        semaphore: Semaphore,
        onResult: (ByteArray) -> Unit
    ) {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val configs = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: run {
            semaphore.release()
            return
        }
        val outputSizes = configs.getOutputSizes(ImageFormat.JPEG) ?: run {
            semaphore.release()
            return
        }
        val size = outputSizes.firstOrNull() ?: run {
            semaphore.release()
            return
        }

        val reader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 1)
        reader.setOnImageAvailableListener({ reader ->
            val image: Image? = reader.acquireLatestImage()
            if (image != null) {
                val buffer: ByteBuffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                onResult(bytes)
                image.close()
            }
            semaphore.release()
        }, null)

        val outputSurface = reader.surface
        val captureRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        captureRequest.addTarget(outputSurface)

        try {
            camera.createCaptureSession(
                listOf(outputSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        session.capture(
                            captureRequest.build(),
                            object : CameraCaptureSession.CaptureCallback() {},
                            null
                        )
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Capture session config failed")
                        semaphore.release()
                    }
                },
                null
            )
        } catch (e: Exception) {
            Log.e(TAG, "createCaptureSession failed: ${e.message}")
            semaphore.release()
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

    private fun hasPermission(): Boolean {
        return context.checkSelfPermission(Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun release() {
        stopStreaming()
    }
}
