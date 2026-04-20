package com.example.motoday.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MoteroColorScheme = darkColorScheme(
    primary = PrimaryCyan,
    onPrimary = OnPrimaryCyan,
    secondary = SecondaryCyan,
    onSecondary = Color.Black,
    background = BackgroundAsphalt,
    onBackground = Color.White,
    surface = SurfaceAsphalt,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2C2C2E), // Un poco más claro que el fondo para tarjetas
    onSurfaceVariant = Color.White,
    tertiary = Color(0xFF00B8C2),
    error = Color(0xFFFF4444)
)

@Composable
fun MotodayTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MoteroColorScheme,
        typography = Typography,
        content = content
    )
}
