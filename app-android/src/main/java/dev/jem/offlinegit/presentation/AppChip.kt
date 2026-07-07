package dev.jem.offlinegit.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Pill styles from the mockup's `.chip`, `.chip.ok`, and `.chip.acc` rules. */
enum class ChipStyle {
    NEUTRAL,
    OK,
    ACCENT,
}

private data class ChipColors(val content: Color, val border: Color)

private fun ChipStyle.colors(): ChipColors = when (this) {
    ChipStyle.NEUTRAL -> ChipColors(OfflineGitPalette.Muted, OfflineGitPalette.Line)
    ChipStyle.OK -> ChipColors(OfflineGitPalette.Add, OfflineGitPalette.Add.copy(alpha = 0.4f))
    ChipStyle.ACCENT -> ChipColors(OfflineGitPalette.Accent, OfflineGitPalette.Accent.copy(alpha = 0.4f))
}

@Composable
fun StatusChip(
    text: String,
    style: ChipStyle = ChipStyle.NEUTRAL,
    showDot: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = style.colors()
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(OfflineGitPalette.Panel)
            .border(1.dp, colors.border, RoundedCornerShape(999.dp))
            .padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showDot) {
            Box(
                modifier = Modifier
                    .padding(end = 5.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(colors.content),
            )
        }
        Text(
            text = text,
            color = colors.content,
            fontSize = 10.5.sp,
        )
    }
}

/** Square file-status badge from the mockup's `.st` rules (M / A / R). */
@Composable
fun StatusSquare(
    status: String,
    modifier: Modifier = Modifier,
) {
    val letter = status.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val content: Color
    val background: Color
    when (letter) {
        "A" -> {
            content = OfflineGitPalette.Add
            background = OfflineGitPalette.Add.copy(alpha = 0.18f)
        }
        "R" -> {
            content = OfflineGitPalette.Rename
            background = OfflineGitPalette.Rename.copy(alpha = 0.18f)
        }
        "D" -> {
            content = OfflineGitPalette.Del
            background = OfflineGitPalette.Del.copy(alpha = 0.18f)
        }
        else -> {
            content = OfflineGitPalette.Accent
            background = OfflineGitPalette.Accent.copy(alpha = 0.18f)
        }
    }
    Box(
        modifier = modifier
            .size(18.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            color = content,
            fontSize = 10.5.sp,
            textAlign = TextAlign.Center,
        )
    }
}
