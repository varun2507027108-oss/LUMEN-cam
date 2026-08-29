package com.auroracam.app.camera

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.util.Log
import android.util.Size
import android.view.Surface

object CameraProbe {
    private const val TAG = "CameraProbe"

    // Shared, reused executor for session-configuration support checks. Avoids
    // leaking a new thread pool on every probeCameraCapabilities /
    // checkSessionConfigurationSupport call.
    private val probeExecutor: java.util.concurrent.ExecutorService =
        java.util.concurrent.Executors.newSingleThreadExecutor()

    data class ProbeResult(
        val backCameraId: String,
        val availableYuvSizes: List<Size>,
        val isPrivYuvSupported: Boolean,
        val isPrivYuvJpegSupported: Boolean,
        val resolvedYuvSize: Size?,
        val maxAnalogSensitivity: Int
    )

    /**
     * Executes Phase 0.1 and 0.2 probes synchronously before opening camera.
     */
    fun probeCameraCapabilities(context: Context, cameraManager: CameraManager): ProbeResult {
        Log.i(TAG, "================ PHASE 0 — CAMERA PROBES BEGIN ================")

        val backCameraId = cameraManager.cameraIdList.firstOrNull { id ->
            val chars = cameraManager.getCameraCharacteristics(id)
            chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: cameraManager.cameraIdList.firstOrNull() ?: "0"

        val characteristics = cameraManager.getCameraCharacteristics(backCameraId)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

        // 0.1: Log all StreamConfigurationMap YUV_420_888 output sizes (back cam)
        val yuvSizes: Array<Size> = map?.getOutputSizes(ImageFormat.YUV_420_888) ?: emptyArray()
        Log.i(TAG, "PROBE 0.1: Back camera ($backCameraId) YUV_420_888 output sizes (${yuvSizes.size}):")
        yuvSizes.forEachIndexed { index, size ->
            val is43 = size.width * 3 == size.height * 4 || size.width * 4 == size.height * 3
            Log.i(TAG, "  [$index] ${size.width}x${size.height} (4:3=$is43, MP=${"%.2f".format(size.width * size.height / 1_000_000.0)})")
        }

        val maxAnalogIso = characteristics.get(CameraCharacteristics.SENSOR_MAX_ANALOG_SENSITIVITY) ?: 800
        Log.i(TAG, "PROBE 0.1: SENSOR_MAX_ANALOG_SENSITIVITY = $maxAnalogIso")

        val privSizes = map?.getOutputSizes(SurfaceHolderSurface::class.java)
            ?: map?.getOutputSizes(android.graphics.SurfaceTexture::class.java)
            ?: emptyArray()
        Log.i(TAG, "PROBE 0.1: SurfaceTexture PRIV output sizes (${privSizes.size}): ${privSizes.take(5).joinToString { "${it.width}x${it.height}" }}")

        // 0.2: Stream combination fallback ladder
        val candidateYuvSizes = listOf(
            Size(3200, 2400),
            Size(2400, 1800),
            Size(1920, 1440)
        )
        val supportedCandidate = candidateYuvSizes.firstOrNull { cand ->
            yuvSizes.any { it.width == cand.width && it.height == cand.height }
        }

        val resolvedYuvSize = supportedCandidate ?: yuvSizes.maxByOrNull { it.width * it.height }
        Log.i(TAG, "PROBE 0.2: Resolved capture YUV size candidate = ${resolvedYuvSize?.width}x${resolvedYuvSize?.height}")

        // isPrivYuvSupported / isPrivYuvJpegSupported are placeholders here — the
        // authoritative check happens in checkSessionConfigurationSupport() once a
        // CameraDevice + real Surfaces exist. This static probe can only confirm the
        // format appears in the StreamConfigurationMap, not that a live session
        // combining preview+YUV(+JPEG) will actually be accepted by the device.
        val privYuvAppearsSupported = yuvSizes.isNotEmpty()
        Log.i(TAG, "================ PHASE 0.1/0.2 PROBES COMPLETE ================")
        return ProbeResult(
            backCameraId = backCameraId,
            availableYuvSizes = yuvSizes.toList(),
            isPrivYuvSupported = privYuvAppearsSupported,
            isPrivYuvJpegSupported = privYuvAppearsSupported,
            resolvedYuvSize = resolvedYuvSize,
            maxAnalogSensitivity = maxAnalogIso
        )
    }

    /**
     * Executes Phase 0.2 stream-combination check on device if API 29+ supported
     */
    fun checkSessionConfigurationSupport(
        device: CameraDevice,
        previewSurface: Surface,
        yuvSurface: Surface,
        jpegSurface: Surface?
    ): Pair<Boolean, Boolean> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val outputConfigsA = listOf(
                    OutputConfiguration(previewSurface),
                    OutputConfiguration(yuvSurface)
                )
                val sessionConfigA = SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    outputConfigsA,
                    probeExecutor,
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {}
                        override fun onConfigureFailed(session: CameraCaptureSession) {}
                    }
                )
                val isSuppA = device.isSessionConfigurationSupported(sessionConfigA)
                Log.i(TAG, "PROBE 0.2(a): isSessionConfigurationSupported(PRIV 1920x1440 + YUV capture) = $isSuppA")

                var isSuppB = false
                if (jpegSurface != null) {
                    val outputConfigsB = listOf(
                        OutputConfiguration(previewSurface),
                        OutputConfiguration(yuvSurface),
                        OutputConfiguration(jpegSurface)
                    )
                    val sessionConfigB = SessionConfiguration(
                        SessionConfiguration.SESSION_REGULAR,
                        outputConfigsB,
                        probeExecutor,
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {}
                            override fun onConfigureFailed(session: CameraCaptureSession) {}
                        }
                    )
                    isSuppB = device.isSessionConfigurationSupported(sessionConfigB)
                    Log.i(TAG, "PROBE 0.2(b): isSessionConfigurationSupported(PRIV 1920x1440 + YUV + JPEG) = $isSuppB")
                }
                return Pair(isSuppA, isSuppB)
            } catch (e: Exception) {
                Log.w(TAG, "PROBE 0.2: isSessionConfigurationSupported check threw exception: ${e.message}")
            }
        }
        return Pair(true, true)
    }

    /**
     * Executes Phase 0.3 Key round-trip probe
     */
    fun executeRoundTripProbe(
        session: CameraCaptureSession,
        yuvTarget: Surface,
        handler: Handler,
        testShutterNanos: Long = 20_000_000L, // 1/50s = 20ms
        testIso: Int = 100
    ) {
        try {
            val device = session.device
            val reqBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(yuvTarget)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                set(CaptureRequest.SENSOR_EXPOSURE_TIME, testShutterNanos)
                set(CaptureRequest.SENSOR_SENSITIVITY, testIso)
                set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_MINIMAL)
                set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_OFF)
                set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO)
            }

            Log.i(TAG, "PROBE 0.3: Submitting Key Round-Trip Probe Request (t=${testShutterNanos}ns, iso=$testIso, NR=MINIMAL, EDGE=OFF, ANTIBANDING=AUTO)...")

            session.capture(reqBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    super.onCaptureCompleted(session, request, result)
                    val resultAeMode = result.get(CaptureResult.CONTROL_AE_MODE)
                    val resultExpTime = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
                    val resultIso = result.get(CaptureResult.SENSOR_SENSITIVITY)
                    val resultNrMode = result.get(CaptureResult.NOISE_REDUCTION_MODE)
                    val resultEdgeMode = result.get(CaptureResult.EDGE_MODE)
                    val resultAntibanding = result.get(CaptureResult.CONTROL_AE_ANTIBANDING_MODE)

                    Log.i(TAG, "================ PROBE 0.3 RESULT ECHO ================")
                    Log.i(TAG, "  Requested AE_MODE=OFF                -> Echo Result: $resultAeMode")
                    Log.i(TAG, "  Requested SENSOR_EXPOSURE_TIME=$testShutterNanos -> Echo Result: $resultExpTime ns (${if (resultExpTime != null) "1/${(1_000_000_000.0 / resultExpTime).toInt()}s" else "null"})")
                    Log.i(TAG, "  Requested SENSOR_SENSITIVITY=$testIso  -> Echo Result: $resultIso")
                    Log.i(TAG, "  Requested NOISE_REDUCTION=MINIMAL(3) -> Echo Result: $resultNrMode")
                    Log.i(TAG, "  Requested EDGE_MODE=OFF(0)           -> Echo Result: $resultEdgeMode")
                    Log.i(TAG, "  Requested ANTIBANDING=AUTO(3)        -> Echo Result: $resultAntibanding")
                    Log.i(TAG, "=========================================================")
                }
            }, handler)
        } catch (e: Exception) {
            Log.e(TAG, "PROBE 0.3 failed to submit key round-trip probe request", e)
        }
    }

    private class SurfaceHolderSurface
}