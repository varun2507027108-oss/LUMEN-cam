package com.auroracam.app.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.StreamConfigurationMap
import android.net.Uri
import android.util.Log
import android.util.Range
import android.util.Rational
import android.util.Size
import android.view.Surface
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.auroracam.app.capture.CaptureSaver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    companion object {
        private const val TAG = "CameraController"
        val PREFERRED_PREVIEW_SIZE = Size(1600, 1200)
        val PREFERRED_CAPTURE_SIZE = Size(3200, 2400)
    }

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null

    private var previewUseCase: Preview? = null
    private var imageCaptureUseCase: ImageCapture? = null

    private var surfaceTexture: SurfaceTexture? = null
    private var currentSurface: Surface? = null

    private var isAeAwbLocked = false
    private var isManualExposureActive = false

    fun onSurfaceTextureReady(st: SurfaceTexture) {
        Log.i(TAG, "onSurfaceTextureReady: new SurfaceTexture received")
        surfaceTexture = st
        currentSurface?.release()
        currentSurface = Surface(st)
        ContextCompat.getMainExecutor(context).execute {
            bindCameraUseCases()
        }
    }

    fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(context))
    }

    fun isLegacyJpegPath(): Boolean {
        val prefs = context.getSharedPreferences("aurora_cam_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("legacy_jpeg_path", true)
    }

    fun setLegacyJpegPath(enabled: Boolean) {
        val prefs = context.getSharedPreferences("aurora_cam_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("legacy_jpeg_path", enabled).apply()
        ContextCompat.getMainExecutor(context).execute {
            bindCameraUseCases()
        }
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        val surface = currentSurface ?: return
        val st = surfaceTexture ?: return

        try {
            provider.unbindAll()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            // Query CameraCharacteristics for dynamic sizes
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: cameraManager.cameraIdList.firstOrNull() ?: "0"
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

            val useLegacy = isLegacyJpegPath()
            val queryFormat = if (useLegacy) android.graphics.ImageFormat.JPEG else android.graphics.ImageFormat.YUV_420_888
            val availableSizes: Array<Size> = map?.getOutputSizes(queryFormat) ?: emptyArray()

            Log.i(TAG, "Available ${if (useLegacy) "JPEG" else "YUV"} sizes (first 10): ${availableSizes.take(10).joinToString { size -> "${size.width}x${size.height}" }}")

            val fourByThreeSizes = availableSizes.filter { size ->
                val ratio = size.width.toFloat() / size.height.toFloat()
                kotlin.math.abs(ratio - (4f / 3f)) < 0.05f || kotlin.math.abs(ratio - (3f / 4f)) < 0.05f
            }.sortedByDescending { size -> size.width.toLong() * size.height.toLong() }

            val targetCaptureSize = fourByThreeSizes.firstOrNull()
                ?: availableSizes.maxByOrNull { size -> size.width.toLong() * size.height.toLong() }
                ?: PREFERRED_CAPTURE_SIZE

            Log.i(TAG, "Chosen capture targetSize: ${targetCaptureSize.width}x${targetCaptureSize.height} (useLegacyJpeg=$useLegacy)")

            val previewResolutionSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(
                    ResolutionStrategy(
                        PREFERRED_PREVIEW_SIZE,
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
                .build()

            val captureResolutionSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(
                    ResolutionStrategy(
                        targetCaptureSize,
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
                .build()

            previewUseCase = Preview.Builder()
                .setResolutionSelector(previewResolutionSelector)
                .build()

            previewUseCase?.setSurfaceProvider(cameraExecutor) { request ->
                val res = request.resolution
                Log.i(TAG, "STEP0 buffers: request=${res.width}x${res.height} tex=${res.width}x${res.height}")
                Log.i(TAG, "SurfaceRequest resolved preview size: ${res.width}x${res.height}")
                st.setDefaultBufferSize(res.width, res.height)
                Log.i(TAG, "SurfaceTexture buffer size configured to: ${res.width}x${res.height}")
                request.provideSurface(surface, cameraExecutor) {
                    Log.i(TAG, "SurfaceRequest completed, result code: ${it.resultCode}")
                }
            }

            val imageCaptureBuilder = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setResolutionSelector(captureResolutionSelector)

            if (useLegacy) {
                imageCaptureBuilder.setBufferFormat(android.graphics.ImageFormat.JPEG)
                imageCaptureBuilder.setJpegQuality(95)
            } else {
                imageCaptureBuilder.setBufferFormat(android.graphics.ImageFormat.YUV_420_888)
            }

            // High-quality processing & sensor metrics logging per still capture request
            val captureExtender = androidx.camera.camera2.interop.Camera2Interop.Extender(imageCaptureBuilder)
            captureExtender.setCaptureRequestOption(
                CaptureRequest.NOISE_REDUCTION_MODE,
                CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY
            )
            captureExtender.setCaptureRequestOption(
                CaptureRequest.EDGE_MODE,
                CaptureRequest.EDGE_MODE_HIGH_QUALITY
            )
            captureExtender.setSessionCaptureCallback(object : android.hardware.camera2.CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: android.hardware.camera2.CameraCaptureSession,
                    request: CaptureRequest,
                    result: android.hardware.camera2.TotalCaptureResult
                ) {
                    super.onCaptureCompleted(session, request, result)
                    val iso = result.get(android.hardware.camera2.CaptureResult.SENSOR_SENSITIVITY)
                    val expTime = result.get(android.hardware.camera2.CaptureResult.SENSOR_EXPOSURE_TIME)
                    val nrMode = result.get(android.hardware.camera2.CaptureResult.NOISE_REDUCTION_MODE)
                    val edgeMode = result.get(android.hardware.camera2.CaptureResult.EDGE_MODE)
                    val tonemapMode = result.get(android.hardware.camera2.CaptureResult.TONEMAP_MODE)
                    val colorMode = result.get(android.hardware.camera2.CaptureResult.COLOR_CORRECTION_MODE)
                    val expTimeFormatted = if (expTime != null && expTime > 0) "1/${(1_000_000_000.0 / expTime).toInt()}s" else "N/A"
                    Log.i(TAG, "STILL CAPTURE SENSOR METRICS: ISO=$iso, ExpTime=${expTime}ns ($expTimeFormatted), NR_MODE=$nrMode, EDGE_MODE=$edgeMode, TONEMAP_MODE=$tonemapMode, COLOR_CORRECTION_MODE=$colorMode")
                }
            })

            imageCaptureUseCase = imageCaptureBuilder.build()

            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                previewUseCase,
                imageCaptureUseCase
            )

            // Setup continuous autofocus & kick AF trigger
            setupContinuousAutofocus()

            val resolvedPreview = previewUseCase?.resolutionInfo?.resolution
            val resolvedCapture = imageCaptureUseCase?.resolutionInfo?.resolution
            Log.i(TAG, "=== CAMERA BIND SUCCESSFUL ===")
            Log.i(TAG, "Resolved Preview Size: ${resolvedPreview ?: "1600x1200"}")
            Log.i(TAG, "Resolved ImageCapture Size: ${resolvedCapture ?: targetCaptureSize}")

        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun setupContinuousAutofocus() {
        val cam = camera ?: return
        val camera2Control = Camera2CameraControl.from(cam.cameraControl)

        val options = CaptureRequestOptions.Builder()
            .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            .setCaptureRequestOption(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
            .build()

        camera2Control.setCaptureRequestOptions(options)
        Log.i(TAG, "Autofocus configured: CONTINUOUS_PICTURE with initial trigger")

        // Kick AF metering again after 2 seconds
        CoroutineScope(Dispatchers.Main).launch {
            delay(2000)
            val kickOptions = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
                .build()
            camera2Control.setCaptureRequestOptions(kickOptions)
            Log.i(TAG, "Autofocus metering kicked at 2s post-bind")
        }
    }

    @OptIn(ExperimentalCamera2Interop::class)
    fun setAeAwbLock(locked: Boolean) {
        val cam = camera ?: return
        isAeAwbLocked = locked
        val camera2Control = Camera2CameraControl.from(cam.cameraControl)
        val options = CaptureRequestOptions.Builder()
            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, locked)
            .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, locked)
            .build()
        camera2Control.setCaptureRequestOptions(options)
        Log.i(TAG, "Exposure & AWB lock applied: $locked")
    }

    fun setExposureCompensation(ev: Float) {
        val cam = camera ?: return
        val exposureState = cam.cameraInfo.exposureState
        val step = exposureState.exposureCompensationStep
        val stepVal = if (step.denominator != 0) step.numerator.toFloat() / step.denominator.toFloat() else 0.1666f
        val index = (ev / stepVal).toInt().coerceIn(exposureState.exposureCompensationRange.lower, exposureState.exposureCompensationRange.upper)
        cam.cameraControl.setExposureCompensationIndex(index)
        Log.i(TAG, "Set EV Bias: $ev (Index: $index, Step: $stepVal)")
    }

    @OptIn(ExperimentalCamera2Interop::class)
    fun triggerPrecaptureAe(onReady: () -> Unit) {
        val cam = camera ?: run {
            onReady()
            return
        }
        val camera2Control = Camera2CameraControl.from(cam.cameraControl)
        val options = CaptureRequestOptions.Builder()
            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START)
            .build()
        camera2Control.setCaptureRequestOptions(options)
        Log.i(TAG, "Precapture AE trigger sent before capture")

        CoroutineScope(Dispatchers.Main).launch {
            delay(150) // Allow HAL precapture sweep under locked state
            onReady()
        }
    }

    @OptIn(ExperimentalCamera2Interop::class)
    fun setManualExposure(enabled: Boolean, shutterSpeedNanos: Long = 500_000_000L, iso: Int = 50) {
        isManualExposureActive = enabled
        val cam = camera ?: return
        val camera2Control = Camera2CameraControl.from(cam.cameraControl)
        val optionsBuilder = CaptureRequestOptions.Builder()
        if (enabled) {
            optionsBuilder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            optionsBuilder.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, shutterSpeedNanos)
            optionsBuilder.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, iso)
        } else {
            optionsBuilder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        }
        camera2Control.setCaptureRequestOptions(optionsBuilder.build())
    }

    fun takePicture(onImageSaved: (Uri?) -> Unit) {
        val capture = imageCaptureUseCase ?: run {
            onImageSaved(null)
            return
        }

        capture.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val uri = CaptureSaver.saveImageProxy(context, image)
                        ContextCompat.getMainExecutor(context).execute { onImageSaved(uri) }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Image capture failed: ${exception.message}", exception)
                    ContextCompat.getMainExecutor(context).execute { onImageSaved(null) }
                }
            }
        )
    }

    fun takePictureBitmap(onBitmapCaptured: (Bitmap?) -> Unit) {
        val capture = imageCaptureUseCase ?: run {
            onBitmapCaptured(null)
            return
        }

        triggerPrecaptureAe {
            capture.takePicture(
                cameraExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val rotationDegrees = image.imageInfo.rotationDegrees
                        val format = image.format
                        val w = image.width
                        val h = image.height
                        val planesInfo = image.planes.mapIndexed { idx, plane ->
                            "plane[$idx]: rowStride=${plane.rowStride}, pixelStride=${plane.pixelStride}, bufRemaining=${plane.buffer.remaining()}"
                        }.joinToString("; ")
                        Log.i(TAG, "ImageProxy captured: format=$format, size=${w}x${h}, rotation=$rotationDegrees, planes: $planesInfo")

                        val rawBitmap = image.toBitmap()
                        image.close()

                        // Calculate average luma (sampling every 100th pixel)
                        var lumaSum = 0L
                        var samples = 0
                        val bw = rawBitmap.width
                        val bh = rawBitmap.height
                        val rowPixels = IntArray(bw)
                        for (y in 0 until bh step 10) {
                            rawBitmap.getPixels(rowPixels, 0, bw, 0, y, bw, 1)
                            for (x in 0 until bw step 10) {
                                val c = rowPixels[x]
                                val r = (c shr 16) and 0xFF
                                val g = (c shr 8) and 0xFF
                                val b = c and 0xFF
                                val luma = (0.299f * r + 0.587f * g + 0.114f * b).toInt()
                                lumaSum += luma
                                samples++
                            }
                        }
                        val avgLuma = if (samples > 0) lumaSum.toDouble() / samples else 0.0
                        Log.i(TAG, "image.toBitmap() created: config=${rawBitmap.config}, size=${bw}x${bh}, avgLuma=${"%.2f".format(avgLuma)} (sampled $samples px)")

                        CoroutineScope(Dispatchers.Default).launch {
                            val uprightBitmap = if (rotationDegrees != 0) {
                                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                                val rotated = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                                if (rotated != rawBitmap) {
                                    rawBitmap.recycle()
                                }
                                rotated
                            } else {
                                rawBitmap
                            }
                            ContextCompat.getMainExecutor(context).execute {
                                onBitmapCaptured(uprightBitmap)
                            }
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e(TAG, "Bitmap capture failed: ${exception.message}", exception)
                        ContextCompat.getMainExecutor(context).execute { onBitmapCaptured(null) }
                    }
                }
            )
        }
    }

    fun release() {
        setAeAwbLock(false)
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        currentSurface?.release()
        currentSurface = null
        surfaceTexture = null
    }
}
