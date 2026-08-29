package com.auroracam.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auroracam.app.ui.theme.DarkGraphite
import com.auroracam.app.ui.theme.FocusGreen
import com.auroracam.app.ui.theme.HairlineBorder
import com.auroracam.app.ui.theme.WarmSilver
import kotlin.math.roundToInt

/**
 * AuroraFocusReticle — Optical Focusing Reticle with Instant Lock Toggle.
 *
 * Clean photographic corner brackets with central targeting ring:
 *      [🔒 AF-L] (Tap to Lock / Unlock)
 *      ┌─────┐
 *      │  ◎  │
 *      └─────┘
 * Shifts to FocusGreen on lock with subtle contraction and tactile feedback.
 */
@Composable
fun AuroraFocusReticle(
    focusPoint: Offset,
    isLocked: Boolean = false,
    onToggleLock: () -> Unit = {},
    focusDistanceDiopters: Float = 0.0f,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val reticleSize = 64.dp

    // Mechanical contraction on lock
    val lockScale by animateFloatAsState(
        targetValue = if (isLocked) 0.82f else 1.0f,
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "ReticleLockContraction"
    )

    val reticleColor = if (isLocked) FocusGreen else WarmSilver.copy(alpha = 0.90f)

    Box(
        modifier = modifier
            .offset {
                IntOffset(
                    (focusPoint.x - 32.dp.toPx()).roundToInt(),
                    (focusPoint.y - 32.dp.toPx()).roundToInt()
                )
            }
            .size(reticleSize),
        contentAlignment = Alignment.Center
    ) {
        // Reticle Corner Brackets & Center Ring Canvas
        Canvas(modifier = Modifier.size(reticleSize * lockScale)) {
            val w = size.width
            val h = size.height
            val armLen = w * 0.24f
            val strokeWidth = 1.35.dp.toPx()

            // 1. Four Corner Registration Brackets
            // Top-Left ┌
            drawLine(reticleColor, Offset(0f, 0f), Offset(armLen, 0f), strokeWidth)
            drawLine(reticleColor, Offset(0f, 0f), Offset(0f, armLen), strokeWidth)

            // Top-Right ┐
            drawLine(reticleColor, Offset(w, 0f), Offset(w - armLen, 0f), strokeWidth)
            drawLine(reticleColor, Offset(w, 0f), Offset(w, armLen), strokeWidth)

            // Bottom-Left └
            drawLine(reticleColor, Offset(0f, h), Offset(armLen, h), strokeWidth)
            drawLine(reticleColor, Offset(0f, h), Offset(0f, h - armLen), strokeWidth)

            // Bottom-Right ┘
            drawLine(reticleColor, Offset(w, h), Offset(w - armLen, h), strokeWidth)
            drawLine(reticleColor, Offset(w, h), Offset(w, h - armLen), strokeWidth)

            // 2. Central Optical Registration Ring (◎)
            val cx = w / 2f
            val cy = h / 2f
            val innerRingRadius = 4.5.dp.toPx()
            drawCircle(
                color = reticleColor,
                radius = innerRingRadius,
                center = Offset(cx, cy),
                style = Stroke(width = 1.25.dp.toPx())
            )
            drawCircle(
                color = reticleColor,
                radius = 1.5.dp.toPx(),
                center = Offset(cx, cy)
            )
        }

        // Tap-to-Lock Button Pill (Directly above reticle)
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-22).dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (isLocked) FocusGreen.copy(alpha = 0.20f) else DarkGraphite.copy(alpha = 0.85f))
                .border(
                    0.75.dp,
                    if (isLocked) FocusGreen else HairlineBorder,
                    RoundedCornerShape(6.dp)
                )
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    onToggleLock()
                }
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                contentDescription = if (isLocked) "Unlock AE/AF" else "Lock AE/AF",
                tint = if (isLocked) FocusGreen else WarmSilver,
                modifier = Modifier.size(10.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = if (isLocked) "AE/AF LOCK" else "LOCK",
                color = if (isLocked) FocusGreen else WarmSilver,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )
        }
    }
}
