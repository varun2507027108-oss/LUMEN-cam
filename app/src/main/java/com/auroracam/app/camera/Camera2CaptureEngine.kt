package com.auroracam.app.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.MeteringRectangle
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import com.auroracam.app.camera.burst.RawYuvFrame
import com.auroracam.app.camera.burst.YuvBufferPool
import com.auroracam.app.capture.CaptureSaver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import kotlin.math.roundToLong

enum class FlashMode(val label: String) {
    OFF("OFF"),
    AUTO("AUTO"),
    ON("ON"),
    TORCH("TORCH")
}

class Camera2CaptureEngine(
    private val context: Context
) {
    companion object {
        private const val TAG = "Camera2CaptureEngine"
        val PREFERRED_PREVIEW_SIZE = Size(1600, 1200)
        val PREFERRED_PREVIEW_HD_SIZE = Size(1920, 1440)
        val PREFERRED_CAPTURE_SIZE = Size(3200, 2400)
    }

    private data class StreamUseCaseSupport(
        val canUseStreamUseCases: Boolean,
        val supportsPreview: Boolean,
        val supportsStillCapture: Boolean
    )

    private fun resolveStreamUseCaseSupport(
        characteristics: CameraCharacteristics
    ): StreamUseCaseSupport {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            return StreamUseCaseSupport(
                canUseStreamUseCases = false,
                supportsPreview = false,
                supportsStillCapture = false
            )
        }

        val available = characteristics.get(
            CameraCharacteristics.SCALER_AVAILABLE_STREAM_USE_CASES
        )?.toSet().orEmpty()

        val previewUseCase =
            CameraCharacteristics.SCALER_AVAILABLE_STREAM_USE_CASES_PREVIEW.toLong()

        val stillUseCase =
            CameraCharacteristics.SCALER_AVAILABLE_STREAM_USE_CASES_STILL_CAPTURE.toLong()

        return StreamUseCaseSupport(
            canUseStreamUseCases = available.isNotEmpty(),
            supportsPreview = previewUseCase in available,
            supportsStillCapture = stillUseCase in available
        )
    }

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null

    private var surfaceTexture: SurfaceTexture? = null
    private var previewSurface: Surface? = null
    private var captureYuvReader: ImageReader? = null
    private var captureJpegReader: ImageReader? = null
    private var captureRawReader: ImageReader? = null

    private var currentCameraId: String = "0"
    private var characteristics: CameraCharacteristics? = null
    private var maxAnalogIso: Int = 800
    private var sensorOrientation: Int = 90

    private val latestCaptureResult = AtomicReference<TotalCaptureResult?>()
    var lastTelemetry: CaptureSaver.Telemetry = CaptureSaver.Telemetry()
        private set

    var resolvedCaptureSize: Size = PREFERRED_CAPTURE_SIZE
        private set

    var resolvedRawSize: Size? = null
        private set

    private val cameraSessionExecutor: ExecutorService by lazy {
        Executors.newSingleThreadExecutor()
    }

    val cameraCharacteristics: CameraCharacteristics?
        get() = characteristics

    val isRawSupported: Boolean
        get() {
            val caps = characteristics?.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: return false
            return caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
        }

    var flashMode: FlashMode = FlashMode.OFF
        private set

    var isManualFocus: Boolean = false
        private set

    var manualFocusDistance: Float = 0.0f
        private set

    var manualShutterNanos: Long = 500_000_000L
        private set

    var manualIso: Int = 50
        private set

    private var isAeAwbLocked = false
    private var isManualExposure = false
    private var currentEvBias: Float = 0.0f
    private var evRange: Range<Int> = Range(0, 0)
    private var evStep: Float = 1.0f / 6.0f

    private var isProbeCompleted = false
    private var currentMeteringRectangle: MeteringRectangle? = null

    private val cameraLock = Any()
    private val isOpeningOrOpen = AtomicBoolean(false)
    private val openGeneration = AtomicInteger(0)

    private fun startBackgroundThread() {
        synchronized(cameraLock) {
            if (cameraThread == null) {
                cameraThread = HandlerThread("Camera2Background").apply {
                    start()
                    cameraHandler = Handler(looper)
                }
            }
        }
    }

    private fun stopBackgroundThread() {
        synchronized(cameraLock) {
            cameraThread?.quitSafely()
            try {
                cameraThread?.join(500)
                cameraThread = null
                cameraHandler = null
            } catch (e: InterruptedException) {
                Log.e(TAG, "Error stopping camera background thread", e)
            }
        }
    }

    fun closeCamera() {
        openGeneration.incrementAndGet()
        synchronized(cameraLock) {
            try {
                isOpeningOrOpen.set(false)
                captureSession?.close()
                captureSession = null
                cameraDevice?.close()
                cameraDevice = null
                captureYuvReader?.close()
                captureYuvReader = null
                captureJpegReader?.close()
                captureJpegReader = null
                captureRawReader?.close()
                captureRawReader = null
                previewSurface?.release()
                previewSurface = null
            } catch (e: Exception) {
                Log.e(TAG, "Error closing camera: ${e.message}")
            }
        }
        // Clear pooled YUV buffers so they don't linger across camera sessions
        YuvBufferPool.clear()
    }

    fun open(
        st: SurfaceTexture,
        isPreviewHd: Boolean,
        isLegacyJpeg: Boolean,
        onOpened: (() -> Unit)? = null
    ) {
        synchronized(cameraLock) {
            closeCamera()
            val myGen = openGeneration.incrementAndGet()
            startBackgroundThread()
            surfaceTexture = st

            try {
                val probe = CameraProbe.probeCameraCapabilities(context, cameraManager)
                currentCameraId = probe.backCameraId
                characteristics = cameraManager.getCameraCharacteristics(currentCameraId)
                maxAnalogIso = probe.maxAnalogSensitivity
                sensorOrientation = characteristics?.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90

                val targetPreviewSize = if (isPreviewHd) PREFERRED_PREVIEW_HD_SIZE else PREFERRED_PREVIEW_SIZE
                st.setDefaultBufferSize(targetPreviewSize.width, targetPreviewSize.height)
                Log.i(TAG, "SurfaceTexture buffer size configured to: ${targetPreviewSize.width}x${targetPreviewSize.height}")

                previewSurface?.release()
                previewSurface = Surface(st)

                resolvedCaptureSize = probe.resolvedYuvSize ?: PREFERRED_CAPTURE_SIZE
                Log.i(TAG, "Capture ImageReader configured size: ${resolvedCaptureSize.width}x${resolvedCaptureSize.height}")

                captureYuvReader?.close()
                captureYuvReader = ImageReader.newInstance(
                    resolvedCaptureSize.width,
                    resolvedCaptureSize.height,
                    ImageFormat.YUV_420_888,
                    8
                )

                captureJpegReader?.close()
                captureJpegReader = null
                if (isLegacyJpeg) {
                    captureJpegReader = ImageReader.newInstance(
                        resolvedCaptureSize.width,
                        resolvedCaptureSize.height,
                        ImageFormat.JPEG,
                        2
                    )
                }

                captureRawReader?.close()
                captureRawReader = null
                resolvedRawSize = null
                if (isRawSupported) {
                    val map = characteristics?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    val rawSizes = map?.getOutputSizes(ImageFormat.RAW_SENSOR)
                    val maxRaw = rawSizes?.maxByOrNull { it.width * it.height }
                    if (maxRaw != null) {
                        captureRawReader = ImageReader.newInstance(
                            maxRaw.width,
                            maxRaw.height,
                            ImageFormat.RAW_SENSOR,
                            2
                        )
                        resolvedRawSize = maxRaw
                        Log.i(TAG, "RAW Sensor ImageReader configured size: ${maxRaw.width}x${maxRaw.height}")
                    }
                }

                // EV Compensation limits
                val compRange = characteristics?.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
                if (compRange != null && compRange.upper > compRange.lower) {
                    evRange = compRange
                } else {
                    evRange = Range(-12, 12)
                }
                val compStep = characteristics?.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)
                if (compStep != null && compStep.denominator > 0 && compStep.numerator > 0) {
                    evStep = compStep.numerator.toFloat() / compStep.denominator.toFloat()
                } else {
                    evStep = 1.0f / 3.0f
                }
                Log.i(TAG, "EV Compensation initialized: range=$evRange, step=$evStep")

                isOpeningOrOpen.set(true)
                @SuppressLint("MissingPermission")
                cameraManager.openCamera(currentCameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(device: CameraDevice) {
                        Log.i(TAG, "CameraDevice onOpened: id=$currentCameraId gen=$myGen (activeGen=${openGeneration.get()})")
                        synchronized(cameraLock) {
                            if (openGeneration.get() != myGen || !isOpeningOrOpen.get()) {
                                Log.w(TAG, "CameraDevice onOpened discarded: superseded by newer generation")
                                try { device.close() } catch (e: Exception) {}
                                return
                            }
                            cameraDevice = device
                            createCameraSession(myGen, onOpened)
                        }
                    }

                    override fun onDisconnected(device: CameraDevice) {
                        Log.w(TAG, "CameraDevice onDisconnected gen=$myGen")
                        synchronized(cameraLock) {
                            try { device.close() } catch (e: Exception) {}
                            if (cameraDevice == device) cameraDevice = null
                        }
                    }

                    override fun onError(device: CameraDevice, error: Int) {
                        Log.e(TAG, "CameraDevice onError: code=$error gen=$myGen")
                        synchronized(cameraLock) {
                            try {
                                device.close()
                            } catch (e: Exception) {
                                Log.w(TAG, "Exception closing device in onError: ${e.message}")
                            }
                            if (cameraDevice == device) cameraDevice = null
                        }
                    }
                }, cameraHandler)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open camera: ${e.message}", e)
                isOpeningOrOpen.set(false)
            }
        }
    }

    private fun createCameraSession(generation: Int, onSessionReady: (() -> Unit)?) {
        val device = cameraDevice ?: return
        val preview = previewSurface ?: return
        val yuvReader = captureYuvReader ?: return
        val jpegReader = captureJpegReader
        val rawReader = captureRawReader

        CameraProbe.checkSessionConfigurationSupport(
            device = device,
            previewSurface = preview,
            yuvSurface = yuvReader.surface,
            jpegSurface = jpegReader?.surface
        )

        val sessionCallback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                Log.i(TAG, "=== CAMERA2 CAPTURE SESSION CONFIGURED SUCCESSFULLY (gen=$generation) ===")
                synchronized(cameraLock) {
                    if (openGeneration.get() != generation || cameraDevice == null) {
                        Log.w(TAG, "CaptureSession onConfigured discarded: superseded by newer generation")
                        try { session.close() } catch (e: Exception) {}
                        return
                    }
                    captureSession = session
                    isProbeCompleted = true

                    startRepeatingPreview()
                    onSessionReady?.invoke()
                }
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                Log.e(TAG, "CameraCaptureSession onConfigureFailed (gen=$generation)")
                try { session.close() } catch (e: Exception) {}
            }
        }

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                // API 33+: tag each stream with its Stream Use Case so the camera HAL
                // can apply per-stream optimization (responsive preview vs. high-quality
                // still capture) instead of treating every surface identically.
                val useCaseSupport = characteristics?.let { resolveStreamUseCaseSupport(it) }
                val outputConfigs = mutableListOf<android.hardware.camera2.params.OutputConfiguration>()

                val previewConfig = android.hardware.camera2.params.OutputConfiguration(preview).apply {
                    if (useCaseSupport?.supportsPreview == true) {
                        streamUseCase = CameraCharacteristics.SCALER_AVAILABLE_STREAM_USE_CASES_PREVIEW.toLong()
                    }
                }
                outputConfigs.add(previewConfig)

                val yuvConfig = android.hardware.camera2.params.OutputConfiguration(yuvReader.surface).apply {
                    if (useCaseSupport?.supportsStillCapture == true) {
                        streamUseCase = CameraCharacteristics.SCALER_AVAILABLE_STREAM_USE_CASES_STILL_CAPTURE.toLong()
                    }
                }
                outputConfigs.add(yuvConfig)

                jpegReader?.let {
                    val jpegConfig = android.hardware.camera2.params.OutputConfiguration(it.surface).apply {
                        if (useCaseSupport?.supportsStillCapture == true) {
                            streamUseCase = CameraCharacteristics.SCALER_AVAILABLE_STREAM_USE_CASES_STILL_CAPTURE.toLong()
                        }
                    }
                    outputConfigs.add(jpegConfig)
                }

                rawReader?.let {
                    // RAW doesn't have its own dedicated use-case constant; leave it
                    // untagged (DEFAULT) rather than guessing.
                    outputConfigs.add(android.hardware.camera2.params.OutputConfiguration(it.surface))
                }

                val sessionConfig = android.hardware.camera2.params.SessionConfiguration(
                    android.hardware.camera2.params.SessionConfiguration.SESSION_REGULAR,
                    outputConfigs,
                    cameraSessionExecutor,
                    sessionCallback
                )
                device.createCaptureSession(sessionConfig)
            } else {
                // Pre-API-33 fallback: legacy path, no stream use case tagging available.
                val surfaces = mutableListOf<Surface>(preview, yuvReader.surface)
                jpegReader?.let { surfaces.add(it.surface) }
                rawReader?.let { surfaces.add(it.surface) }

                @Suppress("DEPRECATION")
                device.createCaptureSession(surfaces, sessionCallback, cameraHandler)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create Camera2 capture session: ${e.message}", e)
        }
    }

    private fun applyPreviewSettings(builder: CaptureRequest.Builder) {
        if (isManualFocus) {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, manualFocusDistance)
        } else if (currentMeteringRectangle != null) {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
            builder.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(currentMeteringRectangle))
        } else {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        }

        if (isManualExposure) {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, manualShutterNanos)
            builder.set(CaptureRequest.SENSOR_SENSITIVITY, manualIso)
            if (flashMode == FlashMode.TORCH) {
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
            } else {
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            }
        } else {
            when (flashMode) {
                FlashMode.OFF -> {
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                    builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                }
                FlashMode.AUTO -> {
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                    builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                }
                FlashMode.ON -> {
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH)
                    builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                }
                FlashMode.TORCH -> {
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                    builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                }
            }
            val evIndex = if (evStep > 0.001f) {
                (currentEvBias / evStep).roundToInt().coerceIn(evRange.lower, evRange.upper)
            } else 0
            builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, evIndex)
            currentMeteringRectangle?.let {
                builder.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(it))
            }
        }

        if (isSimpleHdrEnabled && !isManualExposure) {
            val sceneModes = cameraCharacteristics?.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES)
            if (sceneModes != null && sceneModes.contains(CameraCharacteristics.CONTROL_SCENE_MODE_HDR)) {
                builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_USE_SCENE_MODE)
                builder.set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_HDR)
            }
        }

        builder.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO)
        builder.set(CaptureRequest.CONTROL_AE_LOCK, isAeAwbLocked)
        builder.set(CaptureRequest.CONTROL_AWB_LOCK, isAeAwbLocked)
    }

    private fun startRepeatingPreview() {
        val session = captureSession ?: return
        val device = cameraDevice ?: return
        val preview = previewSurface ?: return

        try {
            val reqBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(preview)
                applyPreviewSettings(this)
            }

            session.setRepeatingRequest(reqBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    super.onCaptureCompleted(session, request, result)
                    latestCaptureResult.set(result)

                    val iso = result.get(CaptureResult.SENSOR_SENSITIVITY)
                    val expTime = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
                    val expTimeFormatted = if (expTime != null && expTime > 0) "1/${(1_000_000_000.0 / expTime).toInt()}s" else "N/A"
                    lastTelemetry = CaptureSaver.Telemetry(iso = iso ?: 0, expTimeFormatted = expTimeFormatted)
                }
            }, cameraHandler)
            Log.i(TAG, "Repeating preview stream started (Flash=$flashMode, ManualExp=$isManualExposure, ManualFocus=$isManualFocus)")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting repeating preview: ${e.message}", e)
        }
    }

    fun setAeAwbLock(locked: Boolean) {
        isAeAwbLocked = locked
        startRepeatingPreview()
        Log.i(TAG, "Exposure & AWB lock applied: $locked")
    }

    fun setFlashMode(mode: FlashMode) {
        flashMode = mode
        startRepeatingPreview()
        Log.i(TAG, "Flash mode updated: $mode")
    }

    fun setManualFocus(enabled: Boolean, distanceDiopters: Float = 0.0f) {
        isManualFocus = enabled
        manualFocusDistance = distanceDiopters
        startRepeatingPreview()
        Log.i(TAG, "Manual focus updated: enabled=$enabled, distance=$distanceDiopters")
    }

    fun setExposureCompensation(ev: Float) {
        currentEvBias = ev
        startRepeatingPreview()
        Log.i(TAG, "EV Bias updated: ev=$ev, step=$evStep -> index=${if (evStep > 0.001f) (currentEvBias / evStep).roundToInt() else 0}")
    }

    fun setManualExposure(enabled: Boolean, shutterSpeedNanos: Long = 500_000_000L, iso: Int = 50) {
        isManualExposure = enabled
        manualShutterNanos = shutterSpeedNanos
        manualIso = iso
        startRepeatingPreview()
        Log.i(TAG, "Manual exposure mode updated: enabled=$enabled, t=${shutterSpeedNanos}ns, iso=$iso")
    }

    fun triggerTapToFocus(xNorm: Float, yNorm: Float) {
        val session = captureSession ?: return
        val device = cameraDevice ?: return
        val preview = previewSurface ?: return
        val sensorRect = characteristics?.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: Rect(0, 0, 3200, 2400)

        val focusAreaSize = 150
        val centerX = (xNorm * sensorRect.width()).toInt().coerceIn(focusAreaSize, sensorRect.width() - focusAreaSize)
        val centerY = (yNorm * sensorRect.height()).toInt().coerceIn(focusAreaSize, sensorRect.height() - focusAreaSize)
        val afRect = Rect(centerX - focusAreaSize, centerY - focusAreaSize, centerX + focusAreaSize, centerY + focusAreaSize)
        val meteringRectangle = MeteringRectangle(afRect, MeteringRectangle.METERING_WEIGHT_MAX)
        currentMeteringRectangle = meteringRectangle

        try {
            val cancelReq = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(preview)
                set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
            }
            session.capture(cancelReq.build(), null, cameraHandler)

            val repBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(preview)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO)
                set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(meteringRectangle))
                set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(meteringRectangle))
                val evIndex = (currentEvBias / evStep).toInt().coerceIn(evRange.lower, evRange.upper)
                set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, evIndex)
            }
            session.setRepeatingRequest(repBuilder.build(), null, cameraHandler)

            val triggerReq = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(preview)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
                set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(meteringRectangle))
                set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(meteringRectangle))
                set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
            }
            session.capture(triggerReq.build(), null, cameraHandler)
            Log.i(TAG, "Tap-to-focus triggered at norm($xNorm, $yNorm), region=$afRect")
        } catch (e: Exception) {
            Log.e(TAG, "Tap to focus trigger failed: ${e.message}", e)
        }
    }

    fun triggerPrecaptureAe(onReady: () -> Unit) {
        val session = captureSession ?: run {
            onReady()
            return
        }
        val device = cameraDevice ?: run {
            onReady()
            return
        }
        val preview = previewSurface ?: run {
            onReady()
            return
        }

        try {
            val reqBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(preview)
                set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START)
            }
            session.capture(reqBuilder.build(), null, cameraHandler)
            Log.i(TAG, "Precapture AE trigger sent before capture")
            CoroutineScope(Dispatchers.Main).launch {
                delay(120)
                onReady()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Precapture trigger error", e)
            onReady()
        }
    }

    @Volatile
    var isRawCaptureEnabled: Boolean = false

    @Volatile
    var isSimpleHdrEnabled: Boolean = false

    fun applySimpleHdrMode(enabled: Boolean) {
        isSimpleHdrEnabled = enabled
        startRepeatingPreview()
        Log.i(TAG, "Simple HDR mode updated: isSimpleHdrEnabled=$enabled")
    }

    val isHdrSceneModeSupported: Boolean
        get() {
            val modes = cameraCharacteristics?.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES)
            return modes != null && modes.contains(CameraCharacteristics.CONTROL_SCENE_MODE_HDR)
        }

    /**
     * Captures a single still from the reader (YUV or JPEG) and converts to upright Bitmap.
     * If RAW stream is enabled, simultaneously captures a RAW_SENSOR DNG frame.
     */
    fun captureSingleStill(
        isLegacyJpeg: Boolean,
        onBitmapCaptured: (Bitmap?) -> Unit,
        onRawCaptured: ((Image, TotalCaptureResult) -> Unit)? = null
    ) {
        val session = captureSession ?: run { onBitmapCaptured(null); return }
        val device = cameraDevice ?: run { onBitmapCaptured(null); return }
        val reader = if (isLegacyJpeg) captureJpegReader else captureYuvReader
        val rawReader = if (isRawCaptureEnabled && isRawSupported) captureRawReader else null

        if (reader == null) {
            Log.e(TAG, "captureSingleStill: Target reader is null!")
            onBitmapCaptured(null)
            return
        }

        triggerPrecaptureAe {
            val isDone = AtomicBoolean(false)
            reader.setOnImageAvailableListener({ ir ->
                while (true) {
                    val image = try {
                        ir.acquireNextImage()
                    } catch (e: Exception) {
                        null
                    } ?: break

                    if (isDone.compareAndSet(false, true)) {
                        ir.setOnImageAvailableListener(null, null)
                        CoroutineScope(Dispatchers.Default).launch {
                            try {
                                val bitmap = decodeImageToUprightBitmap(image)
                                withContext(Dispatchers.Main) {
                                    onBitmapCaptured(bitmap)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error decoding single still", e)
                                withContext(Dispatchers.Main) {
                                    onBitmapCaptured(null)
                                }
                            } finally {
                                image.close()
                            }
                        }
                    } else {
                        image.close()
                    }
                }
            }, cameraHandler)

            // Setup RAW reader listener if active
            var capturedRawImage: Image? = null
            var capturedTotalResult: TotalCaptureResult? = null
            val rawLock = Any()

            val tryDispatchRaw = {
                var imgToDispatch: Image? = null
                var resToDispatch: TotalCaptureResult? = null
                synchronized(rawLock) {
                    if (capturedRawImage != null && capturedTotalResult != null) {
                        imgToDispatch = capturedRawImage
                        resToDispatch = capturedTotalResult
                        capturedRawImage = null
                        capturedTotalResult = null
                    }
                }
                if (imgToDispatch != null && resToDispatch != null) {
                    onRawCaptured?.invoke(imgToDispatch!!, resToDispatch!!)
                }
            }

            if (rawReader != null && onRawCaptured != null) {
                rawReader.setOnImageAvailableListener({ ir ->
                    val img = try {
                        ir.acquireNextImage()
                    } catch (e: Exception) {
                        null
                    }
                    if (img != null) {
                        rawReader.setOnImageAvailableListener(null, null)
                        synchronized(rawLock) {
                            capturedRawImage = img
                        }
                        tryDispatchRaw()
                    }
                }, cameraHandler)
            }

            try {
                val stillBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                    addTarget(reader.surface)
                    rawReader?.let { addTarget(it.surface) }
                    applyPreviewSettings(this)
                    if (isSimpleHdrEnabled) {
                        set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_HIGH_QUALITY)
                    }
                    set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)
                    set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY)
                    set(CaptureRequest.JPEG_QUALITY, 95.toByte())
                }
                session.capture(stillBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult
                    ) {
                        super.onCaptureCompleted(session, request, result)
                        val iso = result.get(CaptureResult.SENSOR_SENSITIVITY)
                        val expTime = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
                        val expTimeFormatted = if (expTime != null && expTime > 0) "1/${(1_000_000_000.0 / expTime).toInt()}s" else "N/A"
                        lastTelemetry = CaptureSaver.Telemetry(iso = iso ?: 0, expTimeFormatted = expTimeFormatted)
                        Log.i(TAG, "STILL CAPTURE SENSOR METRICS: ISO=$iso, ExpTime=${expTime}ns ($expTimeFormatted)")

                        if (rawReader != null && onRawCaptured != null) {
                            synchronized(rawLock) {
                                capturedTotalResult = result
                            }
                            tryDispatchRaw()
                        }
                    }
                }, cameraHandler)
            } catch (e: Exception) {
                Log.e(TAG, "captureSingleStill request error", e)
                onBitmapCaptured(null)
            }
        }
    }

    /**
     * Phase 2: GCam-style Burst Acquisition (N=6 YUV frames)
     */
    fun captureBurst(
        burstCount: Int = 6,
        onBurstAcquired: (List<RawYuvFrame>?, String) -> Unit
    ) {
        val session = captureSession ?: run { onBurstAcquired(null, "Session null"); return }
        val device = cameraDevice ?: run { onBurstAcquired(null, "Device null"); return }
        val reader = captureYuvReader ?: run { onBurstAcquired(null, "YUV reader null"); return }

        // Step 2.1(a): Read latest repeating CaptureResult -> (iso_i, t_i)
        val latestResult = latestCaptureResult.get()
        val isoI = latestResult?.get(CaptureResult.SENSOR_SENSITIVITY) ?: 100
        val tINanos = latestResult?.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: 30_000_000L

        // Step 2.1(b): Motion-priority pin: t = min(t_i, 33ms); iso' = round(iso_i * t_i / t) <= maxAnalogIso
        val tFloorNanos = 33_333_333L // 1/30s
        val tTargetNanos = tINanos.coerceAtMost(tFloorNanos)
        val calculatedIso = (isoI.toDouble() * (tINanos.toDouble() / tTargetNanos.toDouble())).roundToLong().toInt()
        val isoClamped = calculatedIso > maxAnalogIso
        val isoPrime = calculatedIso.coerceIn(50, maxAnalogIso)

        Log.i(TAG, "CAPTURE burst-expo: t_i=${"%.1f".format(tINanos / 1_000_000.0)}ms iso_i=$isoI -> t=${"%.1f".format(tTargetNanos / 1_000_000.0)}ms iso'=$isoPrime clamped=${if (isoClamped) 1 else 0}")

        CoroutineScope(Dispatchers.Default).launch {
            // Step 2.1(c): AF Lock Trigger (timeout 900ms)
            lockAutofocus(session, device)

            // Step 2.1(d): Build N=6 identical CaptureRequests
            val burstRequests = mutableListOf<CaptureRequest>()
            for (i in 0 until burstCount) {
                val req = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                    addTarget(reader.surface)
                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                    set(CaptureRequest.SENSOR_EXPOSURE_TIME, tTargetNanos)
                    set(CaptureRequest.SENSOR_SENSITIVITY, isoPrime)
                    set(CaptureRequest.CONTROL_AWB_LOCK, true)
                    set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_MINIMAL)
                    set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_OFF)
                    set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO)
                    set(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE)
                }.build()
                burstRequests.add(req)
            }

            val frames = mutableListOf<RawYuvFrame>()
            val timestamps = mutableListOf<Long>()
            val exposureTimes = mutableListOf<Long>()
            val isos = mutableListOf<Int>()
            val latch = CountDownLatch(burstCount)
            val burstStartMs = SystemClock.elapsedRealtime()

            reader.setOnImageAvailableListener({ ir ->
                while (true) {
                    val img = try {
                        ir.acquireNextImage()
                    } catch (e: Exception) {
                        null
                    } ?: break

                    try {
                        val rawFrame = RawYuvFrame.fromImage(
                            image = img,
                            timestampNs = img.timestamp,
                            exposureTimeNs = tTargetNanos,
                            iso = isoPrime
                        )
                        synchronized(frames) {
                            frames.add(rawFrame)
                            timestamps.add(img.timestamp)
                            exposureTimes.add(tTargetNanos)
                            isos.add(isoPrime)
                        }
                        latch.countDown()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing burst YUV frame", e)
                    } finally {
                        img.close() // Discipline: close immediately
                    }
                }
            }, cameraHandler)

            // Submit captureBurst
            session.captureBurst(burstRequests, object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    super.onCaptureCompleted(session, request, result)
                }
            }, cameraHandler)

            val finished = latch.await(2000, TimeUnit.MILLISECONDS)
            reader.setOnImageAvailableListener(null, null)
            
            // Cleanly drain any remaining unread frames to keep the buffer pool fresh
            while (true) {
                val leftover = try { reader.acquireNextImage() } catch (e: Exception) { null } ?: break
                leftover.close()
            }

            val burstWindowMs = SystemClock.elapsedRealtime() - burstStartMs

            // Unlock 3A after burst
            unlockAutofocus(session, device)

            val nGot = synchronized(frames) { frames.size }

            // Step 2.1(f): If < 3 frames within 2000ms -> abort burst -> single-frame fallback
            if (nGot < 3) {
                Log.w(TAG, "CAPTURE burst: DEGRADED n=$nGot (< 3 frames within 2000ms, aborting burst)")
                withContext(Dispatchers.Main) {
                    onBurstAcquired(null, "DEGRADED n=$nGot")
                }
                return@launch
            }

            // Calculate timestamp deltas
            val tsDeltas = mutableListOf<String>()
            for (k in 1 until timestamps.size) {
                val deltaMs = (timestamps[k] - timestamps[k - 1]) / 1_000_000.0
                tsDeltas.add("%.1fms".format(deltaMs))
            }

            val expListStr = exposureTimes.joinToString(prefix = "[", postfix = "]") { "1/${(1_000_000_000.0 / it).toInt()}s" }
            val isoListStr = isos.joinToString(prefix = "[", postfix = "]")

            // Step 2.3 Acceptance line
            Log.i(TAG, "CAPTURE burst: req=$burstCount got=$nGot ref=0 winMs=$burstWindowMs expo=$expListStr iso=$isoListStr tsDeltas=[${tsDeltas.joinToString()}]")

            withContext(Dispatchers.Main) {
                onBurstAcquired(frames, "OK")
            }
        }
    }

    private suspend fun lockAutofocus(session: CameraCaptureSession, device: CameraDevice) {
        val preview = previewSurface ?: return
        try {
            val lockReq = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(preview)
                set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
            }
            session.capture(lockReq.build(), null, cameraHandler)
            delay(150) // Wait up to 150ms for lock convergence
        } catch (e: Exception) {
            Log.w(TAG, "AF Lock trigger error: ${e.message}")
        }
    }

    private fun unlockAutofocus(session: CameraCaptureSession, device: CameraDevice) {
        val preview = previewSurface ?: return
        try {
            val unlockReq = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(preview)
                set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL)
            }
            session.capture(unlockReq.build(), null, cameraHandler)
            startRepeatingPreview()
        } catch (e: Exception) {
            Log.w(TAG, "AF Unlock trigger error: ${e.message}")
        }
    }

    private fun decodeImageToUprightBitmap(image: Image): Bitmap {
        val w = image.width
        val h = image.height

        val rawBitmap = when (image.format) {
            ImageFormat.JPEG -> {
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
            ImageFormat.YUV_420_888 -> {
                // Decode YUV_420_888 planes to ARGB_8888 bitmap
                decodeYuv420ToBitmap(image)
            }
            else -> {
                Log.w(TAG, "Unsupported image format: ${image.format}, fallback decode")
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            }
        }

        val rotationDegrees = sensorOrientation
        return if (rotationDegrees != 0 && rawBitmap != null) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            val rotated = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
            if (rotated != rawBitmap) {
                rawBitmap.recycle()
            }
            rotated
        } else {
            rawBitmap ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        }
    }

    private fun decodeYuv420ToBitmap(image: Image): Bitmap {
        val width = image.width
        val height = image.height
        val planes = image.planes

        val yBuf = planes[0].buffer
        val uBuf = planes[1].buffer
        val vBuf = planes[2].buffer

        val yRowStride = planes[0].rowStride
        val uvRowStride = planes[1].rowStride
        val uvPixelStride = planes[1].pixelStride

        val outBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)

        val yRow = ByteArray(width)
        val halfW = width / 2
        val uvRowBytesCount = halfW * uvPixelStride
        val uRowBytes = ByteArray(uvRowBytesCount)
        val vRowBytes = ByteArray(uvRowBytesCount)

        // Precalculated UV offsets per 2-pixel column
        val rOffsets = IntArray(halfW)
        val gOffsets = IntArray(halfW)
        val bOffsets = IntArray(halfW)

        for (y in 0 until height) {
            // Read entire Y row in 1 JNI call
            yBuf.position(y * yRowStride)
            yBuf.get(yRow, 0, width)

            // Update chroma offsets on even rows (YUV 4:2:0 subsampling)
            if ((y and 1) == 0) {
                val uvRowStart = (y shr 1) * uvRowStride
                uBuf.position(uvRowStart)
                uBuf.get(uRowBytes, 0, minOf(uvRowBytesCount, uBuf.remaining()))

                vBuf.position(uvRowStart)
                vBuf.get(vRowBytes, 0, minOf(uvRowBytesCount, vBuf.remaining()))

                for (cx in 0 until halfW) {
                    val uvIdx = cx * uvPixelStride
                    val uVal = (uRowBytes[uvIdx].toInt() and 0xFF) - 128
                    val vVal = (vRowBytes[uvIdx].toInt() and 0xFF) - 128

                    // Fixed-point BT.601 (scaled by 1024, shr 10)
                    rOffsets[cx] = (1436 * vVal) shr 10
                    gOffsets[cx] = (-352 * uVal - 731 * vVal) shr 10
                    bOffsets[cx] = (1815 * uVal) shr 10
                }
            }

            val outOffset = y * width
            for (x in 0 until width) {
                val yVal = yRow[x].toInt() and 0xFF
                val cx = x shr 1
                val r = (yVal + rOffsets[cx]).coerceIn(0, 255)
                val g = (yVal + gOffsets[cx]).coerceIn(0, 255)
                val b = (yVal + bOffsets[cx]).coerceIn(0, 255)

                pixels[outOffset + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        outBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return outBitmap
    }

    fun release() {
        setAeAwbLock(false)
        try {
            captureSession?.close()
            cameraDevice?.close()
            captureYuvReader?.close()
            captureJpegReader?.close()
            captureRawReader?.close()
            previewSurface?.release()
            cameraSessionExecutor.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error during engine release: ${e.message}")
        }
        stopBackgroundThread()
        // Clear pooled YUV buffers on full engine release
        YuvBufferPool.clear()
    }
}
