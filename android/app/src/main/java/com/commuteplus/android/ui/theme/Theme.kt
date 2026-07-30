package com.commuteplus.android.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Commute+ theme. Light + dark, both required (outdoor sunlight and night use).
 * Near-flat elevation, neutral canvas, one accent.
 */

private val LightColorScheme = lightColorScheme(
    primary = BrandAccent,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = BrandAccentLight,
    background = SurfaceLight,
    surface = SurfaceContainerLight,
    onBackground = OnSurfaceLight,
    onSurface = OnSurfaceLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = androidx.compose.ui.graphics.Color(0xFFD0D0D0),
)

private val DarkColorScheme = darkColorScheme(
    primary = BrandAccentLight,
    onPrimary = androidx.compose.ui.graphics.Color.Black,
    primaryContainer = BrandAccent,
    background = SurfaceDark,
    surface = SurfaceContainerDark,
    onBackground = OnSurfaceDark,
    onSurface = OnSurfaceDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = androidx.compose.ui.graphics.Color(0xFF3A3A3A),
)

@Composable
fun CommutePlusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CommutePlusTypography,
        content = content,
    )
}
