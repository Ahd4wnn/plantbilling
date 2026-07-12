package com.plantora.billing.ui.sales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantora.billing.data.SettlementRepository
import com.plantora.billing.data.remote.friendlyError
import com.plantora.billing.domain.PendingSettlement
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The manager's queue of salesperson due-collections awaiting approval. Approving
 * applies the cash/UPI split to the bill and closes the due; rejecting leaves the
 * due outstanding.
 */
data class ApprovalsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val items: List<PendingSettlement> = emptyList(),
    val actingId: String? = null,
    val message: String? = null,
)

@HiltViewModel
class ApprovalsViewModel @Inject constructor(
    private val repo: SettlementRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(ApprovalsUiState())
    val ui: StateFlow<ApprovalsUiState> = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.pending() }
                .onSuccess { list -> _ui.update { it.copy(loading = false, items = list) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = friendlyError(e)) } }
        }
    }

    fun approve(item: PendingSettlement) = act(item, approve = true)
    fun reject(item: PendingSettlement) = act(item, approve = false)

    private fun act(item: PendingSettlement, approve: Boolean) {
        if (_ui.value.actingId != null) return
        _ui.update { it.copy(actingId = item.id) }
        viewModelScope.launch {
            runCatching { if (approve) repo.approve(item.id) else repo.reject(item.id) }
                .onSuccess {
                    _ui.update { s ->
                        s.copy(
                            actingId = null,
                            items = s.items.filterNot { it.id == item.id },
                            message = if (approve) "Approved — ${item.amount.format()} collected." else "Rejected.",
                        )
                    }
                }
                .onFailure { e -> _ui.update { it.copy(actingId = null, message = friendlyError(e, "Couldn't update the request.")) } }
        }
    }

    fun dismissMessage() = _ui.update { it.copy(message = null) }
}
