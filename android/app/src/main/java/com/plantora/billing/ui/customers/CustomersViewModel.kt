package com.plantora.billing.ui.customers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantora.billing.data.CustomerRepository
import com.plantora.billing.data.remote.friendlyError
import com.plantora.billing.domain.Customer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CustomersUiState(
    val loading: Boolean = true,
    val customers: List<Customer> = emptyList(),
    val error: String? = null,
    val query: String = "",
) {
    val visible: List<Customer>
        get() = if (query.isBlank()) customers
        else customers.filter {
            it.name.contains(query.trim(), ignoreCase = true) ||
                (it.phone?.contains(query.trim()) == true)
        }
}

@HiltViewModel
class CustomersViewModel @Inject constructor(
    private val repo: CustomerRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(CustomersUiState())
    val ui: StateFlow<CustomersUiState> = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.list() }
                .onSuccess { list -> _ui.update { it.copy(loading = false, customers = list) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = friendlyError(e)) } }
        }
    }

    fun onQueryChange(q: String) = _ui.update { it.copy(query = q) }
}
