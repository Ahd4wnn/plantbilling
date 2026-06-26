package com.plantora.billing.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Light-first scheme. A dark scheme can be added later; the brief prioritises a
// bright, high-contrast surface for legibility.
private val PlantoraLightColors = lightColorScheme(
    primary = GreenPrimary,
    onPrimary = SurfaceCard,
    primaryContainer = GreenContainer,
    onPrimaryContainer = OnGreenContainer,
    secondary = LeafAccent,
    onSecondary = SurfaceCard,
    background = SurfaceWarm,
    onBackground = InkPrimary,
    surface = SurfaceCard,
    onSurface = InkPrimary,
    surfaceVariant = SurfaceVariantWarm,
    onSurfaceVariant = InkSecondary,
    outline = OutlineSoft,
    error = ErrorRed,
    onError = SurfaceCard,
    errorContainer = ErrorContainerRed,
    onErrorContainer = ErrorRed,
)

@Composable
fun PlantoraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Intentionally always light for now (high-contrast brief).
    val colorScheme = PlantoraLightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = GreenPrimaryDark.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PlantoraTypography,
        shapes = PlantoraShapes,
        content = content,
    )
}
