package com.plantora.billing.ui.billing

import com.plantora.billing.domain.DiscountType
import com.plantora.billing.domain.Money
import com.plantora.billing.domain.Product
import java.math.BigDecimal
import java.math.RoundingMode

/** A line in the cart. The unit price is pre-filled from the product but is
 *  always editable (plants of the same kind sell at different prices by size). */
data class CartLine(
    val product: Product,
    val quantity: Int,
    val unitPrice: Money,
) {
    val lineTotal: Money get() = unitPrice * quantity
}

enum class PaymentMode { CASH, UPI, SPLIT }

/**
 * Client-side preview of the bill totals. DISPLAY ONLY — the server recomputes
 * and is authoritative. Kept pure so it can be unit-tested.
 */
data class CartTotals(
    val subtotal: Money,
    val discountAmount: Money,
    val total: Money,
)

object CartMath {
    fun subtotal(lines: Collection<CartLine>): Money =
        lines.fold(Money.ZERO) { acc, line -> acc + line.lineTotal }

    /** Mirrors the server: flat is capped at subtotal; percent is capped at 100%. */
    fun discountAmount(subtotal: Money, type: DiscountType, value: Money): Money {
        if (subtotal.isZero() || !value.isPositive()) return Money.ZERO
        return when (type) {
            DiscountType.FLAT -> if (value > subtotal) subtotal else value
            DiscountType.PERCENT -> {
                val pct = value.amount.min(BigDecimal(100))
                Money(subtotal.amount.multiply(pct).divide(BigDecimal(100), 2, RoundingMode.HALF_UP))
            }
        }
    }

    fun totals(lines: Collection<CartLine>, type: DiscountType, value: Money): CartTotals {
        val sub = subtotal(lines)
        val disc = discountAmount(sub, type, value)
        return CartTotals(subtotal = sub, discountAmount = disc, total = sub - disc)
    }

    /**
     * Split the payable amount into (cash, upi) for a chosen mode. `due` is the
     * amount deferred; payableNow = total - due. Guarantees cash + upi + due == total
     * (the server's hard rule), with split auto-filling UPI as the remainder.
     */
    fun paymentSplit(total: Money, mode: PaymentMode, cashEntered: Money, due: Money): Pair<Money, Money> {
        val safeDue = if (due > total) total else due
        val payableNow = total - safeDue
        return when (mode) {
            PaymentMode.CASH -> payableNow to Money.ZERO
            PaymentMode.UPI -> Money.ZERO to payableNow
            PaymentMode.SPLIT -> {
                val cash = when {
                    cashEntered > payableNow -> payableNow
                    cashEntered < Money.ZERO -> Money.ZERO
                    else -> cashEntered
                }
                cash to (payableNow - cash)
            }
        }
    }
}
