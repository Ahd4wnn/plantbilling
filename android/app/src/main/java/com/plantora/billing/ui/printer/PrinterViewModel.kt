package com.plantora.billing.ui.printer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantora.billing.data.local.AppPreferences
import com.plantora.billing.print.PrinterController
import com.plantora.billing.print.PrinterDevice
import com.plantora.billing.print.PrinterStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PrinterScreenState(
    val bluetoothSupported: Boolean = true,
    val bluetoothEnabled: Boolean = true,
    val needsPermission: Boolean = false,
    val devices: List<PrinterDevice> = emptyList(),
    val selecting: String? = null, // mac being verified/selected
    val selectedMac: String? = null, // remembered default printer
    val paperWidthChars: Int = 32,
    val autoCut: Boolean = true,
    val message: String? = null,
    val busy: Boolean = false,
) {
    val selectedDevice: PrinterDevice? get() = devices.firstOrNull { it.mac == selectedMac }
}

@HiltViewModel
class PrinterViewModel @Inject constructor(
    private val controller: PrinterController,
    private val prefs: AppPreferences,
) : ViewModel() {

    private val _ui = MutableStateFlow(PrinterScreenState())
    val ui: StateFlow<PrinterScreenState> = _ui.asStateFlow()

    val status: StateFlow<PrinterStatus> = controller.status.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), PrinterStatus.Disconnected,
    )

    init {
        viewModelScope.launch {
            combine(prefs.paperWidthChars, prefs.autoCut) { w, c -> w to c }.collect { (w, c) ->
                _ui.update { it.copy(paperWidthChars = w, autoCut = c) }
            }
        }
        viewModelScope.launch {
            controller.rememberedMac.collect { mac -> _ui.update { it.copy(selectedMac = mac) } }
        }
        refresh()
    }

    fun refresh() {
        _ui.update {
            it.copy(
                bluetoothSupported = controller.isBluetoothSupported,
                bluetoothEnabled = controller.isBluetoothEnabled,
                needsPermission = !controller.hasConnectPermission(),
                devices = controller.pairedPrinters(),
            )
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _ui.update { it.copy(needsPermission = !granted) }
        if (granted) refresh()
        else _ui.update { it.copy(message = "Bluetooth permission is needed to use a printer.") }
    }

    /** Choose this printer as the phone's default (verifies, then releases it). */
    fun select(device: PrinterDevice) {
        _ui.update { it.copy(selecting = device.mac, message = null) }
        viewModelScope.launch {
            controller.selectPrinter(device)
                .onSuccess { _ui.update { it.copy(selecting = null, message = "Ready to print on ${device.name}.") } }
                .onFailure { e -> _ui.update { it.copy(selecting = null, message = e.message) } }
        }
    }

    fun testPrint() {
        _ui.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            controller.printTest()
                .onSuccess { _ui.update { it.copy(busy = false, message = "Test page printed.") } }
                .onFailure { e -> _ui.update { it.copy(busy = false, message = e.message) } }
        }
    }

    fun setPaperWidth(chars: Int) = viewModelScope.launch { prefs.setPaperWidthChars(chars) }
    fun setAutoCut(enabled: Boolean) = viewModelScope.launch { prefs.setAutoCut(enabled) }
    fun dismissMessage() = _ui.update { it.copy(message = null) }
}
