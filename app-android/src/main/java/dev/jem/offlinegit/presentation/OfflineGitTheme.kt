package dev.jem.offlinegit.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Single dark "dev-tool" theme from the mockups
 * (`docs/mockups/offline-github-viewer-preview.html`). The app is intentionally
 * dark-only per the "Android Studio-like coloring" goal, so there is no light scheme.
 */
object OfflineGitPalette {
    val Background = Color(0xFF12161B) // --app-bg
    val Panel = Color(0xFF1A2027) // --app-panel
    val PanelHigh = Color(0xFF212A33) // --app-panel-2
    val Line = Color(0xFF2B353F) // --app-line
    val Ink = Color(0xFFDDE5EC) // --app-ink
    val Muted = Color(0xFF8B98A5) // --app-muted
    val Accent = Color(0xFF4F9CF7) // --app-accent
    val OnAccent = Color(0xFF0B1320)

    val Add = Color(0xFF3FB950) // --add
    val Del = Color(0xFFF0564F) // --del
    val Rename = Color(0xFFC894FF) // status "R" chip accent
}

/**
 * Diff-surface colors pulled straight from the mockup's `.dline` rules. Kept separate
 * from the Material [androidx.compose.material3.ColorScheme] because they are content
 * colors, not role colors, and are referenced directly by the diff renderer.
 */
object DiffPalette {
    val AdditionBackground = OfflineGitPalette.Add.copy(alpha = 0.13f) // --add-bg
    val DeletionBackground = OfflineGitPalette.Del.copy(alpha = 0.12f) // --del-bg
    val HunkBackground = OfflineGitPalette.Accent.copy(alpha = 0.10f) // --hunk-bg

    val AdditionText = Color(0xFFB7E6B0)
    val DeletionText = Color(0xFFF2B8B5)
    val HunkText = OfflineGitPalette.Accent
    val ContextText = OfflineGitPalette.Ink
    val LineNumber = Color(0xFF56616C)
}

private val OfflineGitColorScheme = darkColorScheme(
    primary = OfflineGitPalette.Accent,
    onPrimary = OfflineGitPalette.OnAccent,
    primaryContainer = Color(0xFF1A293A), // accent at ~14% over the app background
    onPrimaryContainer = OfflineGitPalette.Accent,
    secondary = OfflineGitPalette.Accent,
    onSecondary = OfflineGitPalette.OnAccent,
    background = OfflineGitPalette.Background,
    onBackground = OfflineGitPalette.Ink,
    surface = OfflineGitPalette.Panel,
    onSurface = OfflineGitPalette.Ink,
    surfaceVariant = OfflineGitPalette.PanelHigh,
    onSurfaceVariant = OfflineGitPalette.Muted,
    surfaceContainer = OfflineGitPalette.Panel,
    surfaceContainerHigh = OfflineGitPalette.PanelHigh,
    surfaceContainerHighest = OfflineGitPalette.PanelHigh,
    outline = OfflineGitPalette.Line,
    outlineVariant = OfflineGitPalette.Line,
    tertiaryContainer = DiffPalette.HunkBackground,
    onTertiaryContainer = OfflineGitPalette.Accent,
    error = OfflineGitPalette.Del,
    onError = OfflineGitPalette.OnAccent,
    errorContainer = Color(0xFF3A1F1E),
    onErrorContainer = DiffPalette.DeletionText,
)

@Composable
fun OfflineGitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OfflineGitColorScheme,
        typography = Typography(),
        content = content,
    )
}
