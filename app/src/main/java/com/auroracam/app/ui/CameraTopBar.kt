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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auroracam.app.ui.theme.AmberGold
import com.auroracam.app.ui.theme.AmberGoldDim
import com.auroracam.app.ui.theme.DarkSurface
import com.auroracam.app.ui.theme.SlateBorder
import com.auroracam.app.ui.theme.StatusGreen
import com.auroracam.app.ui.theme.StatusRed
import com.auroracam.app.ui.theme.TextMuted
import com.auroracam.app.ui.theme.TextSecondary
import com.auroracam.app.ui.theme.White

/**
 * Minimalist Cinema HUD Top Bar.
 *
 * Provides:
 * 1. Live FPS badge + dedicated, interactive HDR pill toggle.
 * 2. Aspect Ratio badge (4:3, 1:1, XPAN).
 * 3. Double Exposure status or Manual PRO Exposure badge.
 */
@Composable
fun CameraTopBar(
    currentFps: Double,
    isHdrEnabled: Boolean,
    onHdrToggled: () -> Unit,
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
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: FPS & Interactive HDR Button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // FPS Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(com.auroracam.app.ui.theme.SmokedGlass)
                    .border(1.dp, com.auroracam.app.ui.theme.GlassHighlight, RoundedCornerShape(14.dp))
                    .padding(horizontal = 8.dp, vertical = 4.5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (currentFps >= 24.0) com.auroracam.app.ui.theme.FocusMint else com.auroracam.app.ui.theme.SignalRuby)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "${"%.0f".format(currentFps)} FPS",
                    color = com.auroracam.app.ui.theme.TelemetryCobalt,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Dedicated Interactive HDR Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isHdrEnabled) com.auroracam.app.ui.theme.SolarGoldDim else com.auroracam.app.ui.theme.SmokedGlass)
                    .border(
                        1.dp,
                        if (isHdrEnabled) com.auroracam.app.ui.theme.SolarGold else com.auroracam.app.ui.theme.GlassHighlight,
                        RoundedCornerShape(14.dp)
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
                    color = if (isHdrEnabled) com.auroracam.app.ui.theme.SolarGold else TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Center: Format Badge (4:3, 1:1, XPAN)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(com.auroracam.app.ui.theme.SmokedGlass)
                .border(
                    1.dp,
                    if (currentFormat == FormatMode.XPAN) com.auroracam.app.ui.theme.SolarGold else com.auroracam.app.ui.theme.GlassHighlight,
                    RoundedCornerShape(14.dp)
                )
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onFormatClicked()
                }
                .padding(horizontal = 10.dp, vertical = 4.5.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Crop,
                contentDescription = "Aspect Ratio",
                tint = if (currentFormat == FormatMode.XPAN) com.auroracam.app.ui.theme.SolarGold else com.auroracam.app.ui.theme.OpticCyan,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = currentFormat.label,
                color = if (currentFormat == FormatMode.XPAN) com.auroracam.app.ui.theme.SolarGold else White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
        }

        // Right: Double Exposure Stage or Manual Pro Exposure Toggle
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(if (isManualExposureEnabled) com.auroracam.app.ui.theme.SolarGoldDim else com.auroracam.app.ui.theme.SmokedGlass)
                .border(
                    1.dp,
                    if (isManualExposureEnabled) com.auroracam.app.ui.theme.SolarGold else com.auroracam.app.ui.theme.GlassHighlight,
                    RoundedCornerShape(14.dp)
                )
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onManualExposureToggled()
                }
                .padding(horizontal = 10.dp, vertical = 4.5.dp)
        ) {
            if (cameraMode == CameraMode.DOUBLE_EXPOSURE) {
                if (dxStage == DxStage.STAGE_2_LOCKED) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = com.auroracam.app.ui.theme.FocusMint,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = if (dxStage == DxStage.STAGE_1_EMPTY) "1/2 BASE" else "2/2 LOCKED",
                    color = if (dxStage == DxStage.STAGE_1_EMPTY) White else com.auroracam.app.ui.theme.FocusMint,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = if (isManualExposureEnabled) AmberGold else TextSecondary,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isManualExposureEnabled) "PRO 1/2s" else "AUTO",
                    color = if (isManualExposureEnabled) AmberGold else White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
