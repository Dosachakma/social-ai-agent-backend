package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.data.model.PlatformType
import com.example.ui.theme.*

@Composable
fun getPlatformColor(platform: PlatformType): Color {
    return when (platform) {
        PlatformType.FACEBOOK -> ColorFacebook
        PlatformType.INSTAGRAM -> ColorInstagram
        PlatformType.TWITTER -> ColorTwitter
        PlatformType.TIKTOK -> ColorTikTok
    }
}

@Composable
fun getPlatformIcon(platform: PlatformType): ImageVector {
    return when (platform) {
        PlatformType.FACEBOOK -> Icons.Default.Public
        PlatformType.INSTAGRAM -> Icons.Default.CameraAlt
        PlatformType.TWITTER -> Icons.Default.AlternateEmail
        PlatformType.TIKTOK -> Icons.Default.VideoLibrary
    }
}

@Composable
fun PlatformBadge(
    platform: PlatformType,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true
) {
    val color = getPlatformColor(platform)
    val icon = getPlatformIcon(platform)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(color, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = platform.displayName,
                    tint = Color.White,
                    modifier = Modifier.size(10.dp)
                )
            }
            if (showLabel) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = platform.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = color
                )
            }
        }
    }
}
