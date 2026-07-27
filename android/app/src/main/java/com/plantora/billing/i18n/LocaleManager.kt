package com.plantora.billing.i18n

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import java.util.Locale

/**
 * In-app language selection for the shop-owner app. The audience is often elderly
 * shopkeepers whose phone may be in a different language than they read, so the
 * app language is chosen inside the app (in More) independent of the system
 * language, and persisted per device.
 *
 * The choice is stored in a plain SharedPreferences (not DataStore) because it
 * must be read synchronously in [android.app.Activity.attachBaseContext], which
 * runs before the activity is created. [wrap] applies it by handing back a
 * locale-scoped Context; the picker calls [setLanguageTag] then recreates the
 * activity so the whole UI redraws in the new language.
 *
 * An empty tag means "follow the system language" (the default English resources
 * are the base). Supported tags: en, ml, hi, ta, kn.
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

    /** Returns a Context forced to the chosen language, or [base] unchanged if none. */
    fun wrap(base: Context): Context {
        val tag = getLanguageTag(base)
        if (tag.isEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
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
