package com.plantora.billing.ui.sales

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantora.billing.data.BillRepository
import com.plantora.billing.data.remote.friendlyError
import com.plantora.billing.domain.BillDetail
import com.plantora.billing.print.PrinterController
import com.plantora.billing.ui.billing.PrintPhase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BillDetailUiState(
    val loading: Boolean = true,
    val detail: BillDetail? = null,
    val error: String? = null,
    val printPhase: PrintPhase = PrintPhase.IDLE,
    val printMessage: String? = null,
    val deleted: Boolean = false,
)

@HiltViewModel
class BillDetailViewModel @Inject constructor(
    private val billRepo: BillRepository,
    private val printer: PrinterController,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val billId: String = checkNotNull(savedStateHandle["billId"])

    private val _ui = MutableStateFlow(BillDetailUiState())
    val ui: StateFlow<BillDetailUiState> = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { billRepo.detail(billId) }
                .onSuccess { d -> _ui.update { it.copy(loading = false, detail = d) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = friendlyError(e)) } }
        }
    }

    fun print() {
        val detail = _ui.value.detail ?: return
        _ui.update { it.copy(printPhase = PrintPhase.CONNECTING, printMessage = "Connecting to printer…") }
        viewModelScope.launch {
            val statusJob = launch {
                printer.status.collect { st ->
                    when (st) {
                        is com.plantora.billing.print.PrinterStatus.Connecting ->
                            _ui.update { it.copy(printPhase = PrintPhase.CONNECTING, printMessage = "Connecting to printer…") }
                        is com.plantora.billing.print.PrinterStatus.Connected ->
                            _ui.update { it.copy(printPhase = PrintPhase.PRINTING, printMessage = "Printing…") }
                        else -> {}
                    }
                }
            }
            val result = printer.printBill(detail)
            statusJob.cancel()
            result
                .onSuccess { _ui.update { it.copy(printPhase = PrintPhase.DONE, printMessage = "Printed.") } }
                .onFailure { e -> _ui.update { it.copy(printPhase = PrintPhase.FAILED, printMessage = e.message ?: "Printing failed.") } }
        }
    }

    fun delete() {
        viewModelScope.launch {
            runCatching { billRepo.delete(billId) }
                .onSuccess { _ui.update { it.copy(deleted = true) } }
                .onFailure { e -> _ui.update { it.copy(printMessage = friendlyError(e)) } }
        }
    }
}
