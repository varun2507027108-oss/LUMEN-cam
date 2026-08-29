package com.auroracam.app.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
 * 1. Inset-aware with statusBarsPadding() + horizontal 16.dp, vertical 8.dp padding.
 * 2. Semi-transparent dark chips (Color(0x66000000) with 1.dp border Color(0x33FFFFFF)).
 * 3. Tabular monospaced technical metadata:
 *    [ 24 FPS ]  (Technical white/slate font, no misleading red dot)
 *    [ HDR ]     (Toggled active state uses Warm Amber border/text)
 *    [ 4:3 ]     (Aspect ratio switcher)
 *    [ AUTO ] or [ PRO ] (Exposure mode)
 */
@Composable
fun CameraTopBar(
    currentFps: Double,
    isHdrEnabled: Boolean,
    onHdrToggled: () -> Unit,
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Diagnostic FPS & HDR Toggles
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // [ 24 FPS ] (Clean technical monospaced chip without deceptive red dot)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(SmokedChipBg)
                    .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 4.5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${"%.0f".format(currentFps)} FPS",
                    color = NeutralSlate,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // [ HDR ] (Interactive toggle pill)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isHdrEnabled) WarmAmberDim else SmokedChipBg)
                    .border(
                        1.dp,
                        if (isHdrEnabled) WarmAmber else GlassBorder,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onHdrToggled()
                    }
                    .padding(horizontal = 9.dp, vertical = 4.5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "HDR",
                    color = if (isHdrEnabled) WarmAmber else NeutralSlate,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Center: Aspect Ratio [ 4:3 ] / [ 1:1 ] / [ XPAN ]
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (currentFormat == FormatMode.XPAN) WarmAmberDim else SmokedChipBg)
                .border(
                    1.dp,
                    if (currentFormat == FormatMode.XPAN) WarmAmber else GlassBorder,
                    RoundedCornerShape(12.dp)
                )
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onFormatClicked()
                }
                .padding(horizontal = 9.dp, vertical = 4.5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = currentFormat.label,
                color = if (currentFormat == FormatMode.XPAN) WarmAmber else PureWhite,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
        }

        // Right: [ AUTO ] / [ PRO ] or Double Exposure Stage
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (isManualExposureEnabled) WarmAmberDim else SmokedChipBg)
                .border(
                    1.dp,
                    if (isManualExposureEnabled) WarmAmber else GlassBorder,
                    RoundedCornerShape(12.dp)
                )
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onManualExposureToggled()
                }
                .padding(horizontal = 9.dp, vertical = 4.5.dp)
        ) {
            if (cameraMode == CameraMode.DOUBLE_EXPOSURE) {
                if (dxStage == DxStage.STAGE_2_LOCKED) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = WarmAmber,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = if (dxStage == DxStage.STAGE_1_EMPTY) "1/2 BASE" else "2/2 LOCKED",
                    color = if (dxStage == DxStage.STAGE_1_EMPTY) PureWhite else WarmAmber,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            } else {
                Text(
                    text = if (isManualExposureEnabled) "PRO" else "AUTO",
                    color = if (isManualExposureEnabled) WarmAmber else PureWhite,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
