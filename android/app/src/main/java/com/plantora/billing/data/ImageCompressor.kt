package com.plantora.billing.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** A photo ready to upload: always JPEG, always small enough to send. */
data class CompressedImage(val bytes: ByteArray, val fileName: String, val mimeType: String)

/**
 * Prepares a picked photo for upload.
 *
 * Shops kept hitting upload failures because the app sent the picked file
 * untouched. That broke three ways at once: a modern 12MP photo is 5–15 MB and
 * blows the server's 5 MB cap; Samsung/Xiaomi/Realme cameras hand back HEIC,
 * which the server rejects outright; and either way the upload crawls on mobile
 * data. Re-encoding here fixes all three — the server only ever receives a small
 * JPEG — and EXIF rotation stops portrait photos arriving sideways.
 *
 * [MAX_EDGE]/[QUALITY] land a typical photo at 200–500 KB, still sharp at the
 * sizes the catalogue ever shows it.
 */
@Singleton
class ImageCompressor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Reads [uri] and returns a JPEG. Throws [ImageReadException] when the photo
     * can't be decoded — on API 24–27 that includes HEIC, which has no platform
     * decoder — so the caller can say something useful instead of uploading bytes
     * the server will refuse.
     */
    suspend fun compress(uri: Uri): CompressedImage = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        // Pass 1: dimensions only, so a huge photo is never fully decoded into memory.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw ImageReadException()
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw ImageReadException()

        // Pass 2: decode subsampled — the cheapest way to get close to the target.
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw ImageReadException()

        val rotated = applyExifRotation(uri, decoded)
        val scaled = scaleToMaxEdge(rotated)

        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
        scaled.recycle()

        CompressedImage(out.toByteArray(), "photo.jpg", "image/jpeg")
    }

    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        // Halve until the next halving would drop below the target — subsampling is
        // power-of-two only, so we finish the job with an exact scale below.
        while (width / (sample * 2) >= MAX_EDGE || height / (sample * 2) >= MAX_EDGE) {
            sample *= 2
        }
        return sample
    }

    private fun applyExifRotation(uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }

    private fun scaleToMaxEdge(bitmap: Bitmap): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= MAX_EDGE) return bitmap
        val ratio = MAX_EDGE.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled != bitmap) bitmap.recycle()
        return scaled
    }

    private companion object {
        const val MAX_EDGE = 1600
        const val QUALITY = 85
    }
}

/** The picked file isn't an image this device can read (e.g. HEIC before API 28). */
class ImageReadException : Exception("Could not read the selected photo")
