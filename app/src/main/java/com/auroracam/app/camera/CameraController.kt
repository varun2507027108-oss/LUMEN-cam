package com.auroracam.app.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.util.Log
import android.util.Size
import androidx.lifecycle.LifecycleOwner
import com.auroracam.app.camera.burst.RawYuvFrame
import com.auroracam.app.capture.CaptureSaver

class CameraController(
    private val context: Context,
    @Suppress("UNUSED_PARAMETER") private val lifecycleOwner: LifecycleOwner
) {
    companion object {
        private const val TAG = "CameraController"
        val PREFERRED_PREVIEW_SIZE = Size(1600, 1200)
        val PREFERRED_PREVIEW_HD_SIZE = Size(1920, 1440)
        val PREFERRED_CAPTURE_SIZE = Size(3200, 2400)
    }

    private val captureEngine = Camera2CaptureEngine(context)
    private var surfaceTexture: SurfaceTexture? = null

    val lastTelemetry: CaptureSaver.Telemetry
        get() = captureEngine.lastTelemetry

    val resolvedCaptureSize: Size
        get() = captureEngine.resolvedCaptureSize

    fun onSurfaceTextureReady(st: SurfaceTexture) {
        Log.i(TAG, "onSurfaceTextureReady: new SurfaceTexture received")
        surfaceTexture = st
        rebindCamera()
    }

    fun startCamera() {
        Log.i(TAG, "startCamera requested")
        rebindCamera()
    }

    fun onPause() {
        Log.i(TAG, "onPause: closing camera and invalidating stale SurfaceTexture")
        captureEngine.closeCamera()
        surfaceTexture = null // Invalidate — GLSurfaceView/Renderer will hand us a fresh live one on resume
    }

    fun onResume() {
        Log.i(TAG, "onResume: waiting for GLSurfaceView/Renderer to hand back a live SurfaceTexture")
        // Do NOT call rebindCamera() here with a possibly-stale/destroyed surface.
        // onSurfaceTextureReady() is the single authoritative trigger once the GL context is live.
    }

    @Synchronized
    private fun rebindCamera() {
        val st = surfaceTexture ?: run {
            Log.d(TAG, "rebindCamera: surfaceTexture is null, waiting for surface ready")
            return
        }
        captureEngine.open(
            st = st,
            isPreviewHd = isPreviewBufferHd(),
            isLegacyJpeg = isLegacyJpegPath()
        )
    }

    fun isLegacyJpegPath(): Boolean {
        val prefs = context.getSharedPreferences("aurora_cam_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("legacy_jpeg_path", false)
    }

    fun setLegacyJpegPath(enabled: Boolean) {
        val prefs = context.getSharedPreferences("aurora_cam_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("legacy_jpeg_path", enabled).apply()
        rebindCamera()
    }

    fun isPreviewBufferHd(): Boolean {
        val prefs = context.getSharedPreferences("aurora_cam_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("preview_buffer_hd", false)
    }

    fun setPreviewBufferHd(enabled: Boolean) {
        val prefs = context.getSharedPreferences("aurora_cam_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("preview_buffer_hd", enabled).apply()
        rebindCamera()
    }

    fun isLookPrecision16f(): Boolean {
        val prefs = context.getSharedPreferences("aurora_cam_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("look_precision_16f", true)
    }

    fun setLookPrecision16f(enabled: Boolean) {
        val prefs = context.getSharedPreferences("aurora_cam_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("look_precision_16f", enabled).apply()
    }

    fun isBurstStack(): Boolean {
        val prefs = context.getSharedPreferences("aurora_cam_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("burst_stack", false) // Default OFF per plan
    }

    fun setBurstStack(enabled: Boolean) {
        val prefs = context.getSharedPreferences("aurora_cam_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("burst_stack", enabled).apply()
        Log.i(TAG, "QuickStack Burst preference updated: burst_stack=$enabled")
    }

    val cameraCharacteristics: android.hardware.camera2.CameraCharacteristics?
        get() = captureEngine.cameraCharacteristics

    val isRawSupported: Boolean
        get() = captureEngine.isRawSupported

    fun isRawCaptureEnabled(): Boolean {
        val prefs = context.getSharedPreferences("aurora_cam_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("raw_capture_enabled", false)
    }

    fun setRawCaptureEnabled(enabled: Boolean) {
        val prefs = context.getSharedPreferences("aurora_cam_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("raw_capture_enabled", enabled).apply()
        captureEngine.isRawCaptureEnabled = enabled
        Log.i(TAG, "RAW Sensor capture preference updated: raw_capture_enabled=$enabled")
    }

    fun getFlashMode(): FlashMode {
        val prefs = context.getSharedPreferences("aurora_cam_prefs", Context.MODE_PRIVATE)
        val name = prefs.getString("flash_mode", FlashMode.OFF.name)
        return try {
            FlashMode.valueOf(name ?: FlashMode.OFF.name)
        } catch (e: Exception) {
            FlashMode.OFF
        }
    }

    fun setFlashMode(mode: FlashMode) {
        val prefs = context.getSharedPreferences("aurora_cam_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("flash_mode", mode.name).apply()
        captureEngine.setFlashMode(mode)
        Log.i(TAG, "Flash mode preference updated: flash_mode=$mode")
    }

    fun setManualFocus(enabled: Boolean, distanceDiopters: Float = 0.0f) {
        captureEngine.setManualFocus(enabled, distanceDiopters)
    }

    fun setAeAwbLock(locked: Boolean) {
        captureEngine.setAeAwbLock(locked)
    }

    fun setExposureCompensation(ev: Float) {
        captureEngine.setExposureCompensation(ev)
    }

    fun triggerPrecaptureAe(onReady: () -> Unit) {
        captureEngine.triggerPrecaptureAe(onReady)
    }

    fun triggerTapToFocus(xNorm: Float, yNorm: Float) {
        captureEngine.triggerTapToFocus(xNorm, yNorm)
    }

    fun setManualExposure(enabled: Boolean, shutterSpeedNanos: Long = 500_000_000L, iso: Int = 50) {
        captureEngine.setManualExposure(enabled, shutterSpeedNanos, iso)
    }

    fun takePictureBitmap(
        onBitmapCaptured: (Bitmap?) -> Unit,
        onRawCaptured: ((android.media.Image, android.hardware.camera2.TotalCaptureResult) -> Unit)? = null
    ) {
        captureEngine.isRawCaptureEnabled = isRawCaptureEnabled()
        captureEngine.captureSingleStill(
            isLegacyJpeg = isLegacyJpegPath(),
            onBitmapCaptured = onBitmapCaptured,
            onRawCaptured = onRawCaptured
        )
    }

    fun takeBurst(
        burstCount: Int = 6,
        onBurstAcquired: (List<RawYuvFrame>?, String) -> Unit
    ) {
        captureEngine.captureBurst(burstCount = burstCount, onBurstAcquired = onBurstAcquired)
    }

    fun release() {
        captureEngine.release()
        surfaceTexture = null
    }
}
