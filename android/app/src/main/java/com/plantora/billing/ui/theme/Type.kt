package com.plantora.billing.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.plantora.billing.R

// Elderly-friendly scale: larger base sizes, medium/semibold weights (never
// thin/light), generous line height. Body base is 17sp per the design brief.
//
// The font is BUNDLED rather than the system default on purpose. Samsung/Xiaomi
// font packs replace the device's sans-serif with typefaces of different letter
// widths, which re-wraps and clips layouts that were laid out against one metric.
// Shipping Inter means every phone renders identically.
//
// Inter has no Indic glyphs, and the app ships Malayalam/Hindi/Tamil/Kannada.
// Android's platform font fallback substitutes a system face for codepoints Inter
// doesn't cover, so those scripts still render — verify visually when changing this.
private val Default = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private val Tuned = Typography(
    displaySmall = TextStyle(
        fontFamily = Default, fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp, lineHeight = 40.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = Default, fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp, lineHeight = 34.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Default, fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp, lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Default, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Default, fontWeight = FontWeight.Medium,
        fontSize = 18.sp, lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Default, fontWeight = FontWeight.Normal,
        fontSize = 17.sp, lineHeight = 25.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Default, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 23.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Default, fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp, lineHeight = 22.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Default, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 18.sp,
    ),
)

// The styles above are the ones the app writes by hand; Material components also
// reach for the rest (titleSmall, bodySmall, labelSmall…), which would otherwise
// keep Material's system-font defaults and let an OEM font pack back in. Stamp
// Inter onto every slot.
val PlantoraTypography = Tuned.copy(
    displayLarge = Tuned.displayLarge.copy(fontFamily = Default),
    displayMedium = Tuned.displayMedium.copy(fontFamily = Default),
    headlineSmall = Tuned.headlineSmall.copy(fontFamily = Default),
    titleSmall = Tuned.titleSmall.copy(fontFamily = Default),
    bodySmall = Tuned.bodySmall.copy(fontFamily = Default),
    labelSmall = Tuned.labelSmall.copy(fontFamily = Default),
)
