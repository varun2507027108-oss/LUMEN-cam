package com.auroracam.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auroracam.app.ui.theme.AuroraAmber
import com.auroracam.app.ui.theme.AuroraCyan
import com.auroracam.app.ui.theme.DarkBackground
import com.auroracam.app.ui.theme.DarkSurface
import com.auroracam.app.ui.theme.OverlayBackground
import com.auroracam.app.ui.theme.White

@Composable
fun CameraTopBar(
    currentFps: Double,
    cameraMode: CameraMode,
    dxStage: DxStage,
    isManualExposureEnabled: Boolean,
    onManualExposureToggled: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(OverlayBackground)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // FPS badge
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (currentFps >= 25.0) AuroraCyan else AuroraAmber)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "FPS: ${"%.1f".format(currentFps)}",
                color = White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Stage indicator / AE Lock badge in DX mode
        if (cameraMode == CameraMode.DOUBLE_EXPOSURE) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (dxStage == DxStage.STAGE_2_LOCKED) {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = AuroraAmber, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = if (dxStage == DxStage.STAGE_1_EMPTY) "1/2: Base" else "2/2: AE/AWB Locked",
                    color = if (dxStage == DxStage.STAGE_1_EMPTY) AuroraCyan else AuroraAmber,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Manual Exposure Toggle
        Button(
            onClick = onManualExposureToggled,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isManualExposureEnabled) AuroraAmber else DarkSurface,
                contentColor = if (isManualExposureEnabled) DarkBackground else White
            ),
            shape = RoundedCornerShape(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            modifier = Modifier.height(28.dp)
        ) {
            Icon(imageVector = Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(12.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isManualExposureEnabled) "1/2s ISO 50" else "Auto AE",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
