package com.plantora.billing.i18n

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.util.DisplayMetrics
import java.util.Locale

/**
 * The app's Context wrapper: it fixes both the UI language and the display metrics
 * before any UI is built.
 *
 * **Language.** The audience is often elderly shopkeepers whose phone may be in a
 * different language than they read, so the app language is chosen inside the app
 * (in More) independent of the system language, and persisted per device. The
 * choice is stored in a plain SharedPreferences (not DataStore) because it must be
 * read synchronously in [android.app.Activity.attachBaseContext], which runs before
 * the activity is created. The picker calls [setLanguageTag] then recreates the
 * activity so the whole UI redraws in the new language. An empty tag means "follow
 * the system language" (the default English resources are the base). Supported
 * tags: en, ml, hi, ta, kn.
 *
 * **Display metrics.** Shops reported the UI breaking — clipped buttons, charts
 * spilling off screen — on phones with the system accessibility settings turned up.
 * The type is already sized large for older eyes (17sp base, 56dp primary buttons),
 * so we own legibility and pin the metrics rather than letting the OS re-scale a
 * layout that was designed at one size. [wrap] normalises all three knobs:
 * font scale, display size (density), and the Android 12+ bold-text weight bump.
 * Note that display size scales **dp**, not sp, which is why pinning the Compose
 * font scale alone never fixed the charts.
 */
object LocaleManager {
    private const val PREFS = "plantora_locale"
    private const val KEY_LANG = "app_language"

    /** BCP-47 tag of the chosen language, or "" to follow the system. */
    fun getLanguageTag(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANG, "").orEmpty()

    fun setLanguageTag(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LANG, tag).apply()
    }

    /**
     * Returns a Context forced to the chosen language and to our own display metrics.
     *
     * This always rebuilds the Configuration — even when no language is chosen —
     * because the metrics lock has to apply on every launch regardless of language.
     */
    fun wrap(base: Context): Context {
        val config = Configuration(base.resources.configuration)

        val tag = getLanguageTag(base)
        if (tag.isNotEmpty()) {
            val locale = Locale.forLanguageTag(tag)
            Locale.setDefault(locale)
            config.setLocale(locale)
        }

        // System font size: the app supplies its own large type, so ignore the slider.
        config.fontScale = 1f

        // Display size ("Screen zoom"): scales every dp, so a large setting pushes
        // fixed-size elements (the Sales charts, 56dp buttons) past the screen edge.
        // DENSITY_DEVICE_STABLE is the panel's native density, ignoring that override.
        config.densityDpi = DisplayMetrics.DENSITY_DEVICE_STABLE

        // "Bold text" (Android 12+) thickens every glyph, which widens text enough to
        // overflow product names and amounts. Our weights are already Medium/SemiBold.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            config.fontWeightAdjustment = 0
        }

        return base.createConfigurationContext(config)
    }
}

/** Unwrap a (possibly locale-wrapped) Context to the hosting Activity. */
fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
