package com.auroracam.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auroracam.app.ui.theme.AuroraAmber
import com.auroracam.app.ui.theme.AuroraCyan
import com.auroracam.app.ui.theme.TextSecondary
import com.auroracam.app.ui.theme.White

@Composable
fun CameraTopBar(
    currentFps: Double,
    cameraMode: CameraMode,
    dxStage: DxStage,
    currentFormat: FormatMode,
    onFormatClicked: () -> Unit,
    isManualExposureEnabled: Boolean,
    onManualExposureToggled: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: FPS & HDR/LTM badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0x88000000))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (currentFps >= 25.0) AuroraCyan else AuroraAmber)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "${"%.0f".format(currentFps)} FPS",
                color = White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "HDR",
                color = AuroraCyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black
            )
        }

        // Center: Format Badge (4:3, 1:1, XPAN)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0x88000000))
                .clickable { onFormatClicked() }
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Crop,
                contentDescription = "Aspect Ratio",
                tint = if (currentFormat == FormatMode.XPAN) AuroraAmber else White,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = currentFormat.label,
                color = if (currentFormat == FormatMode.XPAN) AuroraAmber else White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Right: Double Exposure Stage or Manual Pro Exposure Toggle
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(if (isManualExposureEnabled) AuroraAmber.copy(alpha = 0.2f) else Color(0x88000000))
                .clickable { onManualExposureToggled() }
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            if (cameraMode == CameraMode.DOUBLE_EXPOSURE) {
                if (dxStage == DxStage.STAGE_2_LOCKED) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = AuroraAmber,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = if (dxStage == DxStage.STAGE_1_EMPTY) "1/2 Base" else "2/2 Locked",
                    color = if (dxStage == DxStage.STAGE_1_EMPTY) AuroraCyan else AuroraAmber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = if (isManualExposureEnabled) AuroraAmber else TextSecondary,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isManualExposureEnabled) "PRO 1/2s" else "AUTO",
                    color = if (isManualExposureEnabled) AuroraAmber else White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
