package com.auroracam.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.auroracam.app.ui.components.AuroraExposureVernier
import com.auroracam.app.ui.components.AuroraFocusReticle
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * FocusRing — Aurora Optical Reticle & EV Vernier Integration.
 *
 * Coordinates:
 * 1. Precision Optical Targeting Reticle with direct Tap-to-Lock and lock contraction.
 * 2. Side-anchored EV Vernier exposure ladder with physical needle index.
 * 3. Keeps slider and reticle visible without disappearing during active dragging.
 */
@Composable
fun FocusRing(
    focusPoint: Offset?,
    evBias: Float,
    onEvBiasChanged: (Float) -> Unit,
    onDismiss: () -> Unit,
    isLocked: Boolean = false,
    onToggleLock: () -> Unit = {},
    focusDistanceDiopters: Float = 0.0f,
    modifier: Modifier = Modifier
) {
    if (focusPoint == null) return

    var isDraggingVernier by remember { mutableStateOf(false) }
    var isVisible by remember(focusPoint) { mutableStateOf(true) }

    // Auto-dismiss countdown (3.5s), paused when locked OR while user is actively dragging the slider
    LaunchedEffect(focusPoint, isLocked, isDraggingVernier) {
        if (!isLocked && !isDraggingVernier) {
            delay(3500)
            isVisible = false
            delay(150)
            onDismiss()
        } else {
            isVisible = true
        }
    }

    val alphaAnim by animateFloatAsState(
        targetValue = if (isVisible) 1.0f else 0.0f,
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "FocusRingAlpha"
    )

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Precision Optical Targeting Reticle with Tap-to-Lock
        AuroraFocusReticle(
            focusPoint = focusPoint,
            isLocked = isLocked,
            onToggleLock = onToggleLock,
            focusDistanceDiopters = focusDistanceDiopters,
            modifier = Modifier
        )

        // 2. Adjacent EV Vernier Exposure Ladder
        val density = LocalDensity.current
        val ladderOffsetX = (focusPoint.x + with(density) { 46.dp.toPx() })
        val ladderOffsetY = (focusPoint.y - with(density) { 90.dp.toPx() })

        Box(
            modifier = Modifier.offset {
                IntOffset(ladderOffsetX.roundToInt(), ladderOffsetY.roundToInt())
            }
        ) {
            AuroraExposureVernier(
                evBias = evBias,
                onEvBiasChanged = onEvBiasChanged,
                onDraggingChanged = { dragging ->
                    isDraggingVernier = dragging
                }
            )
        }
    }
}
