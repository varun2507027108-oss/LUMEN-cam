package com.auroracam.app.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.auroracam.app.ui.theme.NeutralSlate
import com.auroracam.app.ui.theme.WarmAmber

data class LookOption(
    val label: String,
    val presetName: String? // null represents RAW / Natural
)

val DEFAULT_LOOK_OPTIONS = listOf(
    LookOption("NATURAL", null),
    LookOption("LEICA M", LeicaCharacterLut.LUT_NAME),
    LookOption("CLASSIC CHROME", FujiClassicChromeLut.LUT_NAME),
    LookOption("PORTRA 400", KodakPortra400Lut.LUT_NAME),
    LookOption("HASSELBLAD", HasselbladNaturalLut.LUT_NAME),
    LookOption("WARM", AuroraWarmLut.LUT_NAME),
    LookOption("MONO", MonoLut.LUT_NAME)
)

/**
 * Direct-Access "Looks / LUTs" Horizontal Snapping Text Dial.
 *
 * Positioned directly above the Shutter Button in standard viewfinder mode.
 * - Monospaced uppercase text.
 * - Inactive items at 40% alpha.
 * - Active item highlighted in bold Warm Amber with a tiny dot indicator beneath.
 * - Immediate preset switching with light haptic feedback.
 */
@Composable
fun QuickLooksDial(
    activeLutName: String,
    isLookEnabled: Boolean,
    onSelectPreset: (String) -> Unit,
    onLookEnabledChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DEFAULT_LOOK_OPTIONS.forEach { option ->
            val isSelected = if (option.presetName == null) {
                !isLookEnabled
            } else {
                isLookEnabled && activeLutName == option.presetName
            }

            val textColor by animateColorAsState(
                targetValue = if (isSelected) WarmAmber else NeutralSlate.copy(alpha = 0.40f),
                animationSpec = tween(durationMillis = 150),
                label = "DialTextColor"
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE)
                        if (option.presetName == null) {
                            onLookEnabledChanged(false)
                        } else {
                            if (!isLookEnabled) onLookEnabledChanged(true)
                            onSelectPreset(option.presetName)
                        }
                    }
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                Text(
                    text = option.label,
                    color = textColor,
                    fontSize = 11.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .size(3.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) WarmAmber else androidx.compose.ui.graphics.Color.Transparent)
                )
            }
        }
    }
}
