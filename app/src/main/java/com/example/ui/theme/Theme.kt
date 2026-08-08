package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color


private val LightColorScheme = lightColorScheme(
    primary = CinemaPrimary,
    onPrimary = Color.White,
    secondary = CinemaSecondary,
    onSecondary = Color.Black,
    tertiary = CinemaTertiary,
    onTertiary = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF64748B),
    outline = BorderColor,
    error = SpoilerRed,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = CinemaPrimary,
    onPrimary = Color.Black,
    secondary = CinemaSecondary,
    onSecondary = Color.Black,
    tertiary = CinemaTertiary,
    onTertiary = Color.Black,
    background = CinemaBlack,
    onBackground = OnCinemaBackground,
    surface = CinemaSurface,
    onSurface = OnCinemaSurface,
    surfaceVariant = CinemaSurfaceVariant,
    onSurfaceVariant = GrayText,
    outline = BorderColor,
    error = SpoilerRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
