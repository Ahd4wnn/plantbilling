package com.plantora.billing

import com.plantora.billing.domain.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {
    @Test fun parse_and_wire_round_to_two_dp() {
        assertEquals("120.00", Money.parse("120").toWire())
        assertEquals("120.50", Money.parse("120.5").toWire())
        assertEquals("120.46", Money.parse("120.455").toWire()) // HALF_UP
    }

    @Test fun blank_or_invalid_is_zero() {
        assertEquals("0.00", Money.parse(null).toWire())
        assertEquals("0.00", Money.parse("").toWire())
        assertEquals("0.00", Money.parse("abc").toWire())
    }

    @Test fun arithmetic() {
        val a = Money.parse("100.00")
        val b = Money.parse("25.50")
        assertEquals("125.50", (a + b).toWire())
        assertEquals("74.50", (a - b).toWire())
        assertEquals("300.00", (a * 3).toWire())
    }

    @Test fun format_uses_rupee_and_grouping() {
        assertEquals("₹1,234.50", Money.parse("1234.5").format())
    }

    @Test fun comparisons() {
        assertTrue(Money.parse("10.00") > Money.parse("9.99"))
        assertTrue(Money.parse("0.00").isZero())
        assertTrue(Money.parse("0.01").isPositive())
    }
}
