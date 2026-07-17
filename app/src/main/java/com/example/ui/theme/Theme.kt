package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CinemaColorScheme = darkColorScheme(
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
    onSurfaceVariant = OnCinemaSurface,
    outline = BorderColor,
    error = SpoilerRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force dark cinema theme by default
    dynamicColor: Boolean = false, // Disable dynamic colors to keep brand consistency
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = CinemaColorScheme,
        typography = Typography,
        content = content
    )
}
