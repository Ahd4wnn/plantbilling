package com.plantora.billing.print

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** A paired Bluetooth device the user can print to. */
data class PrinterDevice(val name: String, val mac: String)

sealed interface PrinterStatus {
    data object Disconnected : PrinterStatus
    data object Connecting : PrinterStatus
    data class Connected(val device: PrinterDevice) : PrinterStatus
}

/**
 * Native classic-Bluetooth (SPP) thermal printer connection. This is the core
 * reason for going native: a reliable RFCOMM socket instead of flaky Web
 * Bluetooth. Callers must hold BLUETOOTH_CONNECT (API 31+) before connecting;
 * methods fail gracefully with a clear message otherwise.
 */
@Singleton
class BluetoothPrinterManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val adapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private var socket: BluetoothSocket? = null
    private var stream: OutputStream? = null

    private val _status = MutableStateFlow<PrinterStatus>(PrinterStatus.Disconnected)
    val status: StateFlow<PrinterStatus> = _status.asStateFlow()

    val isBluetoothSupported: Boolean get() = adapter != null
    val isBluetoothEnabled: Boolean get() = adapter?.isEnabled == true

    fun hasConnectPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_CONNECT,
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun pairedPrinters(): List<PrinterDevice> {
        if (!hasConnectPermission()) return emptyList()
        return try {
            adapter?.bondedDevices.orEmpty().map {
                PrinterDevice(name = it.name ?: "Printer", mac = it.address)
            }
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(mac: String): Result<PrinterDevice> = withContext(Dispatchers.IO) {
        if (!hasConnectPermission()) {
            return@withContext Result.failure(PrinterException("Bluetooth permission is needed to connect."))
        }
        val ad = adapter ?: return@withContext Result.failure(PrinterException("This device has no Bluetooth."))
        if (!ad.isEnabled) return@withContext Result.failure(PrinterException("Please turn on Bluetooth."))

        _status.value = PrinterStatus.Connecting
        disconnectQuietly()
        try {
            val device: BluetoothDevice = ad.getRemoteDevice(mac)
            val sock = device.createRfcommSocketToServiceRecord(sppUuid)
            ad.cancelDiscovery()
            sock.connect()
            socket = sock
            stream = sock.outputStream
            val printer = PrinterDevice(name = device.name ?: "Printer", mac = mac)
            _status.value = PrinterStatus.Connected(printer)
            Result.success(printer)
        } catch (e: SecurityException) {
            disconnectQuietly()
            _status.value = PrinterStatus.Disconnected
            Result.failure(PrinterException("Bluetooth permission is needed to connect."))
        } catch (e: Exception) {
            disconnectQuietly()
            _status.value = PrinterStatus.Disconnected
            Result.failure(PrinterException("Couldn't connect to the printer. Make sure it's on and paired."))
        }
    }

    /** Write ESC/POS bytes in throttled chunks so the printer buffer keeps up. */
    suspend fun write(bytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        val os = stream ?: return@withContext Result.failure(PrinterException("No printer connected."))
        try {
            val chunkSize = 256
            var i = 0
            while (i < bytes.size) {
                val end = minOf(i + chunkSize, bytes.size)
                os.write(bytes, i, end - i)
                os.flush()
                Thread.sleep(12)
                i = end
            }
            Result.success(Unit)
        } catch (e: Exception) {
            _status.value = PrinterStatus.Disconnected
            disconnectQuietly()
            Result.failure(PrinterException("Printing failed. Reconnect the printer and try again."))
        }
    }

    fun disconnect() {
        disconnectQuietly()
        _status.value = PrinterStatus.Disconnected
    }

    private fun disconnectQuietly() {
        runCatching { stream?.close() }
        runCatching { socket?.close() }
        stream = null
        socket = null
    }
}

class PrinterException(message: String) : Exception(message)
