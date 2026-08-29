package com.auroracam.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auroracam.app.gl.lut.AuroraWarmLut
import com.auroracam.app.gl.lut.FujiClassicChromeLut
import com.auroracam.app.gl.lut.HasselbladNaturalLut
import com.auroracam.app.gl.lut.KodakPortra400Lut
import com.auroracam.app.gl.lut.LeicaCharacterLut
import com.auroracam.app.gl.lut.MonoLut
import com.auroracam.app.ui.theme.BurntBrass
import com.auroracam.app.ui.theme.HairlineBorder
import com.auroracam.app.ui.theme.MutedText
import com.auroracam.app.ui.theme.ParchmentWhite
import com.auroracam.app.ui.theme.TextDisabled
import com.auroracam.app.ui.theme.WarmSlate

data class LookProfile(
    val id: String?, // null = Natural / RAW pass
    val label: String,
    val category: String // "NATURAL", "CHARACTER", "FILM", "MONO"
)

val DEFAULT_LOOK_PROFILES = listOf(
    LookProfile(null, "NATURAL", "RAW"),
    LookProfile(LeicaCharacterLut.LUT_NAME, "LEICA M", "CHARACTER"),
    LookProfile(FujiClassicChromeLut.LUT_NAME, "CLASSIC CHROME", "FILM"),
    LookProfile(KodakPortra400Lut.LUT_NAME, "PORTRA 400", "FILM"),
    LookProfile(HasselbladNaturalLut.LUT_NAME, "HASSELBLAD", "CHARACTER"),
    LookProfile(AuroraWarmLut.LUT_NAME, "AURORA WARM", "LOOK"),
    LookProfile(MonoLut.LUT_NAME, "MONOCHROME", "MONO")
)

/**
 * Aurora Image Profile Index ("Look Index").
 *
 * Direct optical profile selector with mechanical registration needle:
 *
 *        NATURAL    LEICA M    CLASSIC CHROME    PORTRA 400
 *      ───────────────▲─────────────────────────────────────
 */
@Composable
fun AuroraLookIndex(
    activeLutName: String,
    isLookEnabled: Boolean,
    onSelectPreset: (String) -> Unit,
    onLookEnabledChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Horizontal Profile Index
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DEFAULT_LOOK_PROFILES.forEach { profile ->
                val isSelected = if (profile.id == null) {
                    !isLookEnabled
                } else {
                    isLookEnabled && activeLutName == profile.id
                }

                val textColor by animateColorAsState(
                    targetValue = if (isSelected) ParchmentWhite else MutedText,
                    animationSpec = tween(durationMillis = 140),
                    label = "ProfileIndexText"
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE)
                            if (profile.id == null) {
                                onLookEnabledChanged(false)
                            } else {
                                if (!isLookEnabled) onLookEnabledChanged(true)
                                onSelectPreset(profile.id)
                            }
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = profile.label,
                        color = textColor,
                        fontSize = 10.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = profile.category,
                        color = if (isSelected) BurntBrass else TextDisabled,
                        fontSize = 7.5.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    // Registration tick mark
                    Box(
                        modifier = Modifier
                            .width(12.dp)
                            .height(1.5.dp)
                    ) {
                        Canvas(modifier = Modifier.matchParentSize()) {
                            if (isSelected) {
                                drawLine(
                                    color = BurntBrass,
                                    start = Offset(0f, 0f),
                                    end = Offset(size.width, 0f),
                                    strokeWidth = 1.5.dp.toPx()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
