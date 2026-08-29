package com.auroracam.app.ui.components

import android.graphics.Bitmap
import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auroracam.app.ui.theme.Ash
import com.auroracam.app.ui.theme.AuroraBrass
import com.auroracam.app.ui.theme.DarkGraphite
import com.auroracam.app.ui.theme.GraphiteSurface
import com.auroracam.app.ui.theme.HairlineBorder
import com.auroracam.app.ui.theme.HairlineSubtle
import com.auroracam.app.ui.theme.SignalAlert
import com.auroracam.app.ui.theme.WarmSilver

/**
 * AuroraShutter — Minimal Photographic Shutter Deck.
 *
 * Visual Anchors:
 * - Left: Film Negative Gallery Preview
 * - Center: Concentric Physical Shutter Release (◉) with 70ms tactile press animation
 * - Right: Circular Parameter & Pro Control Button
 */
@Composable
fun AuroraShutter(
    isCapturing: Boolean,
    onShutterClick: () -> Unit,
    lastCapturedThumbnail: Bitmap?,
    lastCapturedUri: Uri?,
    onGalleryClicked: () -> Unit,
    isAdjustPanelOpen: Boolean,
    onToggleAdjustPanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    var isPressed by remember { mutableStateOf(false) }

    val shutterScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        animationSpec = tween(durationMillis = 70, easing = FastOutSlowInEasing),
        label = "ShutterPressScale"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .padding(horizontal = 32.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Film Gallery Thumbnail (Left)
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(DarkGraphite)
                .border(1.dp, HairlineBorder, CircleShape)
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onGalleryClicked()
                },
            contentAlignment = Alignment.Center
        ) {
            if (lastCapturedThumbnail != null) {
                Image(
                    bitmap = lastCapturedThumbnail.asImageBitmap(),
                    contentDescription = "Last Capture",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                )
            } else {
                Text(
                    text = "IMG",
                    color = Ash.copy(alpha = 0.7f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 2. Concentric Physical Shutter Release (Center ◉)
        Box(
            modifier = Modifier
                .size(76.dp)
                .scale(shutterScale)
                .pointerInput(isCapturing) {
                    if (!isCapturing) {
                        detectTapGestures(
                            onPress = {
                                isPressed = true
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                tryAwaitRelease()
                                isPressed = false
                            },
                            onTap = {
                                if (!isCapturing) {
                                    onShutterClick()
                                }
                            }
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Outer Chassis Ring
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Color(0x3308090B))
                    .border(2.dp, if (isCapturing) SignalAlert else WarmSilver.copy(alpha = 0.85f), CircleShape)
            )

            // Inner Core Button
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(CircleShape)
                    .background(
                        if (isCapturing) SignalAlert.copy(alpha = 0.85f)
                        else WarmSilver
                    )
            )

            if (isCapturing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(70.dp),
                    color = SignalAlert,
                    strokeWidth = 2.5.dp
                )
            }
        }

        // 3. Circular Parameter & Pro Control Button (Right)
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(if (isAdjustPanelOpen) AuroraBrass.copy(alpha = 0.18f) else DarkGraphite)
                .border(
                    1.dp,
                    if (isAdjustPanelOpen) AuroraBrass else HairlineBorder,
                    CircleShape
                )
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onToggleAdjustPanel()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = "Adjust Parameters & Pro Settings",
                tint = if (isAdjustPanelOpen) AuroraBrass else WarmSilver,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
