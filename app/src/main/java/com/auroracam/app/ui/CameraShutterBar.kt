package com.auroracam.app.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auroracam.app.ui.theme.AuroraAmber
import com.auroracam.app.ui.theme.AuroraCyan
import com.auroracam.app.ui.theme.DarkBackground
import com.auroracam.app.ui.theme.DarkSurface
import com.auroracam.app.ui.theme.TextSecondary
import com.auroracam.app.ui.theme.White

@Composable
fun CameraShutterBar(
    isCapturing: Boolean,
    lastCapturedThumbnail: Bitmap?,
    onShutterClicked: () -> Unit,
    activeLookName: String,
    isLookEnabled: Boolean,
    onLookQuickToggle: () -> Unit,
    statusText: String? = null,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val shutterScale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1.0f,
        animationSpec = tween(durationMillis = 100),
        label = "ShutterScale"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Gallery Thumbnail
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(DarkSurface)
                .border(1.5.dp, Color(0x44FFFFFF), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (lastCapturedThumbnail != null) {
                Image(
                    bitmap = lastCapturedThumbnail.asImageBitmap(),
                    contentDescription = "Last Capture",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 2. Large Tactical Shutter Button
        Box(
            modifier = Modifier
                .size(72.dp)
                .scale(shutterScale)
                .clip(CircleShape)
                .border(3.5.dp, White, CircleShape)
                .padding(4.dp)
                .clip(CircleShape)
                .background(if (isCapturing) AuroraAmber else White)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = !isCapturing
                ) {
                    onShutterClicked()
                },
            contentAlignment = Alignment.Center
        ) {
            if (isCapturing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(26.dp),
                    color = DarkBackground,
                    strokeWidth = 2.5.dp
                )
            }
        }

        // 3. Quick Look Active Pill / Toggle
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (isLookEnabled) Color(0x3300E5FF) else Color(0x22FFFFFF))
                .border(
                    1.dp,
                    if (isLookEnabled) AuroraCyan else Color(0x44FFFFFF),
                    CircleShape
                )
                .clickable { onLookQuickToggle() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "Toggle Look",
                tint = if (isLookEnabled) AuroraCyan else TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
