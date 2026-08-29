package com.plantora.billing.print

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.plantora.billing.domain.BillDetail
import com.plantora.billing.domain.DiscountType
import com.plantora.billing.domain.Money
import com.plantora.billing.domain.formatReceiptDateTime
import java.io.ByteArrayOutputStream

/**
 * Renders a bill into ESC/POS bytes by drawing the WHOLE receipt as a bitmap with
 * Android's text engine, then sending it as a raster image (see [RasterEscPos]).
 *
 * Why an image and not text: the printer's ROM font is ASCII/Latin only and can't
 * shape Indic scripts, so Malayalam / Hindi / Tamil / Kannada printed as text come
 * out blank. Drawing with `Canvas`/`Paint` uses the device's system fonts, which
 * both cover these scripts and shape them correctly (conjuncts, vowel signs), and
 * lets us print the real ₹ symbol. Devices that display these scripts on screen
 * have the fonts; bundled fonts in res/font/ could be added later if any printer-
 * target device is missing one.
 *
 * @param dotsWide printable width in dots: 384 for 58mm paper, 576 for 80mm.
 */
class ReceiptRenderer(private val dotsWide: Int) {

    /**
     * @param logo already-decoded shop logo, or null to print without one. The
     *   caller fetches it (see PrinterController) so a slow or missing image can
     *   never stop a bill from printing.
     */
    fun build(bill: BillDetail, autoCut: Boolean, logo: Bitmap? = null): ByteArray =
        wrap(renderToBitmap(billBlocks(bill, logo)), autoCut)

    /** The rendered receipt bitmap, for on-screen preview / instrumented tests. */
    internal fun renderBitmap(bill: BillDetail, logo: Bitmap? = null): Bitmap =
        renderToBitmap(billBlocks(bill, logo))

    fun buildTest(connectionLabel: String, autoCut: Boolean): ByteArray {
        val blocks = listOf(
            Para("TEST PRINT", pHeader, center = true),
            Gap(8),
            Para("Thermal printer connected!", pBody, center = true),
            Para("Connection: $connectionLabel", pSmall, center = true),
            Divider(),
            // Doubles as a script check: if these render, multilingual bills will too.
            Para("മലയാളം · हिन्दी · தமிழ் · ಕನ್ನಡ", pBody, center = true),
            Para("English · ₹1234.50", pBody, center = true),
            Divider(),
        )
        return wrap(renderToBitmap(blocks), autoCut)
    }

    // ── Assembly ─────────────────────────────────────────────────────────────
    private fun wrap(bmp: Bitmap, autoCut: Boolean): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(RasterEscPos.INIT)
        out.write(byteArrayOf(0x1b, 0x61, 0x00)) // ESC a 0 — left align the raster
        out.write(RasterEscPos.raster(toMono(bmp), bmp.width, bmp.height))
        bmp.recycle()
        out.write("\n\n\n".toByteArray(Charsets.US_ASCII)) // feed clear of the tear bar
        if (autoCut) out.write(RasterEscPos.CUT)
        return out.toByteArray()
    }

    private fun renderToBitmap(blocks: List<Block>): Bitmap {
        val total = (TOP_PAD + blocks.sumOf { it.height() } + BOTTOM_PAD).coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(dotsWide, total, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        var y = TOP_PAD
        for (block in blocks) {
            block.draw(canvas, y)
            y += block.height()
        }
        return bmp
    }

    /** Threshold to 1-bpp: dark pixels → black dot, transparent treated as white. */
    private fun toMono(bmp: Bitmap): BooleanArray {
        val w = bmp.width
        val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        val mono = BooleanArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val a = (p ushr 24) and 0xff
            val r = (p ushr 16) and 0xff
            val g = (p ushr 8) and 0xff
            val b = p and 0xff
            val lum = if (a < 128) 255 else (r * 30 + g * 59 + b * 11) / 100
            mono[i] = lum < 128
        }
        return mono
    }

    // ── Content ──────────────────────────────────────────────────────────────
    private fun billBlocks(bill: BillDetail, logo: Bitmap?): List<Block> = buildList {
        logo?.let { add(LogoBlock(it)) }
        add(Para((bill.businessName ?: bill.shopName ?: "NURSERY RECEIPT"), pHeader, center = true))
        bill.businessAddress?.trim()?.takeIf { it.isNotBlank() }?.let { add(Para(it, pSmall, center = true)) }
        bill.businessPhone?.trim()?.takeIf { it.isNotBlank() }?.let { add(Para("Contact: $it", pSmall, center = true)) }
        add(Divider())

        add(Para("Bill: #${bill.billNo ?: bill.id.take(8).uppercase()}", pSmall, center = false))
        add(Para("Date: ${formatReceiptDateTime(bill.createdAt)}", pSmall, center = false))
        bill.salespersonEmail?.let { add(Para("Staff: $it", pSmall, center = false)) }
        bill.customerName?.let { name ->
            val v = if (bill.customerPhone != null) "$name (${bill.customerPhone})" else name
            add(Para("Customer: $v", pSmall, center = false))
        }
        bill.remarks?.trim()?.takeIf { it.isNotBlank() }?.let { add(Para("Remarks: $it", pSmall, center = false)) }
        add(Divider())

        bill.items.forEach { item ->
            add(Para(item.productName, pBold, center = false))
            add(TwoCol("  ${item.quantity} x ${money(item.unitPrice)}", money(item.lineTotal), pBody))
        }
        add(Divider())

        add(TwoCol("Subtotal", money(bill.subtotal), pBody))
        if (bill.discountAmount.isPositive()) {
            val label = if (bill.discountType == DiscountType.PERCENT) {
                "Discount (${bill.discountValue.toWire()}%)"
            } else "Discount"
            add(TwoCol(label, "-${money(bill.discountAmount)}", pBody))
        }
        add(Divider())
        add(TwoCol("TOTAL", money(bill.total), pTotal))
        add(Divider())

        if (bill.cashAmount.isPositive()) add(TwoCol("Paid via Cash", money(bill.cashAmount), pBody))
        if (bill.upiAmount.isPositive()) add(TwoCol("Paid via UPI", money(bill.upiAmount), pBody))
        if (bill.dueAmount.isPositive()) add(TwoCol("Remaining Due", money(bill.dueAmount), pBody))
        if (!bill.cashAmount.isPositive() && !bill.upiAmount.isPositive() && !bill.dueAmount.isPositive()) {
            add(TwoCol("Paid via Cash", money(Money.ZERO), pBody))
        }
        add(Divider())

        add(Gap(8))
        add(Para("Thank you for shopping with us!", pSmall, center = true))
        add(Para("Please visit us again.", pSmall, center = true))
    }

    private fun money(m: Money): String = "₹" + m.toWire()

    // ── Blocks ───────────────────────────────────────────────────────────────
    private interface Block {
        fun height(): Int
        fun draw(canvas: Canvas, top: Int)
    }

    /** Wrapped, optionally centered text. StaticLayout does the Indic shaping. */
    private inner class Para(text: String, paint: TextPaint, center: Boolean) : Block {
        private val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, dotsWide)
            .setAlignment(if (center) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()

        override fun height() = layout.height + LINE_GAP
        override fun draw(canvas: Canvas, top: Int) {
            canvas.save()
            canvas.translate(0f, top.toFloat())
            layout.draw(canvas)
            canvas.restore()
        }
    }

    /** A label on the left and a right-aligned value (amounts, totals). */
    private inner class TwoCol(
        private val left: String,
        private val right: String,
        private val paint: TextPaint,
    ) : Block {
        private val fm = paint.fontMetricsInt
        override fun height() = (fm.descent - fm.ascent) + LINE_GAP
        override fun draw(canvas: Canvas, top: Int) {
            val baseline = (top - fm.ascent).toFloat()
            canvas.drawText(left, 0f, baseline, paint)
            canvas.drawText(right, dotsWide - paint.measureText(right), baseline, paint)
        }
    }

    private inner class Divider : Block {
        override fun height() = DIVIDER_H
        override fun draw(canvas: Canvas, top: Int) {
            val y = (top + DIVIDER_H / 2).toFloat()
            canvas.drawLine(0f, y, dotsWide.toFloat(), y, rule)
        }
    }

    private class Gap(private val px: Int) : Block {
        override fun height() = px
        override fun draw(canvas: Canvas, top: Int) {}
    }

    /**
     * The shop logo, scaled to fit the paper and centred.
     *
     * Because the whole receipt is already drawn as a bitmap, this is one more
     * draw call — the ESC/POS raster encoding downstream doesn't know or care
     * that part of the image came from a file.
     */
    private inner class LogoBlock(private val logo: Bitmap) : Block {
        // Never upscale (a small logo blown up just prints blurry) and never let
        // it grow so tall it pushes the bill itself off the paper.
        private val scale = minOf(
            dotsWide.toFloat() / logo.width,
            LOGO_MAX_H.toFloat() / logo.height,
            1f,
        )
        private val w = (logo.width * scale).toInt().coerceAtLeast(1)
        private val h = (logo.height * scale).toInt().coerceAtLeast(1)

        override fun height() = h + LOGO_GAP
        override fun draw(canvas: Canvas, top: Int) {
            val dest = android.graphics.Rect((dotsWide - w) / 2, top, (dotsWide - w) / 2 + w, top + h)
            canvas.drawBitmap(logo, null, dest, imagePaint)
        }
    }

    // ── Paints ───────────────────────────────────────────────────────────────
    private fun textPaint(sizePx: Float, bold: Boolean) = TextPaint().apply {
        isAntiAlias = true
        color = Color.BLACK
        textSize = sizePx
        textAlign = Paint.Align.LEFT
        typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
    }

    // Sizes are in printer dots (≈203 dpi). Kept the same across paper widths so
    // text is the same physical size on 58mm and 80mm — 80mm just fits more.
    private val pBody = textPaint(28f, bold = false)
    private val pBold = textPaint(28f, bold = true)
    private val pSmall = textPaint(24f, bold = false)
    private val pHeader = textPaint(46f, bold = true)
    private val pTotal = textPaint(40f, bold = true)
    private val rule = Paint().apply { color = Color.BLACK; strokeWidth = 2f }
    // Filtering matters here: the logo is almost always downscaled, and nearest-
    // neighbour sampling of thin artwork drops whole strokes.
    private val imagePaint = Paint().apply { isAntiAlias = true; isFilterBitmap = true }

    private companion object {
        const val TOP_PAD = 16
        const val BOTTOM_PAD = 40
        const val DIVIDER_H = 18
        const val LINE_GAP = 6
        const val LOGO_MAX_H = 200
        const val LOGO_GAP = 12
    }
}
