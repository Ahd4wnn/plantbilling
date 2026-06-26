package com.plantora.billing.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantora.billing.data.ShopRepository
import com.plantora.billing.data.remote.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShopSettingsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val shopName: String = "",
    val businessName: String = "",
    val businessAddress: String = "",
    val businessPhone: String = "",
    val businessEmail: String = "",
    val businessUpi: String = "",
    val saving: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class ShopSettingsViewModel @Inject constructor(
    private val repo: ShopRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(ShopSettingsUiState())
    val ui: StateFlow<ShopSettingsUiState> = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.get() }
                .onSuccess { s ->
                    _ui.update {
                        it.copy(
                            loading = false,
                            shopName = s.name,
                            businessName = s.businessName.orEmpty(),
                            businessAddress = s.businessAddress.orEmpty(),
                            businessPhone = s.businessPhone.orEmpty(),
                            businessEmail = s.businessEmail.orEmpty(),
                            businessUpi = s.businessUpi.orEmpty(),
                        )
                    }
                }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = friendlyError(e)) } }
        }
    }

    fun setBusinessName(v: String) = _ui.update { it.copy(businessName = v) }
    fun setBusinessAddress(v: String) = _ui.update { it.copy(businessAddress = v) }
    fun setBusinessPhone(v: String) = _ui.update { it.copy(businessPhone = v) }
    fun setBusinessEmail(v: String) = _ui.update { it.copy(businessEmail = v) }
    fun setBusinessUpi(v: String) = _ui.update { it.copy(businessUpi = v) }

    fun save() {
        val s = _ui.value
        if (s.saving) return
        _ui.update { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            runCatching {
                repo.update(
                    businessName = s.businessName,
                    businessAddress = s.businessAddress,
                    businessPhone = s.businessPhone,
                    businessEmail = s.businessEmail,
                    businessUpi = s.businessUpi,
                )
            }
                .onSuccess { _ui.update { it.copy(saving = false, message = "Saved. These appear on printed receipts.") } }
                .onFailure { e -> _ui.update { it.copy(saving = false, message = friendlyError(e)) } }
        }
    }

    fun dismissMessage() = _ui.update { it.copy(message = null) }
}
