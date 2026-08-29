package com.plantora.billing.print

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.plantora.billing.data.local.AppPreferences
import com.plantora.billing.data.resolveMediaUrl
import com.plantora.billing.domain.BillDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level printing facade.
 *
 * The shared-printer model (the whole reason this is a native app): one Bluetooth
 * thermal printer is used by several salespeople's phones. A classic-SPP printer
 * accepts only ONE connection at a time, so we must never hold it open. Every
 * print is a self-contained **connect → write → disconnect** cycle, serialized by
 * a mutex, that releases the printer the instant it's done — leaving it free for
 * the next phone. The chosen printer is only *remembered* (its MAC), never kept
 * connected between prints.
 */
@Singleton
class PrinterController @Inject constructor(
    private val manager: BluetoothPrinterManager,
    private val prefs: AppPreferences,
) {
    val status: StateFlow<PrinterStatus> = manager.status

    /** The remembered printer's MAC, if one has been chosen. */
    val rememberedMac: Flow<String?> = prefs.lastPrinterMac

    val isBluetoothSupported: Boolean get() = manager.isBluetoothSupported
    val isBluetoothEnabled: Boolean get() = manager.isBluetoothEnabled

    fun hasConnectPermission() = manager.hasConnectPermission()
    fun pairedPrinters() = manager.pairedPrinters()

    // Serializes connect→print→disconnect so two taps on one phone never overlap.
    private val printMutex = Mutex()

    /**
     * Choose a printer for this phone. We verify it connects right now (then
     * immediately release it) so the salesperson gets instant confirmation, and
     * only remember it once that succeeds.
     */
    suspend fun selectPrinter(device: PrinterDevice): Result<PrinterDevice> = printMutex.withLock {
        val result = connectWithRetry(device.mac)
        manager.disconnect()
        if (result.isSuccess) prefs.setLastPrinterMac(device.mac)
        result
    }

    fun disconnect() = manager.disconnect()

    /** The shared printer may have just been released by another phone, so retry briefly. */
    private suspend fun connectWithRetry(mac: String, attempts: Int = 3): Result<PrinterDevice> {
        var last: Result<PrinterDevice> =
            Result.failure(PrinterException("Couldn't connect to the printer."))
        repeat(attempts) { i ->
            last = manager.connect(mac)
            if (last.isSuccess) return last
            if (i < attempts - 1) delay(500)
        }
        return last
    }

    /** connect → write → ALWAYS disconnect, freeing the shared printer immediately. */
    private suspend fun printSession(buildBytes: suspend () -> ByteArray): Result<Unit> =
        printMutex.withLock {
            val mac = prefs.lastPrinterMac.first()
                ?: return@withLock Result.failure(
                    PrinterException("No printer chosen yet. Pick one in Printer settings."),
                )
            val connect = connectWithRetry(mac)
            if (connect.isFailure) {
                return@withLock Result.failure(
                    connect.exceptionOrNull() ?: PrinterException("Couldn't connect to the printer."),
                )
            }
            try {
                manager.write(buildBytes())
            } finally {
                // Release no matter what — success, write failure, or exception.
                manager.disconnect()
            }
        }

    suspend fun printBill(bill: BillDetail): Result<Unit> = printSession {
        val dots = dotsFor(prefs.paperWidthChars.first())
        val autoCut = prefs.autoCut.first()
        val logo = fetchLogo(bill.businessLogoUrl)
        try {
            ReceiptRenderer(dots).build(bill, autoCut, logo)
        } finally {
            logo?.recycle()
        }
    }

    /**
     * Download and decode the shop's logo, or return null.
     *
     * Everything here fails soft. A logo is decoration; a bill that won't print
     * because the shop's wifi dropped while fetching an image is a real problem in
     * front of a waiting customer. Any failure — offline, 404, corrupt file, slow
     * server — just prints the receipt without it.
     *
     * The server has already applied the shop's on/off switch, so a non-null URL
     * here means "print this".
     */
    private suspend fun fetchLogo(rawUrl: String?): Bitmap? = withContext(Dispatchers.IO) {
        val url = resolveMediaUrl(rawUrl, prefs.baseUrl.first()) ?: return@withContext null
        runCatching {
            val client = OkHttpClient.Builder()
                .callTimeout(5, TimeUnit.SECONDS)
                .build()
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.byteStream()?.use { BitmapFactory.decodeStream(it) }
            }
        }.getOrNull()
    }

    suspend fun printTest(): Result<Unit> = printSession {
        val dots = dotsFor(prefs.paperWidthChars.first())
        val autoCut = prefs.autoCut.first()
        val mac = prefs.lastPrinterMac.first()
        val label = pairedPrinters().firstOrNull { it.mac == mac }?.name ?: "Bluetooth"
        ReceiptRenderer(dots).buildTest(label, autoCut)
    }

    /** Printable width in dots for the receipt bitmap: 384 for 58mm, 576 for 80mm. */
    private fun dotsFor(paperWidthChars: Int): Int = if (paperWidthChars >= 48) 576 else 384
}
