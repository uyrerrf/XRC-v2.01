package com.xrc.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val XrcDarkColors = darkColorScheme(
    primary = Color(0xFFFF2D2D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF7A0000),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Color(0xFFE6E6E6),
    background = Color(0xFF0B0F14),
    onBackground = Color(0xFFE6E6E6),
    surface = Color(0xFF12181F),
    onSurface = Color(0xFFE6E6E6),
    surfaceVariant = Color(0xFF1A222B),
    onSurfaceVariant = Color(0xFF8A94A0)
)

@Composable
fun XrcTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = XrcDarkColors,
        content = content
    )
}
