package com.plantora.billing.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantora.billing.data.ShopRepository
import com.plantora.billing.data.local.AppPreferences
import com.plantora.billing.data.remote.friendlyError
import com.plantora.billing.domain.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The "Set cash in hand" dialog (manager only). */
data class CashSetUi(
    val open: Boolean = false,
    val amount: String = "",
    val saving: Boolean = false,
    val error: String? = null,
    val message: String? = null,
) {
    val canSave: Boolean get() = amount.isNotBlank() && !Money.parse(amount).isNegative() && !saving
}

@HiltViewModel
class MoreViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val shopRepo: ShopRepository,
) : ViewModel() {

    /** Device preference: show cash in hand as a running all-time total. */
    val cumulative: StateFlow<Boolean> =
        prefs.cashInHandCumulative.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setCumulative(enabled: Boolean) {
        viewModelScope.launch { prefs.setCashInHandCumulative(enabled) }
    }

    private val _cashSet = MutableStateFlow(CashSetUi())
    val cashSet: StateFlow<CashSetUi> = _cashSet.asStateFlow()

    fun openCashSet() = _cashSet.update { CashSetUi(open = true) }
    fun closeCashSet() = _cashSet.update { CashSetUi(open = false) }
    fun setCashAmount(v: String) = _cashSet.update { it.copy(amount = v, error = null) }

    fun confirmCashSet() {
        val s = _cashSet.value
        if (!s.canSave) return
        _cashSet.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching { shopRepo.setCashInHand(Money.parse(s.amount)) }
                .onSuccess { running ->
                    _cashSet.update { CashSetUi(open = false, message = "Cash in hand set to ${running.format()}.") }
                }
                .onFailure { e ->
                    _cashSet.update { it.copy(saving = false, error = friendlyError(e, "Couldn't set cash in hand.")) }
                }
        }
    }

    fun dismissMessage() = _cashSet.update { it.copy(message = null) }
}
