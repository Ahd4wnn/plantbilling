package com.plantora.billing

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.plantora.billing.domain.BillDetail
import com.plantora.billing.domain.BillItem
import com.plantora.billing.domain.DiscountType
import com.plantora.billing.domain.Money
import com.plantora.billing.domain.PaymentMethod
import com.plantora.billing.print.ReceiptRenderer
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Renders a receipt containing Malayalam text and writes the bitmap to the app's
 * external files dir so it can be pulled and eyeballed. Also asserts the bitmap is
 * not blank (proves Indic glyphs actually rasterised, the original bug).
 */
@RunWith(AndroidJUnit4::class)
class ReceiptRenderInstrumentedTest {

    private fun malayalamBill() = BillDetail(
        id = "abcd1234ef",
        shopName = "ഗ്രീൻ ലീഫ്",
        businessName = "ഗ്രീൻ ലീഫ് നഴ്സറി",
        businessAddress = "എം.ജി. റോഡ്, കൊച്ചി",
        businessPhone = "9999999999",
        subtotal = Money.parse("250.00"),
        discountType = DiscountType.FLAT,
        discountValue = Money.parse("50.00"),
        discountAmount = Money.parse("50.00"),
        total = Money.parse("200.00"),
        cashAmount = Money.parse("150.00"),
        upiAmount = Money.parse("50.00"),
        dueAmount = Money.ZERO,
        paymentMethod = PaymentMethod.SPLIT,
        customerName = "രാമൻ",
        customerPhone = "8888888888",
        salespersonEmail = "shop@plantbill.in",
        remarks = "നന്ദി",
        isEdited = false,
        createdAt = "2026-07-27 10:30",
        items = listOf(
            BillItem("p1", "തുളസി ചെടി", Money.parse("100.00"), 2, Money.parse("200.00")),
            BillItem("p2", "റോസ് — deluxe", Money.parse("50.00"), 1, Money.parse("50.00")),
        ),
    )

    @Test fun renders_malayalam_receipt_bitmap() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val bmp = ReceiptRenderer(dotsWide = 384).renderBitmap(malayalamBill())

        // Not blank: at least some black pixels exist (glyphs rasterised).
        val pixels = IntArray(bmp.width * bmp.height)
        bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        val dark = pixels.count { (it and 0x00FFFFFF) < 0x808080 }
        assertTrue("Receipt bitmap looks blank ($dark dark px)", dark > 500)

        val out = File(ctx.getExternalFilesDir(null), "receipt_malayalam.png")
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
