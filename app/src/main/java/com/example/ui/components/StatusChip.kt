package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.model.PostStatus
import com.example.ui.theme.EmeraldTertiary
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusWarning

@Composable
fun StatusChip(
    status: PostStatus,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, dotColor) = when (status) {
        PostStatus.SCHEDULED -> Triple(StatusInfo.copy(alpha = 0.15f), StatusInfo, StatusInfo)
        PostStatus.PUBLISHED -> Triple(EmeraldTertiary.copy(alpha = 0.15f), EmeraldTertiary, EmeraldTertiary)
        PostStatus.DRAFT -> Triple(StatusWarning.copy(alpha = 0.15f), StatusWarning, StatusWarning)
        PostStatus.FAILED -> Triple(StatusError.copy(alpha = 0.15f), StatusError, StatusError)
        PostStatus.GENERATING -> Triple(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(6.dp),
                shape = CircleShape,
                color = dotColor
            ) {}
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = status.label,
                style = MaterialTheme.typography.labelSmall,
                color = textColor
            )
        }
    }
}

@Composable
fun ConnectionBadge(
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor) = if (isConnected) {
        EmeraldTertiary.copy(alpha = 0.15f) to EmeraldTertiary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f) to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(6.dp),
                shape = CircleShape,
                color = textColor
            ) {}
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isConnected) "Connected" else "Disconnected",
                style = MaterialTheme.typography.labelMedium,
                color = textColor
            )
        }
    }
}
