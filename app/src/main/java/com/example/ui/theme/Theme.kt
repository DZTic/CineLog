package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class AppThemeMode(val displayName: String) {
    DARK("Sombre Cinéma"),
    SYSTEM("Système"),
    LIGHT("Clair")
}

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
    themeMode: AppThemeMode = AppThemeMode.DARK,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val darkTheme = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
