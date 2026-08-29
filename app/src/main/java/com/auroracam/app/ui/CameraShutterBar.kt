package com.auroracam.app.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.auroracam.app.ui.components.AuroraShutterDeck

/**
 * CameraShutterBar — Adapter delegating to AuroraShutterDeck.
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
    AuroraShutterDeck(
        isCapturing = isCapturing,
        lastCapturedThumbnail = lastCapturedThumbnail,
        onThumbnailClicked = onThumbnailClicked,
        onShutterClicked = onShutterClicked,
        isDrawerOpen = isDrawerOpen,
        onDrawerToggle = onDrawerToggle,
        modifier = modifier
    )
}
