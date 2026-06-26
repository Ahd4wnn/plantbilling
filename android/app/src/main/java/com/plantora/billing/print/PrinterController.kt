package com.plantora.billing.print

import com.plantora.billing.data.local.AppPreferences
import com.plantora.billing.domain.BillDetail
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
        val width = prefs.paperWidthChars.first()
        val autoCut = prefs.autoCut.first()
        EscPosBuilder(width).build(bill, autoCut)
    }

    suspend fun printTest(): Result<Unit> = printSession {
        val width = prefs.paperWidthChars.first()
        val autoCut = prefs.autoCut.first()
        val mac = prefs.lastPrinterMac.first()
        val label = pairedPrinters().firstOrNull { it.mac == mac }?.name ?: "Bluetooth"
        EscPosBuilder(width).buildTest(label, autoCut)
    }
}
