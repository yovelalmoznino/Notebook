package com.example.notebook.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val ColorScheme = lightColorScheme(
    primary = PastelPrimary,
    background = PastelBackground,
    surface = PastelSurface,
    onBackground = PastelOnBackground
)

@Composable
fun PastelNoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        typography = Typography,
        content = content
    )
}