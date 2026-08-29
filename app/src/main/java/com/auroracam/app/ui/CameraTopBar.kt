package com.auroracam.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.auroracam.app.camera.FlashMode
import com.auroracam.app.ui.components.AuroraInstrumentStrip
import com.auroracam.app.ui.model.ContextualTelemetryMode
import java.io.File

/**
 * CameraTopBar — Adapter delegating to AuroraInstrumentStrip.
 */
@Composable
fun CameraTopBar(
    currentFps: Double,
    flashMode: FlashMode = FlashMode.OFF,
    onFlashModeToggled: () -> Unit = {},
    isRawSupported: Boolean = false,
    isRawEnabled: Boolean = false,
    onRawToggled: () -> Unit = {},
    isHdrEnabled: Boolean,
    onHdrToggled: () -> Unit,
    isFocusPeakingEnabled: Boolean = false,
    onFocusPeakingToggled: () -> Unit = {},
    activeLutName: String,
    isLookEnabled: Boolean,
    onSelectPreset: (String) -> Unit,
    onLookEnabledChanged: (Boolean) -> Unit,
    cachedLuts: List<File> = emptyList(),
    onSelectCachedLut: (File) -> Unit = {},
    cameraMode: CameraMode,
    dxStage: DxStage,
    currentFormat: FormatMode,
    onFormatClicked: () -> Unit,
    isManualExposureEnabled: Boolean,
    onManualExposureToggled: () -> Unit,
    evBias: Float = 0.0f,
    focusDistanceDiopters: Float = 0.0f,
    isManualFocus: Boolean = false,
    telemetryMode: ContextualTelemetryMode = ContextualTelemetryMode.DEFAULT,
    modifier: Modifier = Modifier
) {
    AuroraInstrumentStrip(
        currentFps = currentFps,
        evBias = evBias,
        focusDistanceDiopters = focusDistanceDiopters,
        isManualFocus = isManualFocus,
        telemetryMode = telemetryMode,
        isRawSupported = isRawSupported,
        isRawEnabled = isRawEnabled,
        onRawToggled = onRawToggled,
        isHdrEnabled = isHdrEnabled,
        onHdrToggled = onHdrToggled,
        isFocusPeakingEnabled = isFocusPeakingEnabled,
        onFocusPeakingToggled = onFocusPeakingToggled,
        flashMode = flashMode,
        onFlashModeToggled = onFlashModeToggled,
        modifier = modifier
    )
}
