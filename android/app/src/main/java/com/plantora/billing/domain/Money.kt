package com.plantora.billing.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/**
 * Money value type. The server is the source of truth for all money; this type
 * only displays and sends amounts. Always 2dp, ROUND_HALF_UP, never Double —
 * matching the backend's NUMERIC(12,2) convention.
 *
 * Wire format (both directions) is a 2-decimal string, e.g. "120.00".
 */
@JvmInline
value class Money(val amount: BigDecimal) : Comparable<Money> {

    /** API wire value: exactly 2 decimals, e.g. "120.00". */
    fun toWire(): String = q2().toPlainString()

    /** Display value, e.g. "₹1,234.50" (en-IN grouping). */
    fun format(): String = "₹" + inrFormat.format(q2())

    operator fun plus(other: Money) = Money(amount + other.amount)
    operator fun minus(other: Money) = Money(amount - other.amount)
    operator fun times(qty: Int) = Money(amount * BigDecimal(qty))

    fun isZero() = q2().signum() == 0
    fun isPositive() = q2().signum() > 0

    private fun q2(): BigDecimal = amount.setScale(2, RoundingMode.HALF_UP)

    override fun compareTo(other: Money): Int = q2().compareTo(other.q2())

    companion object {
        val ZERO = Money(BigDecimal.ZERO)

        /** Parse a server/string amount; blank or invalid → ZERO. */
        fun parse(raw: String?): Money {
            if (raw.isNullOrBlank()) return ZERO
            return try {
                Money(BigDecimal(raw.trim()))
            } catch (e: NumberFormatException) {
                ZERO
            }
        }

        fun of(value: Int) = Money(BigDecimal(value))

        private val inrFormat: NumberFormat =
            NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
                minimumFractionDigits = 2
                maximumFractionDigits = 2
            }
    }
}
