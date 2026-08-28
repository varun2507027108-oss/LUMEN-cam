package com.auroracam.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import com.auroracam.app.ui.theme.TextSecondary
import com.auroracam.app.ui.theme.White

@Composable
fun DoubleExposureOverlay(
    stage: DxStage,
    blendMode: BlendMode,
    opacity: Float,
    isFlipped: Boolean,
    evBias: Float,
    onEvBiasChanged: (Float) -> Unit,
    onBlendModeSelected: (BlendMode) -> Unit,
    onOpacityChanged: (Float) -> Unit,
    onFlipToggled: () -> Unit,
    onRetakeClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Stage 1: Silhouette Helper (EV Bias Slider)
        AnimatedVisibility(
            visible = stage == DxStage.STAGE_1_EMPTY,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(OverlayBackground)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.BrightnessMedium, contentDescription = null, tint = AuroraCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Silhouette Helper", color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        text = "EV ${if (evBias > 0) "+" else ""}${"%.1f".format(evBias)}",
                        color = if (evBias != 0f) AuroraCyan else TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Slider(
                    value = evBias,
                    onValueChange = onEvBiasChanged,
                    valueRange = -2.0f..2.0f,
                    steps = 23, // ~1/6 EV steps
                    colors = SliderDefaults.colors(
                        thumbColor = AuroraCyan,
                        activeTrackColor = AuroraCyan,
                        inactiveTrackColor = DarkSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Stage 2: Blend & Layer Controls
        AnimatedVisibility(
            visible = stage == DxStage.STAGE_2_LOCKED,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(OverlayBackground)
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onRetakeClicked,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkSurface,
                            contentColor = White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Retake", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = onFlipToggled,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFlipped) AuroraCyan else DarkSurface,
                            contentColor = if (isFlipped) DarkBackground else White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Flip, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = if (isFlipped) "Flipped" else "Flip", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Opacity: ${(opacity * 100).toInt()}%",
                        color = White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.width(80.dp)
                    )
                    Slider(
                        value = opacity,
                        onValueChange = onOpacityChanged,
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = AuroraCyan,
                            activeTrackColor = AuroraCyan,
                            inactiveTrackColor = DarkSurface
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BlendMode.values().forEach { mode ->
                        FilterChip(
                            selected = blendMode == mode,
                            onClick = { onBlendModeSelected(mode) },
                            label = { Text(text = mode.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AuroraCyan,
                                selectedLabelColor = DarkBackground,
                                containerColor = DarkSurface,
                                labelColor = White
                            ),
                            border = null,
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }
        }
    }
}
