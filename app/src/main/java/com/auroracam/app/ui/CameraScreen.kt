package com.auroracam.app.ui

import android.graphics.Bitmap
import android.opengl.GLSurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
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

    // Mode & Format State
    var cameraMode by remember { mutableStateOf(CameraMode.STANDARD) }
    var currentFormat by remember { mutableStateOf(FormatMode.RATIO_4_3) }
    var dxStage by remember { mutableStateOf(DxStage.STAGE_1_EMPTY) }
    var blendMode by remember { mutableStateOf(BlendMode.SCREEN) }
    var opacity by remember { mutableFloatStateOf(1.0f) }
    var isFlipped by remember { mutableStateOf(false) }
    var evBias by remember { mutableFloatStateOf(0.0f) }

    // Signature Look State
    var isLookEnabled by remember { mutableStateOf(true) }
    var lookIntensity by remember { mutableFloatStateOf(1.0f) }
    var lookHalation by remember { mutableFloatStateOf(0.20f) }
    var activeLutName by remember { mutableStateOf(lutManager.activeLutName) }
    var cachedLutsList by remember { mutableStateOf<List<File>>(emptyList()) }
    var isLegacyJpeg by remember { mutableStateOf(cameraController.isLegacyJpegPath()) }

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
        // 1. OpenGL Viewport
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
                    rendererRef = renderer
                    setRenderer(renderer)
                    renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                }
            }
        )

        // 2. Format Framing Guides Overlay (Compose overlay only — not baked into capture)
        FormatOverlay(formatMode = currentFormat)

        // 3. Top HUD Bar
        CameraTopBar(
            currentFps = currentFps,
            cameraMode = cameraMode,
            dxStage = dxStage,
            isManualExposureEnabled = isManualExposureEnabled,
            onManualExposureToggled = {
                isManualExposureEnabled = !isManualExposureEnabled
                cameraController.setManualExposure(isManualExposureEnabled)
            }
        )

        // 4. Bottom Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            // Double Exposure Overlay
            AnimatedVisibility(visible = cameraMode == CameraMode.DOUBLE_EXPOSURE, enter = fadeIn(), exit = fadeOut()) {
                DoubleExposureOverlay(
                    stage = dxStage,
                    blendMode = blendMode,
                    opacity = opacity,
                    isFlipped = isFlipped,
                    evBias = evBias,
                    onEvBiasChanged = { ev ->
                        evBias = ev
                        cameraController.setExposureCompensation(ev)
                    },
                    onBlendModeSelected = { mode ->
                        blendMode = mode
                        rendererRef?.dxBlendMode = mode.modeId
                    },
                    onOpacityChanged = { op ->
                        opacity = op
                        rendererRef?.dxOpacity = op
                    },
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

            // Look Overlay Panel (Presets, Glow/Halation Slider, Intensity Slider, Format Chips, .CUBE Picker)
            LookOverlay(
                isLookEnabled = isLookEnabled,
                onLookEnabledChanged = { enabled ->
                    isLookEnabled = enabled
                    rendererRef?.isLookEnabled = enabled
                },
                intensity = lookIntensity,
                onIntensityChanged = { intensity ->
                    lookIntensity = intensity
                    rendererRef?.lookIntensity = intensity
                },
                halation = lookHalation,
                onHalationChanged = { halo ->
                    lookHalation = halo
                    rendererRef?.lookHalation = halo
                },
                activeLutName = activeLutName,
                onSelectPreset = { presetName ->
                    val (name, cube) = lutManager.selectPreset(presetName)
                    activeLutName = name
                    rendererRef?.updateLutCube(cube)
                },
                onPickLutFile = {
                    lutPickerLauncher.launch(arrayOf("*/*"))
                },
                onGenerateDebugLuts = {
                    coroutineScope.launch {
                        val files = DebugLutGenerator.generateDebugCubes(context)
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
                currentFormat = currentFormat,
                onFormatChanged = { format -> currentFormat = format },
                isLegacyJpeg = isLegacyJpeg,
                onLegacyJpegToggled = {
                    val next = !isLegacyJpeg
                    isLegacyJpeg = next
                    cameraController.setLegacyJpegPath(next)
                },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )

            // Mode Selector Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                CameraMode.values().forEach { mode ->
                    val isSelected = cameraMode == mode
                    Text(
                        text = mode.title.uppercase(),
                        color = if (isSelected) AuroraCyan else TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                cameraMode = mode
                                rendererRef?.currentMode = mode
                                if (mode == CameraMode.STANDARD) {
                                    rendererRef?.retakeFirstExposure()
                                    cameraController.setAeAwbLock(false)
                                    evBias = 0.0f
                                    cameraController.setExposureCompensation(0f)
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Shutter Bar
            CameraShutterBar(
                isCapturing = isCapturing,
                lastCapturedThumbnail = lastCapturedThumbnail,
                onShutterClicked = {
                    if (cameraMode == CameraMode.STANDARD) {
                        isCapturing = true
                        cameraController.takePictureBitmap { rawBitmap ->
                            if (rawBitmap == null) {
                                isCapturing = false
                                return@takePictureBitmap
                            }
                            rendererRef?.renderGradedStill(rawBitmap) { gradedBitmap ->
                                coroutineScope.launch(Dispatchers.IO) {
                                    val cropped = currentFormat.cropBitmap(gradedBitmap)
                                    val uri = CaptureSaver.saveBitmap(
                                        context = context,
                                        bitmap = cropped,
                                        fileName = "${CaptureSaver.generateBaseFileName()}.jpg",
                                        quality = 97
                                    )
                                    if (cropped != gradedBitmap) {
                                        cropped.recycle()
                                    }
                                    gradedBitmap.recycle()
                                    val thumb = uri?.let { CaptureSaver.loadThumbnail(context, it) }
                                    withContext(Dispatchers.Main) {
                                        lastCapturedThumbnail = thumb
                                        isCapturing = false
                                    }
                                }
                            }
                        }
                    } else {
                        if (dxStage == DxStage.STAGE_1_EMPTY) {
                            rendererRef?.captureFirstExposure()
                        } else {
                            isCapturing = true
                            cameraController.takePictureBitmap { secondBitmap ->
                                if (secondBitmap == null) {
                                    isCapturing = false
                                    return@takePictureBitmap
                                }
                                rendererRef?.renderCompositeStill(secondBitmap) { firstBmp, secondBmp, compositeBmp ->
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val baseName = CaptureSaver.generateBaseFileName()
                                        val firstFile = "${baseName}_first.jpg"
                                        val secondFile = "${baseName}_second.jpg"
                                        val compFile = "${baseName}.jpg"

                                        val croppedFirst = currentFormat.cropBitmap(firstBmp)
                                        val croppedSecond = currentFormat.cropBitmap(secondBmp)
                                        val croppedComp = currentFormat.cropBitmap(compositeBmp)

                                        // Save clean first and second; graded composite with Q97
                                        CaptureSaver.saveBitmap(context, croppedFirst, firstFile, quality = 97)
                                        CaptureSaver.saveBitmap(context, croppedSecond, secondFile, quality = 97)
                                        val compUri = CaptureSaver.saveBitmap(context, croppedComp, compFile, quality = 97)

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
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            )
        }
    }
}
