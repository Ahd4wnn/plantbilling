package com.plantora.billing

import com.plantora.billing.domain.monthBounds
import com.plantora.billing.domain.weekBounds
import com.plantora.billing.ui.billing.voice.PhoneticMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class PhoneticMatcherTest {
    @Test fun phonetic_code_drops_vowels_and_merges() {
        // ficus -> fks (c->k), hibiscus -> hbsks
        assertEquals("fks", PhoneticMatcher.phoneticCode("ficus"))
        assertEquals("tls", PhoneticMatcher.phoneticCode("tulsi"))
    }

    @Test fun exact_match_scores_one() {
        val m = PhoneticMatcher.findBestMatch("Money Plant", listOf("Money Plant", "Rose"))
        assertNotNull(m); assertEquals("Money Plant", m!!.candidate); assertEquals(1.0, m.score, 0.0001)
    }

    @Test fun phonetic_handles_indian_pronunciation() {
        // "vatering" should map to "Watering Can" via w/v merger
        val m = PhoneticMatcher.findBestMatch("vatering can", listOf("Watering Can", "Money Plant"))
        assertNotNull(m); assertEquals("Watering Can", m!!.candidate)
    }

    @Test fun no_match_returns_null() {
        assertNull(PhoneticMatcher.findBestMatch("zzzzzz", listOf("Money Plant", "Rose")))
    }

    @Test fun week_bounds_are_monday_to_sunday() {
        // 2026-06-25 is a Thursday → Mon 22nd .. Sun 28th
        val (from, to) = weekBounds(LocalDate.of(2026, 6, 25))
        assertEquals(LocalDate.of(2026, 6, 22), from)
        assertEquals(LocalDate.of(2026, 6, 28), to)
    }

    @Test fun month_bounds_cover_full_month() {
        val (from, to) = monthBounds(LocalDate.of(2026, 6, 25))
        assertEquals(LocalDate.of(2026, 6, 1), from)
        assertEquals(LocalDate.of(2026, 6, 30), to)
    }
}
