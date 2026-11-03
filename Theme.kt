package com.example.enhancer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Scheme = darkColorScheme(
    primary = Color(0xFFF2B544),        // ذهبي سينمائي
    onPrimary = Color(0xFF1A1200),
    secondary = Color(0xFF4FB3BF),      // تركواز
    background = Color(0xFF0D0E12),
    onBackground = Color(0xFFECEDEF),
    surface = Color(0xFF16181F),
    onSurface = Color(0xFFECEDEF),
    surfaceVariant = Color(0xFF1F222B),
    onSurfaceVariant = Color(0xFFA9ADB8),
    error = Color(0xFFFF6B6B)
)

@Composable
fun EnhancerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
