package com.auroracam.app.ui

import android.graphics.Bitmap
import android.net.Uri
import android.opengl.GLSurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.content.Intent
import com.auroracam.app.camera.FlashMode
import com.auroracam.app.camera.CameraController
import com.auroracam.app.capture.CaptureSaver
import com.auroracam.app.capture.DxMetadata
import com.auroracam.app.capture.LookActivationResult
import com.auroracam.app.capture.LookUniforms
import com.auroracam.app.capture.LutManager
import com.auroracam.app.gl.AuroraRenderer
import com.auroracam.app.gl.GpuTelemetry
import com.auroracam.app.gl.lut.DebugLutGenerator
import com.auroracam.app.ui.theme.AmberGold
import com.auroracam.app.ui.theme.AuroraCyan
import com.auroracam.app.ui.theme.DarkBackground
import com.auroracam.app.ui.theme.SlateBorder
import com.auroracam.app.ui.theme.StatusGreen
import com.auroracam.app.ui.theme.StatusRed
import com.auroracam.app.ui.theme.TextSecondary
import com.auroracam.app.ui.theme.WarmAmber
import com.auroracam.app.ui.theme.White
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    var lastCapturedUri by remember { mutableStateOf<Uri?>(null) }
    var isPhotoViewerOpen by remember { mutableStateOf(false) }
    var captureStatus by remember { mutableStateOf<String?>(null) }

    // Tap-to-Focus & Exposure State
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var evBias by remember { mutableFloatStateOf(0.0f) }
    var isAeAfLocked by remember { mutableStateOf(false) }
    var showEvBadge by remember { mutableStateOf(false) }
    var evBadgeJob by remember { mutableStateOf<Job?>(null) }

    // Flash & RAW Capture State
    var flashMode: FlashMode by remember { mutableStateOf(cameraController.getFlashMode()) }
    var isRawCaptureEnabled by remember { mutableStateOf(cameraController.isRawCaptureEnabled()) }

    // Manual Focus & Focus Peaking State
    var isManualFocus by remember { mutableStateOf(false) }
    var focusDistanceDiopters by remember { mutableFloatStateOf(0.0f) }
    var isFocusPeakingEnabled by remember { mutableStateOf(false) }
    var focusPeakingSensitivity by remember { mutableFloatStateOf(0.20f) }
    var focusPeakingColorIndex by remember { mutableIntStateOf(0) }

    // Mode & Drawer State
    var cameraMode by remember { mutableStateOf(CameraMode.STANDARD) }
    var isDrawerOpen by remember { mutableStateOf(false) }
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
    var lookGrain by remember { mutableFloatStateOf(0.04f) }
    var lookVignette by remember { mutableFloatStateOf(0.12f) }
    var activeLutName by remember { mutableStateOf(lutManager.activeLutName) }
    var cachedLutsList by remember { mutableStateOf<List<File>>(emptyList()) }
    var isLegacyJpeg by remember { mutableStateOf(cameraController.isLegacyJpegPath()) }
    var isPreviewBufferHd by remember { mutableStateOf(cameraController.isPreviewBufferHd()) }
    var isLookPrecision16f by remember { mutableStateOf(cameraController.isLookPrecision16f()) }
    var isBurstStack by remember { mutableStateOf(cameraController.isBurstStack()) }
    var showGpuOverlay by remember { mutableStateOf(false) }
    var gpuTelemetry by remember { mutableStateOf(GpuTelemetry()) }

    var rendererRef by remember { mutableStateOf<AuroraRenderer?>(null) }
    var glSurfaceViewRef by remember { mutableStateOf<GLSurfaceView?>(null) }

    // Helper to atomically apply 3D LUT texture and its companion optical uniforms
    val applyLookActivation: (com.auroracam.app.capture.LookActivationResult) -> Unit = { res ->
        activeLutName = res.name
        lookIntensity = res.uniforms.intensity
        lookHalation = res.uniforms.halation
        lookGrain = res.uniforms.grain
        lookVignette = res.uniforms.vignette
        chromaticAberration = res.uniforms.chromaticAberration
        rendererRef?.updateLutCube(res.cube)
        rendererRef?.updateLookUniforms(
            intensity = res.uniforms.intensity,
            halation = res.uniforms.halation,
            grain = res.uniforms.grain,
            vignette = res.uniforms.vignette,
            chromaticAberration = res.uniforms.chromaticAberration
        )
    }

    // SAF Document Launcher for .cube files
    val lutPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val res = lutManager.importAndSelectCubeUri(uri)
                    applyLookActivation(res)
                    cachedLutsList = lutManager.listCachedLuts()
                } catch (e: Exception) {
                    android.util.Log.e("CameraScreen", "Failed to import .cube file", e)
                }
            }
        }
    }

    // Load initial LUT & auto-generate test/debug LUTs & load latest photo on startup
    LaunchedEffect(Unit) {
        val res = lutManager.loadInitialLut()
        applyLookActivation(res)
        DebugLutGenerator.generateDebugCubes(context)
        cachedLutsList = lutManager.listCachedLuts()

        withContext(Dispatchers.IO) {
            val latestUri = CaptureSaver.getLatestCaptureUri(context)
            if (latestUri != null) {
                val thumb = CaptureSaver.loadThumbnail(context, latestUri)
                withContext(Dispatchers.Main) {
                    lastCapturedUri = latestUri
                    lastCapturedThumbnail = thumb
                }
            }
        }
    }

    // Robust Lifecycle Event Observer to properly close & resume camera and GL rendering
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    glSurfaceViewRef?.onPause()
                    cameraController.onPause()
                }
                Lifecycle.Event.ON_RESUME, Lifecycle.Event.ON_START -> {
                    glSurfaceViewRef?.onResume()
                    cameraController.onResume()
                    coroutineScope.launch(Dispatchers.IO) {
                        val latestUri = CaptureSaver.getLatestCaptureUri(context)
                        val thumb = latestUri?.let { CaptureSaver.loadThumbnail(context, it) }
                        withContext(Dispatchers.Main) {
                            lastCapturedUri = latestUri
                            lastCapturedThumbnail = thumb
                        }
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            cameraController.release()
            rendererRef?.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // 1. OpenGL Viewport with Tap-to-Focus, Long-Press AE/AF Lock, and Vertical Drag EV Exposure Gesture
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { tapOffset ->
                            focusPoint = tapOffset
                            isAeAfLocked = false
                            cameraController.setAeAwbLock(false)
                            val xNorm = (tapOffset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            val yNorm = (tapOffset.y / size.height.toFloat()).coerceIn(0f, 1f)
                            cameraController.triggerTapToFocus(xNorm, yNorm)
                        },
                        onLongPress = { tapOffset ->
                            focusPoint = tapOffset
                            isAeAfLocked = true
                            cameraController.setAeAwbLock(true)
                            val xNorm = (tapOffset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            val yNorm = (tapOffset.y / size.height.toFloat()).coerceIn(0f, 1f)
                            cameraController.triggerTapToFocus(xNorm, yNorm)
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        val step = -dragAmount * 0.008f
                        evBias = (evBias + step).coerceIn(-2.0f, 2.0f)
                        cameraController.setExposureCompensation(evBias)
                        showEvBadge = true
                        evBadgeJob?.cancel()
                        evBadgeJob = coroutineScope.launch {
                            delay(1500)
                            showEvBadge = false
                        }
                    }
                }
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    GLSurfaceView(ctx).apply {
                        glSurfaceViewRef = this
                        preserveEGLContextOnPause = true
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
                        renderer.lookGrain = lookGrain
                        renderer.lookVignette = lookVignette
                        renderer.temporalEchoDecay = temporalEchoDecay
                        renderer.motionThreshold = motionThreshold
                        renderer.lightTrailDecay = lightTrailDecay
                        renderer.lightTrailBlendMode = lightTrailBlendMode
                        renderer.chromaticAberrationIntensity = chromaticAberration
                        renderer.isFocusPeakingEnabled = isFocusPeakingEnabled
                        renderer.focusPeakingSensitivity = focusPeakingSensitivity
                        renderer.focusPeakingColorIndex = focusPeakingColorIndex
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

        // 3. Interactive Focus Ring + Exposure Slider with AE/AF Lock
        FocusRing(
            focusPoint = focusPoint,
            evBias = evBias,
            onEvBiasChanged = { ev ->
                evBias = ev
                cameraController.setExposureCompensation(ev)
                showEvBadge = true
                evBadgeJob?.cancel()
                evBadgeJob = coroutineScope.launch {
                    delay(1500)
                    showEvBadge = false
                }
            },
            onDismiss = {
                if (!isAeAfLocked) {
                    focusPoint = null
                }
            },
            isLocked = isAeAfLocked,
            onToggleLock = {
                isAeAfLocked = !isAeAfLocked
                cameraController.setAeAwbLock(isAeAfLocked)
            }
        )

        // Floating Center EV Exposure Compensation Badge HUD
        AnimatedVisibility(
            visible = showEvBadge,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xCC111111))
                    .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                val sign = if (evBias > 0.05f) "+" else ""
                Text(
                    text = "EV $sign${"%.2f".format(evBias)}",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (kotlin.math.abs(evBias) < 0.05f) Color.White else WarmAmber
                )
            }
        }

        // 4. Ultra-Minimal Top HUD Bar with Look Quick-Switcher & Diagnostic Toggles
        CameraTopBar(
            currentFps = currentFps,
            flashMode = flashMode,
            onFlashModeToggled = {
                val next = when (flashMode) {
                    FlashMode.OFF -> FlashMode.AUTO
                    FlashMode.AUTO -> FlashMode.ON
                    FlashMode.ON -> FlashMode.TORCH
                    FlashMode.TORCH -> FlashMode.OFF
                }
                flashMode = next
                cameraController.setFlashMode(next)
            },
            isRawSupported = cameraController.isRawSupported,
            isRawEnabled = isRawCaptureEnabled,
            onRawToggled = {
                val next = !isRawCaptureEnabled
                isRawCaptureEnabled = next
                cameraController.setRawCaptureEnabled(next)
            },
            isHdrEnabled = isBurstStack,
            onHdrToggled = {
                val next = !isBurstStack
                isBurstStack = next
                cameraController.setBurstStack(next)
            },
            isFocusPeakingEnabled = isFocusPeakingEnabled,
            onFocusPeakingToggled = {
                val next = !isFocusPeakingEnabled
                isFocusPeakingEnabled = next
                rendererRef?.isFocusPeakingEnabled = next
            },
            activeLutName = activeLutName,
            isLookEnabled = isLookEnabled,
            onSelectPreset = { preset ->
                val res = lutManager.selectPreset(preset)
                applyLookActivation(res)
            },
            onLookEnabledChanged = { enabled ->
                isLookEnabled = enabled
                rendererRef?.isLookEnabled = enabled
            },
            cachedLuts = cachedLutsList,
            onSelectCachedLut = { file ->
                coroutineScope.launch {
                    val res = lutManager.selectCachedLut(file)
                    applyLookActivation(res)
                }
            },
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

        // 6. Bottom Controls: Quick Looks Snapping Dial + Shutter Bar Dock
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Direct-Access "Looks / LUTs" Snapping Dial (Positioned right above the Shutter Dock in standard mode)
            if (!isDrawerOpen && cameraMode == CameraMode.STANDARD) {
                QuickLooksDial(
                    activeLutName = activeLutName,
                    isLookEnabled = isLookEnabled,
                    onSelectPreset = { preset ->
                        val res = lutManager.selectPreset(preset)
                        applyLookActivation(res)
                    },
                    onLookEnabledChanged = { enabled ->
                        isLookEnabled = enabled
                        rendererRef?.isLookEnabled = enabled
                    }
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Shutter Bar (In-App Gallery Thumbnail | Big Mechanical Shutter | Creative Controls Toggle)
            CameraShutterBar(
                isCapturing = isCapturing,
                lastCapturedThumbnail = lastCapturedThumbnail,
                onThumbnailClicked = {
                    try {
                        if (lastCapturedUri != null) {
                            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(lastCapturedUri, "image/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(viewIntent)
                        } else {
                            val galleryIntent = Intent(Intent.ACTION_VIEW).apply {
                                type = "image/*"
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(galleryIntent)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("CameraScreen", "Could not open gallery intent: ${e.message}")
                        if (lastCapturedUri != null) {
                            isPhotoViewerOpen = true
                        }
                    }
                },
                activeLookName = activeLutName,
                isLookEnabled = isLookEnabled,
                cameraMode = cameraMode,
                isDrawerOpen = isDrawerOpen,
                onDrawerToggle = { isDrawerOpen = !isDrawerOpen },
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
                                                        lastCapturedUri = uri
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
                                cameraController.takePictureBitmap(
                                    onBitmapCaptured = { rawBmp ->
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
                                                        lastCapturedUri = uri
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
                                    },
                                    onRawCaptured = { rawImage, captureResult ->
                                        val chars = cameraController.cameraCharacteristics
                                        if (chars != null) {
                                            coroutineScope.launch(Dispatchers.IO) {
                                                val dngFileName = "${CaptureSaver.generateProvenanceBaseName(
                                                    path = "RAW",
                                                    isLegacy = false,
                                                    lookName = activeLutName,
                                                    isLookEnabled = isLookEnabled,
                                                    intensity = lookIntensity
                                                )}.dng"
                                                CaptureSaver.saveDng(
                                                    context = context,
                                                    rawImage = rawImage,
                                                    captureResult = captureResult,
                                                    characteristics = chars,
                                                    fileName = dngFileName,
                                                    telemetry = cameraController.lastTelemetry
                                                )
                                            }
                                        } else {
                                            try {
                                                rawImage.close()
                                            } catch (e: Exception) {
                                                android.util.Log.w("CameraScreen", "Error closing raw image", e)
                                            }
                                        }
                                    }
                                )
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
                                    cameraController.takePictureBitmap(
                                        onBitmapCaptured = { secondBitmap ->
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
                                                    isAeAfLocked = false
                                                    evBias = 0.0f
                                                    cameraController.setExposureCompensation(0f)

                                                    val thumb = compUri?.let { CaptureSaver.loadThumbnail(context, it) }
                                                    withContext(Dispatchers.Main) {
                                                        lastCapturedUri = compUri
                                                        lastCapturedThumbnail = thumb
                                                        isCapturing = false
                                                        captureStatus = null
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        CameraMode.TEMPORAL_ECHO, CameraMode.MOTION_EXPOSURE, CameraMode.LIGHT_TRAILS -> {
                            isCapturing = true
                            captureStatus = "Capturing high-res frame..."
                            cameraController.takePictureBitmap(
                                onBitmapCaptured = { highResBmp ->
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
                                                lastCapturedUri = uri
                                                lastCapturedThumbnail = thumb
                                                isCapturing = false
                                                captureStatus = null
                                            }
                                        }
                                    }
                                } else {
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
                                                lastCapturedUri = uri
                                                lastCapturedThumbnail = thumb
                                                isCapturing = false
                                                captureStatus = null
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        )
    }

        // 7. Modal Creative Controls Drawer with Viewfinder Scrim
        if (isDrawerOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.50f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        isDrawerOpen = false
                    }
            )
        }

        // Consolidated Creative Controls Drawer (Modes, Looks, Smooth Effects, Pro Options)
        AnimatedVisibility(
            visible = isDrawerOpen,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            CreativeDrawer(
                cameraMode = cameraMode,
                onModeChanged = { mode ->
                    cameraMode = mode
                    rendererRef?.currentMode = mode
                    if (mode == CameraMode.LIGHT_TRAILS) {
                        rendererRef?.clearLightTrails()
                    }
                    if (mode == CameraMode.STANDARD) {
                        rendererRef?.retakeFirstExposure()
                        cameraController.setAeAwbLock(false)
                        isAeAfLocked = false
                        evBias = 0.0f
                        cameraController.setExposureCompensation(0f)
                    }
                },
                isLookEnabled = isLookEnabled,
                onLookEnabledChanged = { enabled ->
                    isLookEnabled = enabled
                    rendererRef?.isLookEnabled = enabled
                },
                activeLutName = activeLutName,
                onSelectPreset = { preset ->
                    val res = lutManager.selectPreset(preset)
                    applyLookActivation(res)
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
                        val res = lutManager.selectCachedLut(file)
                        applyLookActivation(res)
                    }
                },
                onResetToDefaultLut = {
                    val res = lutManager.resetToDefault()
                    applyLookActivation(res)
                },
                lookIntensity = lookIntensity,
                onLookIntensityChanged = { intensity ->
                    lookIntensity = intensity
                    rendererRef?.lookIntensity = intensity
                    lutManager.saveUserOverride(
                        activeLutName,
                        LookUniforms(intensity, lookHalation, lookGrain, lookVignette, chromaticAberration)
                    )
                },
                lookHalation = lookHalation,
                onLookHalationChanged = { halo ->
                    lookHalation = halo
                    rendererRef?.lookHalation = halo
                    lutManager.saveUserOverride(
                        activeLutName,
                        LookUniforms(lookIntensity, halo, lookGrain, lookVignette, chromaticAberration)
                    )
                },
                lookGrain = lookGrain,
                onLookGrainChanged = { grain ->
                    lookGrain = grain
                    rendererRef?.lookGrain = grain
                    lutManager.saveUserOverride(
                        activeLutName,
                        LookUniforms(lookIntensity, lookHalation, grain, lookVignette, chromaticAberration)
                    )
                },
                lookVignette = lookVignette,
                onLookVignetteChanged = { vig ->
                    lookVignette = vig
                    rendererRef?.lookVignette = vig
                    lutManager.saveUserOverride(
                        activeLutName,
                        LookUniforms(lookIntensity, lookHalation, lookGrain, vig, chromaticAberration)
                    )
                },
                onResetLookUniforms = {
                    val defUniforms = lutManager.resetUserOverrides(activeLutName)
                    lookIntensity = defUniforms.intensity
                    lookHalation = defUniforms.halation
                    lookGrain = defUniforms.grain
                    lookVignette = defUniforms.vignette
                    chromaticAberration = defUniforms.chromaticAberration
                    rendererRef?.updateLookUniforms(
                        intensity = defUniforms.intensity,
                        halation = defUniforms.halation,
                        grain = defUniforms.grain,
                        vignette = defUniforms.vignette,
                        chromaticAberration = defUniforms.chromaticAberration
                    )
                },
                // Focus & Peaking Controls
                isManualFocus = isManualFocus,
                onManualFocusToggled = { mf ->
                    isManualFocus = mf
                    cameraController.setManualFocus(mf, focusDistanceDiopters)
                },
                focusDistanceDiopters = focusDistanceDiopters,
                onFocusDistanceChanged = { dist ->
                    focusDistanceDiopters = dist
                    if (isManualFocus) {
                        cameraController.setManualFocus(true, dist)
                    }
                },
                isFocusPeakingEnabled = isFocusPeakingEnabled,
                onFocusPeakingToggled = {
                    val next = !isFocusPeakingEnabled
                    isFocusPeakingEnabled = next
                    rendererRef?.isFocusPeakingEnabled = next
                },
                focusPeakingSensitivity = focusPeakingSensitivity,
                onFocusPeakingSensitivityChanged = { s ->
                    focusPeakingSensitivity = s
                    rendererRef?.focusPeakingSensitivity = s
                },
                focusPeakingColorIndex = focusPeakingColorIndex,
                onFocusPeakingColorIndexChanged = { idx ->
                    focusPeakingColorIndex = idx
                    rendererRef?.focusPeakingColorIndex = idx
                },
                // Effects & Creative Controls
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
                    lutManager.saveUserOverride(
                        activeLutName,
                        LookUniforms(lookIntensity, lookHalation, lookGrain, lookVignette, ca)
                    )
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
                isRawSupported = cameraController.isRawSupported,
                isRawEnabled = isRawCaptureEnabled,
                onRawToggled = {
                    val next = !isRawCaptureEnabled
                    isRawCaptureEnabled = next
                    cameraController.setRawCaptureEnabled(next)
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
                onCaptureContactSheet7Up = {
                    isCapturing = true
                    captureStatus = "Rendering 7-Up Contact Sheet..."
                    cameraController.takePictureBitmap(
                        onBitmapCaptured = { sourceBitmap ->
                            if (sourceBitmap != null && rendererRef != null) {
                                rendererRef?.renderContactSheet7Up(sourceBitmap) { sheetBitmap ->
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val baseName = CaptureSaver.generateProvenanceBaseName(
                                            path = "CONTACT_7UP",
                                            isLegacy = isLegacyJpeg,
                                            lookName = "ALL_7_LOOKS",
                                            isLookEnabled = true,
                                            intensity = 1.0f
                                        )
                                        val fileName = "${baseName}.jpg"
                                        val telemetry = cameraController.lastTelemetry
                                        val uri = CaptureSaver.saveBitmap(
                                            context = context,
                                            bitmap = sheetBitmap,
                                            fileName = fileName,
                                            quality = 97,
                                            telemetry = telemetry
                                        )
                                        sheetBitmap.recycle()
                                        val thumb = uri?.let { CaptureSaver.loadThumbnail(context, it) }
                                        withContext(Dispatchers.Main) {
                                            lastCapturedUri = uri
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
                    )
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
                    isAeAfLocked = false
                    evBias = 0.0f
                    cameraController.setExposureCompensation(0f)
                },
                onClose = { isDrawerOpen = false }
            )
            }

        // 8. Fullscreen In-App Photo Viewer Dialog
        if (isPhotoViewerOpen && lastCapturedUri != null) {
            PhotoViewerDialog(
                photoUri = lastCapturedUri,
                onDismiss = { isPhotoViewerOpen = false },
                onPhotoDeleted = {
                    isPhotoViewerOpen = false
                    lastCapturedThumbnail = null
                    lastCapturedUri = null
                    coroutineScope.launch(Dispatchers.IO) {
                        val newLatest = CaptureSaver.getLatestCaptureUri(context)
                        val newThumb = newLatest?.let { CaptureSaver.loadThumbnail(context, it) }
                        withContext(Dispatchers.Main) {
                            lastCapturedUri = newLatest
                            lastCapturedThumbnail = newThumb
                        }
                    }
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
        color = Color(0xCC0C0D0F),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, SlateBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(
                text = "GPU TELEMETRY",
                color = AmberGold,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "FPS: ${"%.1f".format(telemetry.fps)} (${"%.1f".format(telemetry.frameTimeMs)}ms)",
                color = if (telemetry.fps >= 24f) StatusGreen else StatusRed,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "GPU FX: ${"%.1f".format(telemetry.gpuEffectsTimeMs)}ms",
                color = White.copy(alpha = 0.85f),
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "RES: ${telemetry.previewWidth}x${telemetry.previewHeight}",
                color = White.copy(alpha = 0.85f),
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "FBO: ${if (telemetry.isHalfFloat) "16-Bit Float" else "8-Bit RGBA"} | Buffers: ${telemetry.historyBufferCount}",
                color = TextSecondary,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
