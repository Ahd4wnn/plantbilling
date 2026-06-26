package com.plantora.billing.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Renders [content] as a QR code, generated locally (offline) with ZXing — no
 * network call, unlike the web's external QR API. The customer scans it with any
 * UPI app and the amount is pre-filled from the embedded `upi://pay` link.
 */
@Composable
fun UpiQrCode(content: String, sizePx: Int = 640, modifier: Modifier = Modifier) {
    val bitmap = remember(content, sizePx) { encodeQr(content, sizePx) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
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
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        }
    }
    bmp
}.getOrNull()

/** Builds a standard UPI deep link the customer's app understands. */
fun buildUpiUri(upiId: String, payeeName: String, amount: String): String {
    fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
    return "upi://pay?pa=${enc(upiId)}&pn=${enc(payeeName)}&am=$amount&cu=INR"
}
