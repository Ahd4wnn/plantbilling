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
    primary = BrandPrimary,
    onPrimary = SurfaceCard,
    primaryContainer = BrandContainer,
    onPrimaryContainer = OnBrandContainer,
    secondary = BrandAccent,
    onSecondary = SurfaceCard,
    // Left unset, these fall back to Material's baseline purple — which is what
    // the quick-add FAB on the Bill screen was drawing itself in, next to a
    // brand-coloured cart FAB. Tie them to the brand so the pair belongs together.
    secondaryContainer = BrandContainer,
    onSecondaryContainer = OnBrandContainer,
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
            window.statusBarColor = BrandPrimaryDark.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    // Font scale and display size are pinned upstream, on the Configuration itself
    // (see LocaleManager.wrap), with a belt-and-braces Compose pin in MainActivity.
    MaterialTheme(
        colorScheme = colorScheme,
        typography = PlantoraTypography,
        shapes = PlantoraShapes,
        content = content,
    )
}
