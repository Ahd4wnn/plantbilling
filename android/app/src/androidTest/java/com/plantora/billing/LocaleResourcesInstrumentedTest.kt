package com.plantora.billing

import android.content.Context
import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Proves the per-language resource files resolve — the same mechanism the in-app
 * language picker uses (LocaleManager wraps the Context in the chosen locale).
 */
@RunWith(AndroidJUnit4::class)
class LocaleResourcesInstrumentedTest {

    private fun localized(tag: String): Context {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag))
        return base.createConfigurationContext(config)
    }

    @Test fun english_is_the_default() {
        assertEquals("Bill", localized("en").getString(R.string.nav_bill))
        assertEquals("Language", localized("en").getString(R.string.more_language))
    }

    @Test fun malayalam_resolves() {
        val c = localized("ml")
        assertEquals("ബിൽ", c.getString(R.string.nav_bill))
        assertEquals("കൂടുതൽ", c.getString(R.string.nav_more))
        assertEquals("ഭാഷ", c.getString(R.string.more_language))
    }

    @Test fun hindi_tamil_kannada_resolve() {
        assertEquals("बिल", localized("hi").getString(R.string.nav_bill))
        assertEquals("பில்", localized("ta").getString(R.string.nav_bill))
        assertEquals("ಬಿಲ್", localized("kn").getString(R.string.nav_bill))
    }

    @Test fun format_args_survive_translation() {
        // %1$s placeholder must be preserved in every locale.
        assertEquals("റോൾ: manager", localized("ml").getString(R.string.more_role, "manager"))
        assertEquals("भूमिका: manager", localized("hi").getString(R.string.more_role, "manager"))
    }
}
