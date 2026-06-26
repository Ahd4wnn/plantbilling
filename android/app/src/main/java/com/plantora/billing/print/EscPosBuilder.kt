package com.plantora.billing.print

import com.plantora.billing.domain.BillDetail
import com.plantora.billing.domain.Money
import java.io.ByteArrayOutputStream
import java.text.Normalizer

/**
 * Compiles a bill into ESC/POS bytes for a thermal printer. Ported from the web
 * app's compileEscPosReceipt (frontend/src/store/bluetooth.ts), adapted to be
 * paper-width aware: `width` is the printable character columns (32 for 58mm,
 * 48 for 80mm).
 *
 * Money prints as ASCII "Rs. 1234.50" (no grouping) for clean monospace
 * alignment on cheap printers.
 */
class EscPosBuilder(private val width: Int = 32) {

    private val out = ByteArrayOutputStream()

    fun build(bill: BillDetail, autoCut: Boolean): ByteArray {
        init()
        header(bill)
        meta(bill)
        items(bill)
        totals(bill)
        payments(bill)
        footer(autoCut)
        return out.toByteArray()
    }

    fun buildTest(connectionLabel: String, autoCut: Boolean): ByteArray {
        init()
        center(); bold(true); doubleSize(true)
        text("TEST PRINT\n\n")
        normalSize(); bold(false)
        text("Thermal Printer Connected!\n")
        text("Connection: $connectionLabel\n")
        text("ESC/POS Commands: Working OK\n")
        divider()
        text(ruler() + "\n")
        divider()
        footer(autoCut)
        return out.toByteArray()
    }

    // ── Sections ─────────────────────────────────────────────────────────────
    private fun header(bill: BillDetail) {
        center(); bold(true); doubleSize(true)
        text((bill.businessName ?: bill.shopName ?: "NURSERY RECEIPT").uppercase() + "\n")
        normalSize(); bold(false)
        bill.businessAddress?.takeIf { it.isNotBlank() }?.let { text(it.trim() + "\n") }
        bill.businessPhone?.takeIf { it.isNotBlank() }?.let { text("Contact: ${it.trim()}\n") }
        divider()
    }

    private fun meta(bill: BillDetail) {
        left()
        text("Bill Ref : #${bill.id.take(8).uppercase()}\n")
        text("Date     : ${bill.createdAt}\n")
        bill.salespersonEmail?.let { text("Staff    : $it\n") }
        bill.customerName?.let { name ->
            val v = if (bill.customerPhone != null) "$name (${bill.customerPhone})" else name
            text("Customer : $v\n")
        }
        bill.remarks?.takeIf { it.isNotBlank() }?.let { text("Remarks  : $it\n") }
        divider()
    }

    private fun items(bill: BillDetail) {
        bill.items.forEach { item ->
            bold(true); text("${item.productName}\n"); bold(false)
            val qtyPrice = "  ${item.quantity} x ${rs(item.unitPrice)}"
            val lineTotal = rs(item.lineTotal)
            text(twoCol(qtyPrice, lineTotal, width) + "\n")
        }
        divider()
    }

    private fun totals(bill: BillDetail) {
        row("Subtotal", rs(bill.subtotal))
        if (bill.discountAmount.isPositive()) {
            val label = if (bill.discountType == com.plantora.billing.domain.DiscountType.PERCENT) {
                "Discount (${bill.discountValue.toWire()}%)"
            } else "Discount"
            row(label, "-" + rs(bill.discountAmount))
        }
        divider()
        bigRow("TOTAL", rs(bill.total))
        divider()
    }

    private fun payments(bill: BillDetail) {
        if (bill.cashAmount.isPositive()) row("Paid via Cash", rs(bill.cashAmount))
        if (bill.upiAmount.isPositive()) row("Paid via UPI", rs(bill.upiAmount))
        if (bill.dueAmount.isPositive()) row("Remaining Due", rs(bill.dueAmount))
        if (!bill.cashAmount.isPositive() && !bill.upiAmount.isPositive() && !bill.dueAmount.isPositive()) {
            row("Paid via Cash", rs(Money.ZERO))
        }
        divider()
    }

    private fun footer(autoCut: Boolean) {
        center()
        text("Thank you for shopping with us!\n")
        text("Please visit us again.\n\n\n\n")
        if (autoCut) out.write(byteArrayOf(0x1d, 0x56, 0x42, 0x00)) else text("\n\n\n\n")
    }

    // ── Row layout ───────────────────────────────────────────────────────────
    private fun row(label: String, value: String) = text(twoCol(label, value, width) + "\n")

    private fun bigRow(label: String, value: String) {
        bold(true); doubleSize(true)
        // Double-width consumes two columns per char, so use half the width.
        text(twoCol(label, value, width / 2) + "\n")
        bold(false); normalSize()
    }

    private fun twoCol(left: String, right: String, cols: Int): String {
        val pad = cols - left.length - right.length
        return if (pad > 0) left + " ".repeat(pad) + right
        else left + "\n" + " ".repeat((cols - right.length).coerceAtLeast(0)) + right
    }

    private fun ruler(): String = (1..width).joinToString("") { (it % 10).toString() }

    private fun rs(money: Money): String = "Rs. " + money.toWire()

    // ── Raw ESC/POS commands ─────────────────────────────────────────────────
    private fun init() = out.write(byteArrayOf(0x1b, 0x40))
    private fun center() = out.write(byteArrayOf(0x1b, 0x61, 0x01))
    private fun left() = out.write(byteArrayOf(0x1b, 0x61, 0x00))
    private fun bold(on: Boolean) = out.write(byteArrayOf(0x1b, 0x45, if (on) 0x01 else 0x00))
    private fun doubleSize(on: Boolean) = out.write(byteArrayOf(0x1d, 0x21, if (on) 0x11 else 0x00))
    private fun normalSize() = doubleSize(false)
    private fun divider() = text("-".repeat(width) + "\n")

    private fun text(s: String) = out.write(cleanAscii(s).toByteArray(Charsets.US_ASCII))

    /** Strip accents/non-ASCII so cheap printers don't garble (mirrors web cleanAscii). */
    private fun cleanAscii(str: String): String {
        val noAccents = Normalizer.normalize(str, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        return noAccents
            .replace('–', '-').replace('—', '-')
            .replace('‘', '\'').replace('’', '\'')
            .replace('“', '"').replace('”', '"')
            .replace("₹", "Rs.")
            .replace("[^\\x00-\\x7F]".toRegex(), "")
    }
}
