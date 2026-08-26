package com.plantora.billing.ui.owner

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantora.billing.data.OwnerRepository
import com.plantora.billing.data.remote.friendlyError
import com.plantora.billing.domain.CustomerDue
import com.plantora.billing.domain.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OwnerDuesState(
    val loading: Boolean = true,
    val error: String? = null,
    val shopName: String = "",
    val customers: List<CustomerDue> = emptyList(),
    /** Which customer's bills are expanded. Only one at a time keeps the list scannable. */
    val expandedId: String? = null,
) {
    val total: Money get() = customers.fold(Money.ZERO) { acc, c -> acc + c.outstanding }
}

/** Read-only: who owes one shop what. Collecting a due stays a shop-side action,
 *  so it keeps going through the manager-approval flow in Sales. */
@HiltViewModel
class OwnerDuesViewModel @Inject constructor(
    private val repo: OwnerRepository,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val shopId: String = checkNotNull(savedState["shopId"])

    private val _ui = MutableStateFlow(OwnerDuesState())
    val ui: StateFlow<OwnerDuesState> = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.shopDues(shopId) to repo.shops().firstOrNull { it.id == shopId }?.name }
                .onSuccess { (customers, name) ->
                    _ui.update { it.copy(loading = false, customers = customers, shopName = name ?: "") }
                }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = friendlyError(e)) } }
        }
    }

    fun toggle(customerKey: String) = _ui.update {
        it.copy(expandedId = if (it.expandedId == customerKey) null else customerKey)
    }
}
