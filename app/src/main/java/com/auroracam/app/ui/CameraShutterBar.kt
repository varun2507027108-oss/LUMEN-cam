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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.auroracam.app.ui.theme.BorderHairline
import com.auroracam.app.ui.theme.NeutralSlate
import com.auroracam.app.ui.theme.PureWhite
import com.auroracam.app.ui.theme.SurfaceDark
import com.auroracam.app.ui.theme.SurfaceElevated
import com.auroracam.app.ui.theme.WarmAmber
import com.auroracam.app.ui.theme.WarmAmberDim

/**
 * Mechanical-Style Tactile Shutter Dock.
 *
 * 1. Left: Gallery Thumbnail (48.dp Squircle with 2.dp amber/slate border).
 * 2. Center: Mechanical Shutter Button (80.dp outer metallic ring + 66.dp inner solid white disc with 0.92f press scale).
 * 3. Right: Creative Controls Toggle Button (48.dp squircle chip with fine slider/tuning icon).
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
        targetValue = if (isPressed) 0.92f else 1.0f,
        animationSpec = tween(durationMillis = 80),
        label = "MechanicalShutterScale"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Gallery Thumbnail (48.dp Squircle)
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceElevated)
                .border(
                    2.dp,
                    if (lastCapturedThumbnail != null) WarmAmber else BorderHairline,
                    RoundedCornerShape(14.dp)
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
                    contentDescription = "Open Gallery",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Open Gallery",
                    tint = NeutralSlate,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 2. Mechanical Tactile Shutter Button (80.dp Outer Ring + 66.dp Inner Disc)
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .border(3.dp, PureWhite, CircleShape)
                .padding(5.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(66.dp)
                    .scale(shutterScale)
                    .clip(CircleShape)
                    .background(if (isCapturing) WarmAmber else PureWhite)
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
                        modifier = Modifier.size(24.dp),
                        color = SurfaceDark,
                        strokeWidth = 2.5.dp
                    )
                }
            }
        }

        // 3. Creative Controls Toggle Button (48.dp Squircle)
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (isDrawerOpen) WarmAmberDim else SurfaceElevated)
                .border(
                    1.dp,
                    if (isDrawerOpen) WarmAmber else BorderHairline,
                    RoundedCornerShape(14.dp)
                )
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onDrawerToggle()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = "Creative Controls",
                tint = if (isDrawerOpen) WarmAmber else NeutralSlate,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
