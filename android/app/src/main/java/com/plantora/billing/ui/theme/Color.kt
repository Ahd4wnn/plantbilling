package com.plantora.billing.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The app's brand identity, as a set of interchangeable palettes.
 *
 * Everything brand-coloured in the app resolves through [ACTIVE_BRAND], so the
 * whole look changes by editing one line — no find-and-replace, no risk of a
 * stray old colour surviving in some screen nobody reopened.
 *
 * Every palette must keep white text on [primary] at WCAG AA (≥ 4.5:1): the
 * audience is elderly shop owners and primary buttons are white-on-brand.
 * [accent] is deliberately lighter — it is for icons and highlights only and
 * must never sit behind white text.
 *
 * If the brand colour changes here, also update to match:
 *   - `res/values/colors.xml`     (pre-Compose status bar + launcher background)
 *   - the launcher/web/iOS icon PNGs
 *   - `backend/app/services/report_xlsx.py` (the Excel report's header bands)
 */
sealed interface Brand {
    val primary: Color
    val primaryDark: Color
    val container: Color
    val onContainer: Color
    val accent: Color

    /**
     * The brand orange. White on #F05B01 is 3.4:1 — that clears WCAG AA for LARGE
     * text only, so anything white-on-brand must stay big and bold, and small
     * brand-coloured text on white (prices, links) sits below AA at this shade.
     * [primaryDark] is 5.0:1 and is the safe choice wherever that matters.
     */
    data object Orange : Brand {
        override val primary = Color(0xFFF05B01)
        override val primaryDark = Color(0xFFC24700)
        override val container = Color(0xFFFFE3D0)
        override val onContainer = Color(0xFF451900)
        override val accent = Color(0xFFFF8534)
    }

    /** The previous, deeper orange. White on #C2410C is 4.9:1 (AA at any size). */
    data object DeepOrange : Brand {
        override val primary = Color(0xFFC2410C)
        override val primaryDark = Color(0xFF9A3412)
        override val container = Color(0xFFFFE0CC)
        override val onContainer = Color(0xFF3D1502)
        override val accent = Color(0xFFF97316)
    }

    /** The original botanical green. White on #2E7D46 is 5.3:1. */
    data object Green : Brand {
        override val primary = Color(0xFF2E7D46)
        override val primaryDark = Color(0xFF1F5C32)
        override val container = Color(0xFFB7E5C4)
        override val onContainer = Color(0xFF06210F)
        override val accent = Color(0xFF5BA86E)
    }
}

/**
 * The live brand. Swap for [Brand.DeepOrange] (higher contrast, same family) or
 * [Brand.Green] (the original botanical identity) — nothing else needs editing.
 */
val ACTIVE_BRAND: Brand = Brand.Orange

val BrandPrimary = ACTIVE_BRAND.primary
val BrandPrimaryDark = ACTIVE_BRAND.primaryDark
val BrandContainer = ACTIVE_BRAND.container
val OnBrandContainer = ACTIVE_BRAND.onContainer
val BrandAccent = ACTIVE_BRAND.accent

// Warm, near-white surfaces (not flat gray) for depth and legibility.
val SurfaceWarm = Color(0xFFFBFAF7)
val SurfaceCard = Color(0xFFFFFFFF)
val SurfaceVariantWarm = Color(0xFFEFEDE6)
val OutlineSoft = Color(0xFFD9D6CD)

// Near-black primary text for high contrast (never light gray for content).
val InkPrimary = Color(0xFF14171A)
val InkSecondary = Color(0xFF4A4F55)

val ErrorRed = Color(0xFFB3261E)
val ErrorContainerRed = Color(0xFFF9DEDC)

// Money / status accents. These are SEMANTIC, not brand: green means "cash
// received", blue means UPI, amber means still owed. They stay put when the
// brand colour changes so the three payment figures remain tellable apart at a
// glance — which matters more here than colour-matching the brand.
val CashGreen = Color(0xFF2E7D46)
val UpiBlue = Color(0xFF2563A8)
val DueAmber = Color(0xFFB26A00)
