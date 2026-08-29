package com.auroracam.app.ui.components

import android.graphics.Bitmap
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auroracam.app.ui.theme.AuroraInstrumentTokens
import com.auroracam.app.ui.theme.BurntBrass
import com.auroracam.app.ui.theme.BurntBrassDim
import com.auroracam.app.ui.theme.Graphite
import com.auroracam.app.ui.theme.HairlineBorder
import com.auroracam.app.ui.theme.MutedText
import com.auroracam.app.ui.theme.ParchmentWhite
import com.auroracam.app.ui.theme.SurfaceRecess
import com.auroracam.app.ui.theme.WarmSlate

/**
 * Aurora Precision Shutter Deck.
 *
 * 1. Left: Film Negative Frame (1:1 Contact Sheet Negative with hairline frame index).
 * 2. Center: Mechanical Shutter Release (78.dp machined concentric release disc with 80ms tactile stroke).
 * 3. Right: Creative Control Rail Trigger (Recessed machined instrument lever).
 */
@Composable
fun AuroraShutterDeck(
    isCapturing: Boolean,
    lastCapturedThumbnail: Bitmap?,
    onThumbnailClicked: () -> Unit,
    onShutterClicked: () -> Unit,
    isDrawerOpen: Boolean,
    onDrawerToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val shutterScale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1.0f,
        animationSpec = tween(durationMillis = 80),
        label = "MechanicalShutterStroke"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Film Negative Review Frame (1:1 Contact Sheet aesthetic)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(SurfaceRecess, AuroraInstrumentTokens.CornerMicro)
                    .border(AuroraInstrumentTokens.StrokeHairline, HairlineBorder, AuroraInstrumentTokens.CornerMicro)
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onThumbnailClicked()
                    }
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                if (lastCapturedThumbnail != null) {
                    Image(
                        bitmap = lastCapturedThumbnail.asImageBitmap(),
                        contentDescription = "Review Frame",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(AuroraInstrumentTokens.CornerNone)
                    )
                } else {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        // Subtle film sprockets reference
                        drawLine(
                            color = HairlineBorder,
                            start = Offset(0f, h * 0.5f),
                            end = Offset(w, h * 0.5f),
                            strokeWidth = 1f
                        )
                        drawLine(
                            color = HairlineBorder,
                            start = Offset(w * 0.5f, 0f),
                            end = Offset(w * 0.5f, h),
                            strokeWidth = 1f
                        )
                    }
                }
            }
            Text(
                text = "EXP #01",
                color = MutedText,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )
        }

        // 2. Precision Mechanical Shutter Release (Concentric Bezel Geometry)
        Box(
            modifier = Modifier
                .size(76.dp),
            contentAlignment = Alignment.Center
        ) {
            // Concentric outer calibration ring
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = HairlineBorder,
                    style = Stroke(width = 1.dp.toPx())
                )
                // 4 Cardinal calibration index ticks
                val r = size.minDimension / 2f
                val c = Offset(size.width / 2f, size.height / 2f)
                val tickLen = 4.dp.toPx()
                drawLine(BurntBrass, Offset(c.x, c.y - r), Offset(c.x, c.y - r + tickLen), 1.5.dp.toPx())
                drawLine(HairlineBorder, Offset(c.x, c.y + r - tickLen), Offset(c.x, c.y + r), 1.dp.toPx())
                drawLine(HairlineBorder, Offset(c.x - r, c.y), Offset(c.x - r + tickLen, c.y), 1.dp.toPx())
                drawLine(HairlineBorder, Offset(c.x + r - tickLen, c.y), Offset(c.x + r, c.y), 1.dp.toPx())
            }

            // Inner solid mechanical release disc
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .scale(shutterScale)
                    .clip(CircleShape)
                    .background(if (isCapturing) BurntBrass else ParchmentWhite)
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
                        modifier = Modifier.size(22.dp),
                        color = SurfaceRecess,
                        strokeWidth = 2.dp
                    )
                }
            }
        }

        // 3. Creative Rail Trigger (Recessed Instrument Lever)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(if (isDrawerOpen) BurntBrassDim else Graphite, AuroraInstrumentTokens.CornerInstrument)
                    .border(
                        AuroraInstrumentTokens.StrokeHairline,
                        if (isDrawerOpen) BurntBrass else HairlineBorder,
                        AuroraInstrumentTokens.CornerInstrument
                    )
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE)
                        onDrawerToggle()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Creative Control Rail",
                    tint = if (isDrawerOpen) BurntBrass else WarmSlate,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = "RAIL",
                color = if (isDrawerOpen) BurntBrass else MutedText,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )
        }
    }
}
