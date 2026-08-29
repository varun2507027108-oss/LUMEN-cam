package com.auroracam.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.auroracam.app.ui.components.AuroraLookIndex

/**
 * QuickLooksDial — Delegating to AuroraLookIndex (Image Profile Index).
 */
@Composable
fun QuickLooksDial(
    activeLutName: String,
    isLookEnabled: Boolean,
    onSelectPreset: (String) -> Unit,
    onLookEnabledChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    AuroraLookIndex(
        activeLutName = activeLutName,
        isLookEnabled = isLookEnabled,
        onSelectPreset = onSelectPreset,
        onLookEnabledChanged = onLookEnabledChanged,
        modifier = modifier
    )
}
