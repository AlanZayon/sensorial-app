package com.sensorial.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Ink = Color(0xFF0A0C0F)
private val Slate = Color(0xFF1A2228)
private val Fog = Color(0xFFE6ECEE)
private val Mist = Color(0xFFC5D0D4)
private val Seafoam = Color(0xFF2A9D8F)
private val SeafoamBright = Color(0xFF5EC4B6)
private val Ember = Color(0xFFD76B4F)
private val EmberSoft = Color(0xFFE08A72)

private val LightColors = lightColorScheme(
    primary = Seafoam,
    onPrimary = Color.White,
    secondary = Ink,
    onSecondary = Fog,
    tertiary = Ember,
    onTertiary = Color.White,
    background = Fog,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Mist.copy(alpha = 0.45f),
    onSurfaceVariant = Slate,
    outline = Mist,
)

private val DarkColors = darkColorScheme(
    primary = SeafoamBright,
    onPrimary = Ink,
    secondary = Fog,
    onSecondary = Ink,
    tertiary = EmberSoft,
    onTertiary = Ink,
    background = Ink,
    onBackground = Fog,
    surface = Slate,
    onSurface = Fog,
    surfaceVariant = Color(0xFF243038),
    onSurfaceVariant = Mist,
    outline = Color(0xFF3A4650),
)

@Composable
fun SensorialTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = DerivaTypography,
        content = content,
    )
}
