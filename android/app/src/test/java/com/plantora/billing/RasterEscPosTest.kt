package com.plantora.billing

import com.plantora.billing.print.RasterEscPos
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class RasterEscPosTest {

    @Test fun init_and_cut_commands() {
        assertArrayEquals(byteArrayOf(0x1b, 0x40), RasterEscPos.INIT)
        assertArrayEquals(byteArrayOf(0x1d, 0x56, 0x42, 0x00), RasterEscPos.CUT)
    }

    @Test fun raster_header_encodes_width_bytes_and_height() {
        // 16px wide, 1 row, all black.
        val mono = BooleanArray(16) { true }
        val bytes = RasterEscPos.raster(mono, width = 16, height = 1)
        // GS v 0 m xL xH yL yH
        assertEquals(0x1d.toByte(), bytes[0])
        assertEquals(0x76.toByte(), bytes[1])
        assertEquals(0x30.toByte(), bytes[2])
        assertEquals(0x00.toByte(), bytes[3])
        assertEquals(2.toByte(), bytes[4]) // xL: 16/8 = 2 bytes per row
        assertEquals(0.toByte(), bytes[5]) // xH
        assertEquals(1.toByte(), bytes[6]) // yL
        assertEquals(0.toByte(), bytes[7]) // yH
        assertEquals(0xFF.toByte(), bytes[8]) // both data bytes fully set
        assertEquals(0xFF.toByte(), bytes[9])
        assertEquals(10, bytes.size)
    }

    @Test fun raster_sets_most_significant_bit_first() {
        // 8px wide, only leftmost pixel black -> 0x80.
        val mono = BooleanArray(8) { it == 0 }
        val bytes = RasterEscPos.raster(mono, width = 8, height = 1)
        assertEquals(0x80.toByte(), bytes[8])
    }

    @Test fun raster_pads_width_to_whole_bytes() {
        // 9px wide -> 2 bytes per row; only bit for x=8 set -> second byte 0x80.
        val mono = BooleanArray(9) { it == 8 }
        val bytes = RasterEscPos.raster(mono, width = 9, height = 1)
        assertEquals(2.toByte(), bytes[4]) // xL bytes per row
        assertEquals(0x00.toByte(), bytes[8]) // first byte empty
        assertEquals(0x80.toByte(), bytes[9]) // x=8 lands in the high bit of byte 2
    }

    @Test fun raster_splits_tall_images_into_bands() {
        val w = 8
        val h = 200
        val bytes = RasterEscPos.raster(BooleanArray(w * h), w, h, bandRows = 128)
        // Count GS v 0 headers: 200 rows / 128 -> 2 bands.
        var count = 0
        var i = 0
        while (i + 2 < bytes.size) {
            if (bytes[i] == 0x1d.toByte() && bytes[i + 1] == 0x76.toByte() && bytes[i + 2] == 0x30.toByte()) count++
            i++
        }
        assertEquals(2, count)
    }
}
