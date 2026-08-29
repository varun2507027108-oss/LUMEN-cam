package com.auroracam.app.ui

import android.graphics.Bitmap
import android.view.HapticFeedbackConstants
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.auroracam.app.ui.theme.AmberGold
import com.auroracam.app.ui.theme.AmberGoldDim
import com.auroracam.app.ui.theme.DarkBackground
import com.auroracam.app.ui.theme.DarkSurface
import com.auroracam.app.ui.theme.ElevatedSurface
import com.auroracam.app.ui.theme.SlateBorder
import com.auroracam.app.ui.theme.TextSecondary
import com.auroracam.app.ui.theme.White

/**
 * Minimalist Cinema Shutter Bar.
 *
 * 1. Left: Direct Phone Gallery Launcher Thumbnail.
 * 2. Center: Tactile Shutter Button with animated press feedback.
 * 3. Right: Sleek Creative Drawer Toggle Button.
 */
@Composable
fun CameraShutterBar(
    isCapturing: Boolean,
    lastCapturedThumbnail: Bitmap?,
    onThumbnailClicked: () -> Unit,
    onShutterClicked: () -> Unit,
    activeLookName: String,
    isLookEnabled: Boolean,
    cameraMode: CameraMode,
    isDrawerOpen: Boolean,
    onDrawerToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
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
            .padding(horizontal = 28.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Direct Gallery Launch Thumbnail Preview
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(ElevatedSurface)
                .border(
                    1.5.dp,
                    if (lastCapturedThumbnail != null) AmberGold else SlateBorder,
                    RoundedCornerShape(15.dp)
                )
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onThumbnailClicked()
                },
            contentAlignment = Alignment.Center
        ) {
            if (lastCapturedThumbnail != null) {
                Image(
                    bitmap = lastCapturedThumbnail.asImageBitmap(),
                    contentDescription = "Open in Gallery",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Open Gallery",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 2. Tactile Shutter Button
        Box(
            modifier = Modifier
                .size(76.dp)
                .scale(shutterScale)
                .clip(CircleShape)
                .border(3.dp, White, CircleShape)
                .padding(4.dp)
                .clip(CircleShape)
                .background(if (isCapturing) AmberGold else White)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = !isCapturing
                ) {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onShutterClicked()
                },
            contentAlignment = Alignment.Center
        ) {
            if (isCapturing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = DarkBackground,
                    strokeWidth = 3.dp
                )
            }
        }

        // 3. Creative Modes & Drawer Toggle Button
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(if (isDrawerOpen) AmberGold else ElevatedSurface)
                .border(
                    1.5.dp,
                    if (isDrawerOpen) AmberGold else SlateBorder,
                    CircleShape
                )
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onDrawerToggle()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isDrawerOpen) Icons.Default.Close else Icons.Default.Tune,
                contentDescription = "Creative Controls & Modes",
                tint = if (isDrawerOpen) DarkBackground else White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
