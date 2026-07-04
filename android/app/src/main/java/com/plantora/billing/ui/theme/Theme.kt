package com.plantora.billing.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat

// Cap on the system font scale the app will honour. The base type is already large
// (17px+) and tuned for older eyes, so we still let users enlarge — but a 2x/3x
// system setting would break every layout, so we clamp to 1.5x. We never shrink
// below 1.0 either, keeping our tuned sizes intact.
private const val MAX_FONT_SCALE = 1.5f

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

    // Clamp the font scale so an extreme system setting can't wreck the layout.
    val density = LocalDensity.current
    val clampedDensity = Density(
        density = density.density,
        fontScale = density.fontScale.coerceIn(1f, MAX_FONT_SCALE),
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PlantoraTypography,
        shapes = PlantoraShapes,
    ) {
        CompositionLocalProvider(LocalDensity provides clampedDensity, content = content)
    }
}
