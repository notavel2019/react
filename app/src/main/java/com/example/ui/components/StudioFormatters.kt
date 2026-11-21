package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CoralCrimson
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.SignalAmber
import com.example.ui.theme.StudioMonoStyle
import java.util.Locale

fun formatTimecode(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L).toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

fun formatDb(db: Float): String {
    return if (db <= -59f) "-∞ dB" else String.format(Locale.US, "%.0f dB", db)
}

@Composable
fun StudioStatusPill(
    text: String,
    dotColor: Color,
    modifier: Modifier = Modifier,
    containerColor: Color = Color(0xCC10141E),
    borderColor: Color = dotColor.copy(alpha = 0.45f)
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = text,
            style = StudioMonoStyle,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun MiniMicBars(
    levels: List<Float>,
    isSpeaking: Boolean,
    modifier: Modifier = Modifier
) {
    val recent = levels.takeLast(7)
    Canvas(modifier = modifier.height(16.dp)) {
        val count = recent.size.coerceAtLeast(1)
        val gap = 2.5.dp.toPx()
        val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(2.dp.toPx())
        recent.forEachIndexed { idx, value ->
            val barH = (size.height * value.coerceIn(0.16f, 1f))
            val top = (size.height - barH) / 2f
            val color = when {
                isSpeaking && value > 0.72f -> CoralCrimson
                isSpeaking -> ElectricCyan
                value > 0.5f -> SignalAmber
                else -> ElectricCyan.copy(alpha = 0.55f)
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(idx * (barWidth + gap), top),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
