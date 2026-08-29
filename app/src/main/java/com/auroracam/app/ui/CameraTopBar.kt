package com.auroracam.app.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auroracam.app.camera.FlashMode
import com.auroracam.app.ui.theme.FocusMint
import com.auroracam.app.ui.theme.GlassBorder
import com.auroracam.app.ui.theme.NeutralSlate
import com.auroracam.app.ui.theme.PureWhite
import com.auroracam.app.ui.theme.SmokedChipBg
import com.auroracam.app.ui.theme.WarmAmber
import com.auroracam.app.ui.theme.WarmAmberDim
import java.io.File

/**
 * Leica / Hasselblad Minimalist Diagnostic Top Bar Strip.
 *
 * 1. Inset-aware with statusBarsPadding() + horizontal 12.dp, vertical 6.dp padding.
 * 2. Semi-transparent dark chips (Color(0x66000000) with 1.dp border Color(0x33FFFFFF)).
 * 3. Tabular monospaced technical metadata:
 *    [ 24 FPS ]           (Technical slate font)
 *    [ ⚡ AUTO / ON / TORCH / OFF ] (Flash selector)
 *    [ RAW / JPG ]        (Sensor negative companion stream toggle)
 *    [ HDR ]              (Multi-frame burst stacking toggle)
 *    [ 4:3 / 1:1 / XPAN ] (Aspect ratio framing mode)
 *    [ PEAK ]             (Focus Peaking edge detection toggle)
 *    [ AUTO / PRO ]       (Manual Exposure lock)
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
    modifier: Modifier = Modifier
) {
    val view = LocalView.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left side: [ FPS ] | [ FLASH ] | [ RAW ] | [ HDR ]
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.weight(1f, fill = false)
        ) {
            // [ 30 FPS ]
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(SmokedChipBg)
                    .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 7.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${"%.0f".format(currentFps)}F",
                    color = NeutralSlate,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // [ FLASH ] (OFF, AUTO, ON, TORCH)
            val isFlashActive = flashMode != FlashMode.OFF
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isFlashActive) WarmAmberDim else SmokedChipBg)
                    .border(
                        1.dp,
                        if (isFlashActive) WarmAmber else GlassBorder,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onFlashModeToggled()
                    }
                    .padding(horizontal = 7.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val icon = when (flashMode) {
                        FlashMode.AUTO -> Icons.Default.FlashAuto
                        FlashMode.ON -> Icons.Default.FlashOn
                        FlashMode.TORCH -> Icons.Default.Highlight
                        FlashMode.OFF -> Icons.Default.FlashOff
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = "Flash",
                        tint = if (isFlashActive) WarmAmber else NeutralSlate,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = flashMode.name,
                        color = if (isFlashActive) WarmAmber else NeutralSlate,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // [ RAW ] / [ JPG ] (Toggled sensor DNG negative stream)
            if (isRawSupported) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isRawEnabled) WarmAmberDim else SmokedChipBg)
                        .border(
                            1.dp,
                            if (isRawEnabled) WarmAmber else GlassBorder,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onRawToggled()
                        }
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isRawEnabled) "RAW" else "JPG",
                        color = if (isRawEnabled) WarmAmber else NeutralSlate,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // [ HDR ] (Multi-frame burst stacking toggle)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isHdrEnabled) WarmAmberDim else SmokedChipBg)
                    .border(
                        1.dp,
                        if (isHdrEnabled) WarmAmber else GlassBorder,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onHdrToggled()
                    }
                    .padding(horizontal = 7.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "HDR",
                    color = if (isHdrEnabled) WarmAmber else NeutralSlate,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Center: Aspect Ratio [ 4:3 ] / [ 1:1 ] / [ XPAN ]
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (currentFormat == FormatMode.XPAN) WarmAmberDim else SmokedChipBg)
                .border(
                    1.dp,
                    if (currentFormat == FormatMode.XPAN) WarmAmber else GlassBorder,
                    RoundedCornerShape(10.dp)
                )
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onFormatClicked()
                }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = currentFormat.label,
                color = if (currentFormat == FormatMode.XPAN) WarmAmber else PureWhite,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
        }

        // Right side: [ PEAK ] | [ AUTO / PRO ]
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // [ PEAK ] (Focus Peaking edge detection toggle)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isFocusPeakingEnabled) FocusMint.copy(alpha = 0.20f) else SmokedChipBg)
                    .border(
                        1.dp,
                        if (isFocusPeakingEnabled) FocusMint else GlassBorder,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onFocusPeakingToggled()
                    }
                    .padding(horizontal = 7.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "PEAK",
                    color = if (isFocusPeakingEnabled) FocusMint else NeutralSlate,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // [ AUTO ] / [ PRO ] or Double Exposure Stage
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isManualExposureEnabled) WarmAmberDim else SmokedChipBg)
                    .border(
                        1.dp,
                        if (isManualExposureEnabled) WarmAmber else GlassBorder,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onManualExposureToggled()
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                if (cameraMode == CameraMode.DOUBLE_EXPOSURE) {
                    if (dxStage == DxStage.STAGE_2_LOCKED) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = WarmAmber,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                    }
                    Text(
                        text = if (dxStage == DxStage.STAGE_1_EMPTY) "1/2 BASE" else "2/2 LOCK",
                        color = if (dxStage == DxStage.STAGE_1_EMPTY) PureWhite else WarmAmber,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    Text(
                        text = if (isManualExposureEnabled) "PRO" else "AUTO",
                        color = if (isManualExposureEnabled) WarmAmber else PureWhite,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
