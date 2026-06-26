package com.plantora.billing.ui.sales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantora.billing.data.BillRepository
import com.plantora.billing.data.remote.friendlyError
import com.plantora.billing.domain.BillListEntry
import com.plantora.billing.domain.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Outstanding dues across the whole shop (shared by all salespeople). Lists every
 * bill still owing money; collecting it settles the due into cash or UPI so it
 * disappears from this list and is reflected in the day's takings.
 */
data class DuesUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val dues: List<BillListEntry> = emptyList(),
    val settlingId: String? = null,
    val message: String? = null,
) {
    val totalOwed: Money get() = dues.fold(Money.ZERO) { acc, b -> acc + b.dueAmount }
}

@HiltViewModel
class DuesViewModel @Inject constructor(
    private val billRepo: BillRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(DuesUiState())
    val ui: StateFlow<DuesUiState> = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { billRepo.listDues() }
                .onSuccess { page -> _ui.update { it.copy(loading = false, dues = page.items) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = friendlyError(e)) } }
        }
    }

    /** Record that the owed amount was collected, via cash or UPI. */
    fun markPaid(entry: BillListEntry, viaUpi: Boolean) {
        if (_ui.value.settlingId != null) return
        _ui.update { it.copy(settlingId = entry.id) }
        viewModelScope.launch {
            runCatching {
                val detail = billRepo.detail(entry.id)
                billRepo.settleDue(detail, viaUpi)
            }
                .onSuccess {
                    _ui.update { s ->
                        s.copy(
                            settlingId = null,
                            dues = s.dues.filterNot { it.id == entry.id },
                            message = "Marked paid — ${entry.dueAmount.format()} collected.",
                        )
                    }
                }
                .onFailure { e -> _ui.update { it.copy(settlingId = null, message = friendlyError(e, "Couldn't update the due.")) } }
        }
    }

    fun dismissMessage() = _ui.update { it.copy(message = null) }
}
