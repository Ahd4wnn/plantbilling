package com.plantora.billing.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders [content] as a QR code, generated locally (offline) with ZXing — no
 * network call, unlike the web's external QR API. The customer scans it with any
 * UPI app and the amount is pre-filled from the embedded `upi://pay` link.
 *
 * The bitmap is built OFF the main thread (produceState + Dispatchers.Default) and
 * with a single bulk `setPixels`, not size*size individual `setPixel` calls — the
 * old per-pixel loop (409k calls at 640px) froze the UI for up to a few seconds
 * whenever the payment QR appeared.
 */
@Composable
fun UpiQrCode(content: String, sizePx: Int = 640, modifier: Modifier = Modifier) {
    val bitmap by produceState<Bitmap?>(initialValue = null, content, sizePx) {
        value = withContext(Dispatchers.Default) { encodeQr(content, sizePx) }
    }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = "UPI payment QR code",
            modifier = modifier,
        )
    }
}

private fun encodeQr(content: String, size: Int): Bitmap? = runCatching {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 1,
    )
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    // Fill one int[] and hand it to the bitmap in a single call — orders of
    // magnitude faster than size*size individual setPixel() calls.
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        val offset = y * size
        for (x in 0 until size) {
            pixels[offset + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
        }
    }
    Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).apply {
        setPixels(pixels, 0, size, 0, 0, size, size)
    }
}.getOrNull()

/** Builds a standard UPI deep link the customer's app understands. */
fun buildUpiUri(upiId: String, payeeName: String, amount: String): String {
    fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
    return "upi://pay?pa=${enc(upiId)}&pn=${enc(payeeName)}&am=$amount&cu=INR"
}
