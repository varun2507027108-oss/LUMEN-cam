package com.auroracam.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.auroracam.app.ui.theme.AuroraAmber
import com.auroracam.app.ui.theme.DarkBackground
import com.auroracam.app.ui.theme.DarkSurface
import com.auroracam.app.ui.theme.TextSecondary
import com.auroracam.app.ui.theme.White

@Composable
fun CameraShutterBar(
    isCapturing: Boolean,
    lastCapturedThumbnail: Bitmap?,
    onShutterClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
    ) {
        // Thumbnail preview
        Box(
            modifier = Modifier
                .size(52.dp)
                .align(Alignment.CenterStart)
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSurface)
                .border(1.dp, White.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (lastCapturedThumbnail != null) {
                Image(
                    bitmap = lastCapturedThumbnail.asImageBitmap(),
                    contentDescription = "Last Capture",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Shutter Button
        Box(
            modifier = Modifier
                .size(76.dp)
                .align(Alignment.Center)
                .clip(CircleShape)
                .border(4.dp, White, CircleShape)
                .padding(5.dp)
                .clip(CircleShape)
                .background(if (isCapturing) AuroraAmber else White)
                .clickable(enabled = !isCapturing) {
                    onShutterClicked()
                },
            contentAlignment = Alignment.Center
        ) {
            if (isCapturing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = DarkBackground,
                    strokeWidth = 3.dp
                )
            }
        }
    }
}
