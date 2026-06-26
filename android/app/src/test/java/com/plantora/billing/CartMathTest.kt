package com.plantora.billing

import com.plantora.billing.domain.DiscountType
import com.plantora.billing.domain.Money
import com.plantora.billing.domain.Product
import com.plantora.billing.ui.billing.CartLine
import com.plantora.billing.ui.billing.CartMath
import com.plantora.billing.ui.billing.PaymentMode
import org.junit.Assert.assertEquals
import org.junit.Test

class CartMathTest {
    private fun product(price: String) =
        Product(id = "p", name = "Plant", category = null, retailPrice = Money.parse(price), photoUrl = null, isActive = true)

    private fun line(price: String, qty: Int) = CartLine(product(price), qty, Money.parse(price))

    @Test fun subtotal_sums_line_totals() {
        val lines = listOf(line("100.00", 2), line("50.00", 1))
        assertEquals("250.00", CartMath.subtotal(lines).toWire())
    }

    @Test fun flat_discount_capped_at_subtotal() {
        val sub = Money.parse("200.00")
        assertEquals("50.00", CartMath.discountAmount(sub, DiscountType.FLAT, Money.parse("50")).toWire())
        assertEquals("200.00", CartMath.discountAmount(sub, DiscountType.FLAT, Money.parse("999")).toWire())
    }

    @Test fun percent_discount_capped_at_100() {
        val sub = Money.parse("200.00")
        assertEquals("20.00", CartMath.discountAmount(sub, DiscountType.PERCENT, Money.parse("10")).toWire())
        assertEquals("200.00", CartMath.discountAmount(sub, DiscountType.PERCENT, Money.parse("150")).toWire())
    }

    @Test fun payment_split_balances_with_due() {
        val total = Money.parse("300.00")
        // Split: cash 100, due 50 -> upi should be remainder 150; cash+upi+due == total
        val (cash, upi) = CartMath.paymentSplit(total, PaymentMode.SPLIT, Money.parse("100"), Money.parse("50"))
        assertEquals("100.00", cash.toWire())
        assertEquals("150.00", upi.toWire())
        assertEquals(total.toWire(), (cash + upi + Money.parse("50")).toWire())
    }

    @Test fun cash_mode_puts_everything_payable_in_cash() {
        val total = Money.parse("300.00")
        val (cash, upi) = CartMath.paymentSplit(total, PaymentMode.CASH, Money.ZERO, Money.parse("50"))
        assertEquals("250.00", cash.toWire())
        assertEquals("0.00", upi.toWire())
    }
}
