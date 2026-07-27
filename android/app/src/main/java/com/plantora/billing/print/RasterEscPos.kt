package com.plantora.billing.print

import java.io.ByteArrayOutputStream

/**
 * Pure ESC/POS raster helpers — no Android dependencies, so this stays
 * unit-testable on the JVM. The receipt is drawn to a monochrome pixel buffer by
 * [ReceiptRenderer] (which needs Android's text engine for Indic shaping) and
 * handed here to be packed into `GS v 0` raster commands.
 *
 * Printing as an image is the whole point: cheap thermal printers only carry
 * ASCII/Latin glyphs in ROM and can't shape complex scripts, so Malayalam / Hindi
 * / Tamil / Kannada text must be rasterised on the phone and sent as dots.
 */
object RasterEscPos {

    /** ESC @ — initialise the printer. */
    val INIT: ByteArray = byteArrayOf(0x1b, 0x40)

    /** GS V 66 0 — feed and full cut. */
    val CUT: ByteArray = byteArrayOf(0x1d, 0x56, 0x42, 0x00)

    /**
     * Pack a monochrome image into one or more `GS v 0` raster commands.
     *
     * @param mono row-major pixels, `mono[y * width + x] == true` means a black dot.
     * @param bandRows rows per command; tall receipts are split into horizontal
     *   bands so a single command never exceeds the printer's raster buffer.
     */
    fun raster(mono: BooleanArray, width: Int, height: Int, bandRows: Int = 128): ByteArray {
        require(mono.size == width * height) { "mono size ${mono.size} != $width x $height" }
        val bytesPerRow = (width + 7) / 8
        val out = ByteArrayOutputStream()
        var row = 0
        while (row < height) {
            val rows = minOf(bandRows, height - row)
            // GS v 0 m xL xH yL yH
            out.write(0x1d); out.write(0x76); out.write(0x30); out.write(0x00)
            out.write(bytesPerRow and 0xff); out.write((bytesPerRow ushr 8) and 0xff)
            out.write(rows and 0xff); out.write((rows ushr 8) and 0xff)

            val band = ByteArray(bytesPerRow * rows)
            for (r in 0 until rows) {
                val srcY = row + r
                val base = r * bytesPerRow
                for (x in 0 until width) {
                    if (mono[srcY * width + x]) {
                        val idx = base + (x ushr 3)
                        band[idx] = (band[idx].toInt() or (0x80 ushr (x and 7))).toByte()
                    }
                }
            }
            out.write(band)
            row += rows
        }
        return out.toByteArray()
    }
}
