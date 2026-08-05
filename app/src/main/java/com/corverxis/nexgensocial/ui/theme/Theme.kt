package com.corverxis.nexgensocial.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Same hex values as the web app's CSS variables, so the three clients
// don't look like unrelated products.
val Navy950 = Color(0xFF060F1C)
val Navy900 = Color(0xFF0B1728)
val Navy800 = Color(0xFF142438)
val Cyan400 = Color(0xFF29D3F5)
val Cyan300 = Color(0xFF7FE3FA)
val Slate300 = Color(0xFFC3D3E2)
val Slate400 = Color(0xFF94A9BE)
val Danger = Color(0xFFFF6B6B)
val LineColor = Color(0x1AFFFFFF)

private val DarkColors = darkColorScheme(
    primary = Cyan400,
    onPrimary = Navy950,
    secondary = Cyan300,
    background = Navy950,
    onBackground = Color.White,
    surface = Navy900,
    onSurface = Color.White,
    surfaceVariant = Navy800,
    onSurfaceVariant = Slate300,
    error = Danger,
    outline = LineColor,
)

/**
 * Dark-only by design. The web app is dark, and a light variant that was
 * never designed would look broken rather than adaptive.
 */
@Composable
fun NexgenSocialTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
