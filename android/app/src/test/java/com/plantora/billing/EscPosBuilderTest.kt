package com.plantora.billing

import com.plantora.billing.domain.BillDetail
import com.plantora.billing.domain.BillItem
import com.plantora.billing.domain.DiscountType
import com.plantora.billing.domain.Money
import com.plantora.billing.domain.PaymentMethod
import com.plantora.billing.print.EscPosBuilder
import org.junit.Assert.assertTrue
import org.junit.Test

class EscPosBuilderTest {
    private fun sampleBill() = BillDetail(
        id = "abcd1234ef",
        shopName = "Green Leaf",
        businessName = "Green Leaf Nursery",
        businessAddress = "MG Road",
        businessPhone = "9999999999",
        subtotal = Money.parse("250.00"),
        discountType = DiscountType.FLAT,
        discountValue = Money.parse("50.00"),
        discountAmount = Money.parse("50.00"),
        total = Money.parse("200.00"),
        cashAmount = Money.parse("200.00"),
        upiAmount = Money.ZERO,
        dueAmount = Money.ZERO,
        paymentMethod = PaymentMethod.CASH,
        customerName = "Rama",
        customerPhone = "8888888888",
        salespersonEmail = null,
        remarks = null,
        isEdited = false,
        createdAt = "2026-06-25T10:00:00Z",
        items = listOf(
            BillItem("p1", "Rose — déluxe", Money.parse("100.00"), 2, Money.parse("200.00")),
            BillItem("p2", "Tulsi", Money.parse("50.00"), 1, Money.parse("50.00")),
        ),
    )

    @Test fun build_starts_with_init_command() {
        val bytes = EscPosBuilder(32).build(sampleBill(), autoCut = true)
        // ESC @ initialise
        assertTrue(bytes.size > 2)
        assertTrue(bytes[0] == 0x1b.toByte() && bytes[1] == 0x40.toByte())
    }

    @Test fun output_is_ascii_only_and_rupee_replaced() {
        val bytes = EscPosBuilder(32).build(sampleBill(), autoCut = false)
        val text = String(bytes, Charsets.US_ASCII)
        // Accented char stripped to ASCII; rupee shown as Rs.
        assertTrue(text.contains("Rose"))
        assertTrue(text.contains("Rs. 200.00"))
        // No bytes above 0x7F (pure ASCII payload).
        assertTrue(bytes.all { it.toInt() and 0xFF <= 0x7F })
    }

    @Test fun autocut_appends_cut_command() {
        val bytes = EscPosBuilder(32).build(sampleBill(), autoCut = true)
        val tail = bytes.takeLast(4)
        assertTrue(tail == listOf(0x1d.toByte(), 0x56.toByte(), 0x42.toByte(), 0x00.toByte()))
    }
}
