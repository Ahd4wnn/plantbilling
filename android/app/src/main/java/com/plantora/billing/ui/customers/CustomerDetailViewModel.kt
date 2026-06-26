package com.plantora.billing.ui.customers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantora.billing.data.BillRepository
import com.plantora.billing.data.remote.friendlyError
import com.plantora.billing.domain.BillListEntry
import com.plantora.billing.domain.Money
import com.plantora.billing.domain.PaymentMethod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CustomerDetailUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val name: String = "Customer",
    val bills: List<BillListEntry> = emptyList(),
    val totalSpent: Money = Money.ZERO,
    val creditBillCount: Int = 0,
)

@HiltViewModel
class CustomerDetailViewModel @Inject constructor(
    private val billRepo: BillRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val customerId: String = checkNotNull(savedStateHandle["customerId"])

    private val _ui = MutableStateFlow(CustomerDetailUiState())
    val ui: StateFlow<CustomerDetailUiState> = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { billRepo.listByCustomer(customerId) }
                .onSuccess { page ->
                    val spent = page.items.fold(Money.ZERO) { acc, b -> acc + b.total }
                    val credit = page.items.count { it.paymentMethod == PaymentMethod.DUE }
                    val name = page.items.firstOrNull { !it.customerName.isNullOrBlank() }?.customerName ?: "Customer"
                    _ui.update {
                        it.copy(
                            loading = false,
                            name = name,
                            bills = page.items,
                            totalSpent = spent,
                            creditBillCount = credit,
                        )
                    }
                }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = friendlyError(e)) } }
        }
    }
}
