package com.auroracam.app.ui

import android.graphics.Bitmap
import android.opengl.GLSurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.auroracam.app.camera.CameraController
import com.auroracam.app.capture.CaptureSaver
import com.auroracam.app.capture.DxMetadata
import com.auroracam.app.capture.LutManager
import com.auroracam.app.gl.AuroraRenderer
import com.auroracam.app.gl.GpuTelemetry
import com.auroracam.app.gl.lut.DebugLutGenerator
import com.auroracam.app.ui.theme.AuroraCyan
import com.auroracam.app.ui.theme.DarkBackground
import com.auroracam.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    val lutManager = remember { LutManager(context) }
    val cameraController = remember { CameraController(context, lifecycleOwner) }

    var currentFps by remember { mutableDoubleStateOf(0.0) }
    var isManualExposureEnabled by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    var lastCapturedThumbnail by remember { mutableStateOf<Bitmap?>(null) }
    var captureStatus by remember { mutableStateOf<String?>(null) }

    // Tap-to-Focus & Exposure State
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var evBias by remember { mutableFloatStateOf(0.0f) }

    // Mode & Format State
    var cameraMode by remember { mutableStateOf(CameraMode.STANDARD) }
    var currentFormat by remember { mutableStateOf(FormatMode.RATIO_4_3) }
    var dxStage by remember { mutableStateOf(DxStage.STAGE_1_EMPTY) }
    var blendMode by remember { mutableStateOf(BlendMode.SCREEN) }
    var opacity by remember { mutableFloatStateOf(1.0f) }
    var isFlipped by remember { mutableStateOf(false) }

    // Creative Effects State
    var temporalEchoDecay by remember { mutableFloatStateOf(0.75f) }
    var motionThreshold by remember { mutableFloatStateOf(0.08f) }
    var lightTrailDecay by remember { mutableFloatStateOf(0.94f) }
    var lightTrailBlendMode by remember { mutableIntStateOf(0) }
    var chromaticAberration by remember { mutableFloatStateOf(0.0f) }
    var currentWheelParam by remember { mutableStateOf(WheelParameter.LOOK_INTENSITY) }

    // Signature Look State
    var isLookEnabled by remember { mutableStateOf(true) }
    var lookIntensity by remember { mutableFloatStateOf(1.0f) }
    var lookHalation by remember { mutableFloatStateOf(0.20f) }
    var activeLutName by remember { mutableStateOf(lutManager.activeLutName) }
    var cachedLutsList by remember { mutableStateOf<List<File>>(emptyList()) }
    var isLegacyJpeg by remember { mutableStateOf(cameraController.isLegacyJpegPath()) }
    var isPreviewBufferHd by remember { mutableStateOf(cameraController.isPreviewBufferHd()) }
    var isLookPrecision16f by remember { mutableStateOf(cameraController.isLookPrecision16f()) }
    var isBurstStack by remember { mutableStateOf(cameraController.isBurstStack()) }
    var showGpuOverlay by remember { mutableStateOf(false) }
    var gpuTelemetry by remember { mutableStateOf(GpuTelemetry()) }

    var rendererRef by remember { mutableStateOf<AuroraRenderer?>(null) }

    // SAF Document Launcher for .cube files
    val lutPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val (name, cube) = lutManager.importAndSelectCubeUri(uri)
                    activeLutName = name
                    rendererRef?.updateLutCube(cube)
                    cachedLutsList = lutManager.listCachedLuts()
                } catch (e: Exception) {
                    android.util.Log.e("CameraScreen", "Failed to import .cube file", e)
                }
            }
        }
    }

    // Load initial LUT & auto-generate test/debug LUTs on startup
    LaunchedEffect(Unit) {
        val (name, cube) = lutManager.loadInitialLut()
        activeLutName = name
        rendererRef?.updateLutCube(cube)
        DebugLutGenerator.generateDebugCubes(context)
        cachedLutsList = lutManager.listCachedLuts()
    }

    DisposableEffect(lifecycleOwner) {
        cameraController.startCamera()
        onDispose {
            cameraController.release()
            rendererRef?.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // 1. OpenGL Viewport with Tap-to-Focus gesture handler
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { tapOffset ->
                        focusPoint = tapOffset
                        val xNorm = (tapOffset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        val yNorm = (tapOffset.y / size.height.toFloat()).coerceIn(0f, 1f)
                        cameraController.triggerTapToFocus(xNorm, yNorm)
                    }
                }
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    GLSurfaceView(ctx).apply {
                        setEGLContextClientVersion(3)
                        val renderer = AuroraRenderer(
                            glSurfaceView = this,
                            onSurfaceReady = { st -> cameraController.onSurfaceTextureReady(st) },
                            onFpsUpdated = { fps -> currentFps = fps },
                            onDxStageChanged = { stage ->
                                dxStage = stage
                                cameraController.setAeAwbLock(stage == DxStage.STAGE_2_LOCKED)
                            }
                        )
                        renderer.isLookEnabled = isLookEnabled
                        renderer.lookIntensity = lookIntensity
                        renderer.lookHalation = lookHalation
                        renderer.temporalEchoDecay = temporalEchoDecay
                        renderer.motionThreshold = motionThreshold
                        renderer.lightTrailDecay = lightTrailDecay
                        renderer.lightTrailBlendMode = lightTrailBlendMode
                        renderer.chromaticAberrationIntensity = chromaticAberration
                        renderer.onGpuTelemetryUpdated = { stats -> gpuTelemetry = stats }
                        rendererRef = renderer
                        setRenderer(renderer)
                        renderer.setLookPrecision16f(isLookPrecision16f)
                        renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                    }
                }
            )
        }

        // 2. Format Framing Guides Overlay (Compose overlay only — not baked into capture)
        FormatOverlay(formatMode = currentFormat)

        // 3. Interactive Focus Ring + Exposure Slider
        FocusRing(
            focusPoint = focusPoint,
            evBias = evBias,
            onEvBiasChanged = { ev ->
                evBias = ev
                cameraController.setExposureCompensation(ev)
            },
            onDismiss = { focusPoint = null }
        )

        // 4. Ultra-Minimal Top HUD Bar
        CameraTopBar(
            currentFps = currentFps,
            cameraMode = cameraMode,
            dxStage = dxStage,
            currentFormat = currentFormat,
            onFormatClicked = {
                currentFormat = when (currentFormat) {
                    FormatMode.RATIO_4_3 -> FormatMode.RATIO_1_1
                    FormatMode.RATIO_1_1 -> FormatMode.XPAN
                    FormatMode.XPAN -> FormatMode.RATIO_4_3
                }
            },
            isManualExposureEnabled = isManualExposureEnabled,
            onManualExposureToggled = {
                isManualExposureEnabled = !isManualExposureEnabled
                cameraController.setManualExposure(isManualExposureEnabled)
            }
        )

        // 5. Developer GPU Profiling Overlay HUD
        if (showGpuOverlay) {
            GpuTelemetryOverlay(
                telemetry = gpuTelemetry,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 48.dp, start = 12.dp)
            )
        }

        // 6. Bottom Controls Stack: Mode Selector -> Shutter Bar -> Options Shelf Below Shutter
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Mode Selector Ribbon (Compact pills right above shutter)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x88000000))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CameraMode.values().forEach { mode ->
                    val isSelected = cameraMode == mode
                    Text(
                        text = if (mode == CameraMode.STANDARD && isBurstStack) "QUICKSTACK" else mode.title.uppercase(),
                        color = if (isSelected) AuroraCyan else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) Color(0x3300E5FF) else Color.Transparent)
                            .clickable {
                                cameraMode = mode
                                rendererRef?.currentMode = mode
                                if (mode == CameraMode.LIGHT_TRAILS) {
                                    rendererRef?.clearLightTrails()
                                }
                                if (mode == CameraMode.STANDARD) {
                                    rendererRef?.retakeFirstExposure()
                                    cameraController.setAeAwbLock(false)
                                    evBias = 0.0f
                                    cameraController.setExposureCompensation(0f)
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Shutter Bar (Gallery Thumbnail | Big Shutter | Quick Look Toggle)
            CameraShutterBar(
                isCapturing = isCapturing,
                lastCapturedThumbnail = lastCapturedThumbnail,
                activeLookName = activeLutName,
                isLookEnabled = isLookEnabled,
                onLookQuickToggle = {
                    isLookEnabled = !isLookEnabled
                    rendererRef?.isLookEnabled = isLookEnabled
                },
                statusText = captureStatus,
                onShutterClicked = {
                    if (isCapturing) return@CameraShutterBar

                    when (cameraMode) {
                        CameraMode.STANDARD -> {
                            isCapturing = true
                            if (isBurstStack) {
                                captureStatus = "Stacking N=6..."
                                cameraController.takeBurst(6) { frames, status ->
                                    if (frames == null || frames.isEmpty()) {
                                        android.util.Log.e("CameraScreen", "Burst capture failed or empty: $status")
                                        isCapturing = false
                                        captureStatus = null
                                        return@takeBurst
                                    }
                                    coroutineScope.launch(Dispatchers.Default) {
                                        val aligned = com.auroracam.app.camera.burst.BurstAligner.alignBurst(frames)
                                        val frameW = frames[0].width
                                        val frameH = frames[0].height
                                        withContext(Dispatchers.Main) {
                                            captureStatus = "Merging & Grading..."
                                            rendererRef?.renderBurstMergeAndGrade(aligned, frameW, frameH) { mergedBitmap ->
                                                coroutineScope.launch(Dispatchers.IO) {
                                                    val cropped = currentFormat.cropBitmap(mergedBitmap)
                                                    val fileName = CaptureSaver.generateCaptureFileName(
                                                        path = "STD",
                                                        isLegacy = isLegacyJpeg,
                                                        lookName = activeLutName,
                                                        isLookEnabled = isLookEnabled,
                                                        intensity = lookIntensity,
                                                        encOverride = "STK"
                                                    )
                                                    val uri = CaptureSaver.saveBitmap(
                                                        context = context,
                                                        bitmap = cropped,
                                                        fileName = fileName,
                                                        quality = 97,
                                                        telemetry = cameraController.lastTelemetry
                                                    )
                                                    if (cropped != mergedBitmap) {
                                                        cropped.recycle()
                                                    }
                                                    mergedBitmap.recycle()
                                                    val thumb = uri?.let { CaptureSaver.loadThumbnail(context, it) }
                                                    withContext(Dispatchers.Main) {
                                                        lastCapturedThumbnail = thumb
                                                        isCapturing = false
                                                        captureStatus = null
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                captureStatus = "Capturing..."
                                cameraController.takePictureBitmap { rawBmp ->
                                    if (rawBmp != null) {
                                        captureStatus = "Grading..."
                                        rendererRef?.renderGradedStill(rawBmp) { gradedBmp ->
                                            coroutineScope.launch(Dispatchers.IO) {
                                                val cropped = currentFormat.cropBitmap(gradedBmp)
                                                val fileName = CaptureSaver.generateCaptureFileName(
                                                    path = "STD",
                                                    isLegacy = isLegacyJpeg,
                                                    lookName = activeLutName,
                                                    isLookEnabled = isLookEnabled,
                                                    intensity = lookIntensity
                                                )
                                                val uri = CaptureSaver.saveBitmap(
                                                    context = context,
                                                    bitmap = cropped,
                                                    fileName = fileName,
                                                    quality = 97,
                                                    telemetry = cameraController.lastTelemetry
                                                )
                                                if (cropped != gradedBmp) {
                                                    cropped.recycle()
                                                }
                                                gradedBmp.recycle()
                                                val thumb = uri?.let { CaptureSaver.loadThumbnail(context, it) }
                                                withContext(Dispatchers.Main) {
                                                    lastCapturedThumbnail = thumb
                                                    isCapturing = false
                                                    captureStatus = null
                                                }
                                            }
                                        }
                                    } else {
                                        isCapturing = false
                                        captureStatus = null
                                    }
                                }
                            }
                        }
                        CameraMode.DOUBLE_EXPOSURE -> {
                            when (dxStage) {
                                DxStage.STAGE_1_EMPTY -> {
                                    cameraController.setAeAwbLock(true)
                                    rendererRef?.captureFirstExposure()
                                }
                                DxStage.STAGE_2_LOCKED -> {
                                    isCapturing = true
                                    captureStatus = "Blending Frame 2..."
                                    cameraController.takePictureBitmap { secondBitmap ->
                                        if (secondBitmap == null) {
                                            isCapturing = false
                                            captureStatus = null
                                            return@takePictureBitmap
                                        }
                                        rendererRef?.renderCompositeStill(secondBitmap) { firstBmp, secondBmp, compositeBmp ->
                                            coroutineScope.launch(Dispatchers.IO) {
                                                val baseName = CaptureSaver.generateProvenanceBaseName(
                                                    path = "DX",
                                                    isLegacy = isLegacyJpeg,
                                                    lookName = activeLutName,
                                                    isLookEnabled = isLookEnabled,
                                                    intensity = lookIntensity
                                                )
                                                val firstFile = "${baseName}_first.jpg"
                                                val secondFile = "${baseName}_second.jpg"
                                                val compFile = "${baseName}.jpg"

                                                val croppedFirst = currentFormat.cropBitmap(firstBmp)
                                                val croppedSecond = currentFormat.cropBitmap(secondBmp)
                                                val croppedComp = currentFormat.cropBitmap(compositeBmp)

                                                val telemetry = cameraController.lastTelemetry
                                                CaptureSaver.saveBitmap(context, croppedFirst, firstFile, quality = 97, telemetry = telemetry)
                                                CaptureSaver.saveBitmap(context, croppedSecond, secondFile, quality = 97, telemetry = telemetry)
                                                val compUri = CaptureSaver.saveBitmap(context, croppedComp, compFile, quality = 97, telemetry = telemetry)

                                                if (croppedFirst != firstBmp) croppedFirst.recycle()
                                                if (croppedSecond != secondBmp) croppedSecond.recycle()
                                                if (croppedComp != compositeBmp) croppedComp.recycle()
                                                firstBmp.recycle()
                                                secondBmp.recycle()
                                                compositeBmp.recycle()

                                                DxMetadata.save(
                                                    context,
                                                    DxMetadata(
                                                        firstFileName = firstFile,
                                                        secondFileName = secondFile,
                                                        compositeFileName = compFile,
                                                        blendMode = blendMode.modeId,
                                                        opacity = opacity,
                                                        flipFirst = isFlipped
                                                    )
                                                )

                                                cameraController.setAeAwbLock(false)
                                                evBias = 0.0f
                                                cameraController.setExposureCompensation(0f)

                                                val thumb = compUri?.let { CaptureSaver.loadThumbnail(context, it) }
                                                withContext(Dispatchers.Main) {
                                                    lastCapturedThumbnail = thumb
                                                    isCapturing = false
                                                    captureStatus = null
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        CameraMode.TEMPORAL_ECHO, CameraMode.MOTION_EXPOSURE, CameraMode.LIGHT_TRAILS -> {
                            isCapturing = true
                            captureStatus = "Capturing high-res frame..."
                            cameraController.takePictureBitmap { highResBmp ->
                                if (highResBmp != null && rendererRef != null) {
                                    rendererRef?.renderTemporalCreativeStill(highResBmp, cameraMode) { finalBmp ->
                                        coroutineScope.launch(Dispatchers.IO) {
                                            val cropped = currentFormat.cropBitmap(finalBmp)
                                            val prefix = when (cameraMode) {
                                                CameraMode.TEMPORAL_ECHO -> "ECHO"
                                                CameraMode.MOTION_EXPOSURE -> "MOT"
                                                CameraMode.LIGHT_TRAILS -> "TRAIL"
                                                else -> "FX"
                                            }
                                            val fileName = CaptureSaver.generateCaptureFileName(
                                                path = prefix,
                                                isLegacy = isLegacyJpeg,
                                                lookName = activeLutName,
                                                isLookEnabled = isLookEnabled,
                                                intensity = lookIntensity
                                            )
                                            val uri = CaptureSaver.saveBitmap(
                                                context = context,
                                                bitmap = cropped,
                                                fileName = fileName,
                                                quality = 97,
                                                telemetry = cameraController.lastTelemetry
                                            )
                                            if (cropped != finalBmp) {
                                                cropped.recycle()
                                            }
                                            finalBmp.recycle()
                                            val thumb = uri?.let { CaptureSaver.loadThumbnail(context, it) }
                                            withContext(Dispatchers.Main) {
                                                lastCapturedThumbnail = thumb
                                                isCapturing = false
                                                captureStatus = null
                                            }
                                        }
                                    }
                                } else {
                                    // Fallback to preview FBO capture if takePictureBitmap unavailable
                                    rendererRef?.captureLiveSceneStill { liveBmp ->
                                        coroutineScope.launch(Dispatchers.IO) {
                                            val cropped = currentFormat.cropBitmap(liveBmp)
                                            val prefix = when (cameraMode) {
                                                CameraMode.TEMPORAL_ECHO -> "ECHO"
                                                CameraMode.MOTION_EXPOSURE -> "MOT"
                                                CameraMode.LIGHT_TRAILS -> "TRAIL"
                                                else -> "FX"
                                            }
                                            val fileName = CaptureSaver.generateCaptureFileName(
                                                path = prefix,
                                                isLegacy = isLegacyJpeg,
                                                lookName = activeLutName,
                                                isLookEnabled = isLookEnabled,
                                                intensity = lookIntensity
                                            )
                                            val uri = CaptureSaver.saveBitmap(
                                                context = context,
                                                bitmap = cropped,
                                                fileName = fileName,
                                                quality = 97,
                                                telemetry = cameraController.lastTelemetry
                                            )
                                            if (cropped != liveBmp) {
                                                cropped.recycle()
                                            }
                                            liveBmp.recycle()
                                            val thumb = uri?.let { CaptureSaver.loadThumbnail(context, it) }
                                            withContext(Dispatchers.Main) {
                                                lastCapturedThumbnail = thumb
                                                isCapturing = false
                                                captureStatus = null
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 7. Options Shelf BELOW the Shutter Button
            BottomControlShelf(
                cameraMode = cameraMode,
                isLookEnabled = isLookEnabled,
                onLookEnabledChanged = { enabled ->
                    isLookEnabled = enabled
                    rendererRef?.isLookEnabled = enabled
                },
                activeLutName = activeLutName,
                onSelectPreset = { preset ->
                    val (name, cube) = lutManager.selectPreset(preset)
                    activeLutName = name
                    rendererRef?.updateLutCube(cube)
                },
                onPickLutFile = { lutPickerLauncher.launch(arrayOf("*/*")) },
                onGenerateDebugLuts = {
                    coroutineScope.launch {
                        DebugLutGenerator.generateDebugCubes(context)
                        cachedLutsList = lutManager.listCachedLuts()
                    }
                },
                availableLuts = cachedLutsList,
                onSelectCachedLut = { file ->
                    coroutineScope.launch {
                        val (name, cube) = lutManager.selectCachedLut(file)
                        activeLutName = name
                        rendererRef?.updateLutCube(cube)
                    }
                },
                onResetToDefaultLut = {
                    val (name, cube) = lutManager.resetToDefault()
                    activeLutName = name
                    rendererRef?.updateLutCube(cube)
                },
                lookIntensity = lookIntensity,
                onLookIntensityChanged = { intensity ->
                    lookIntensity = intensity
                    rendererRef?.lookIntensity = intensity
                },
                lookHalation = lookHalation,
                onLookHalationChanged = { halo ->
                    lookHalation = halo
                    rendererRef?.lookHalation = halo
                },
                temporalEchoDecay = temporalEchoDecay,
                onTemporalEchoDecayChanged = { decay ->
                    temporalEchoDecay = decay
                    rendererRef?.temporalEchoDecay = decay
                },
                motionThreshold = motionThreshold,
                onMotionThresholdChanged = { thresh ->
                    motionThreshold = thresh
                    rendererRef?.motionThreshold = thresh
                },
                lightTrailDecay = lightTrailDecay,
                onLightTrailDecayChanged = { decay ->
                    lightTrailDecay = decay
                    rendererRef?.lightTrailDecay = decay
                },
                lightTrailBlendMode = lightTrailBlendMode,
                onLightTrailBlendModeChanged = { mode ->
                    lightTrailBlendMode = mode
                    rendererRef?.lightTrailBlendMode = mode
                },
                chromaticAberration = chromaticAberration,
                onChromaticAberrationChanged = { ca ->
                    chromaticAberration = ca
                    rendererRef?.chromaticAberrationIntensity = ca
                },
                onClearLightTrails = {
                    rendererRef?.clearLightTrails()
                },
                currentWheelParam = currentWheelParam,
                onSelectWheelParam = { param ->
                    currentWheelParam = param
                },
                evBias = evBias,
                onEvBiasChanged = { ev ->
                    evBias = ev
                    cameraController.setExposureCompensation(ev)
                },
                currentFormat = currentFormat,
                onFormatChanged = { format -> currentFormat = format },
                isBurstStack = isBurstStack,
                onBurstStackToggled = {
                    val next = !isBurstStack
                    isBurstStack = next
                    cameraController.setBurstStack(next)
                },
                isLegacyJpeg = isLegacyJpeg,
                onLegacyJpegToggled = {
                    val next = !isLegacyJpeg
                    isLegacyJpeg = next
                    cameraController.setLegacyJpegPath(next)
                },
                isLookPrecision16f = isLookPrecision16f,
                onLookPrecision16fToggled = {
                    val next = !isLookPrecision16f
                    isLookPrecision16f = next
                    cameraController.setLookPrecision16f(next)
                    rendererRef?.setLookPrecision16f(next)
                },
                isPreviewBufferHd = isPreviewBufferHd,
                onPreviewBufferHdToggled = {
                    val next = !isPreviewBufferHd
                    isPreviewBufferHd = next
                    cameraController.setPreviewBufferHd(next)
                },
                showGpuOverlay = showGpuOverlay,
                onShowGpuOverlayToggled = {
                    showGpuOverlay = !showGpuOverlay
                },
                dxStage = dxStage,
                dxBlendMode = blendMode,
                onBlendModeSelected = { mode ->
                    blendMode = mode
                    rendererRef?.dxBlendMode = mode.modeId
                },
                dxOpacity = opacity,
                onOpacityChanged = { op ->
                    opacity = op
                    rendererRef?.dxOpacity = op
                },
                dxIsFlipped = isFlipped,
                onFlipToggled = {
                    isFlipped = !isFlipped
                    rendererRef?.dxFlipFirst = isFlipped
                },
                onRetakeClicked = {
                    rendererRef?.retakeFirstExposure()
                    cameraController.setAeAwbLock(false)
                    evBias = 0.0f
                    cameraController.setExposureCompensation(0f)
                }
            )
        }
    }
}

/**
 * Developer GPU Profiling HUD Overlay.
 */
@Composable
fun GpuTelemetryOverlay(
    telemetry: GpuTelemetry,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xCC0A0A0A),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0x3300E5FF)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(
                text = "⚡ GPU PROFILER",
                color = AuroraCyan,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "FPS: ${"%.1f".format(telemetry.fps)} (${"%.1f".format(telemetry.frameTimeMs)}ms)",
                color = if (telemetry.fps >= 55f) Color(0xFF69F0AE) else Color(0xFFFFD54F),
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "GPU FX: ${"%.1f".format(telemetry.gpuEffectsTimeMs)}ms",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "RES: ${telemetry.previewWidth}x${telemetry.previewHeight}",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "FBO: ${if (telemetry.isHalfFloat) "16-Bit Float" else "8-Bit RGBA"} | Buffers: ${telemetry.historyBufferCount}",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
