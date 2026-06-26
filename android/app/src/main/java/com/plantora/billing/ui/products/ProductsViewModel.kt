package com.plantora.billing.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantora.billing.data.ProductRepository
import com.plantora.billing.data.remote.friendlyError
import com.plantora.billing.domain.Money
import com.plantora.billing.domain.Product
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backing fields for the create/edit form. id == null means "create". */
data class ProductFormState(
    val id: String? = null,
    val name: String = "",
    val priceInput: String = "",
    val category: String = "",
    val isActive: Boolean = true,
    val photoUrl: String? = null,
    val saving: Boolean = false,
    val error: String? = null,
) {
    val isEdit: Boolean get() = id != null
    val canSave: Boolean get() = name.isNotBlank() && Money.parse(priceInput).isPositive() && !saving
}

data class ProductsUiState(
    val loading: Boolean = true,
    val products: List<Product> = emptyList(),
    val error: String? = null,
    val query: String = "",
    val categoryFilter: String? = null,
    val showInactive: Boolean = false,
    val form: ProductFormState? = null,
    val message: String? = null,
    val bulkSheet: Boolean = false,
    val bulkBusy: Boolean = false,
) {
    val categories: List<String>
        get() = products.mapNotNull { it.category?.takeIf { c -> c.isNotBlank() } }.distinct().sorted()

    val visibleProducts: List<Product>
        get() = products
            .filter { categoryFilter == null || it.category == categoryFilter }
            .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
}

@HiltViewModel
class ProductsViewModel @Inject constructor(
    private val repo: ProductRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(ProductsUiState())
    val ui: StateFlow<ProductsUiState> = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val active = if (_ui.value.showInactive) "all" else "true"
            runCatching { repo.list(active = active) }
                .onSuccess { list -> _ui.update { it.copy(loading = false, products = list) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = friendlyError(e)) } }
        }
    }

    fun onQueryChange(q: String) = _ui.update { it.copy(query = q) }
    fun setCategoryFilter(c: String?) = _ui.update { it.copy(categoryFilter = c) }
    fun toggleShowInactive() {
        _ui.update { it.copy(showInactive = !it.showInactive) }
        load()
    }

    // ── Form ──
    fun openCreate() = _ui.update { it.copy(form = ProductFormState()) }
    fun openEdit(p: Product) = _ui.update {
        it.copy(
            form = ProductFormState(
                id = p.id,
                name = p.name,
                priceInput = p.retailPrice.toWire(),
                category = p.category.orEmpty(),
                isActive = p.isActive,
                photoUrl = p.photoUrl,
            ),
        )
    }
    fun closeForm() = _ui.update { it.copy(form = null) }

    fun updateForm(transform: (ProductFormState) -> ProductFormState) =
        _ui.update { it.copy(form = it.form?.let(transform)) }

    fun save() {
        val form = _ui.value.form ?: return
        if (!form.canSave) return
        updateForm { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val price = Money.parse(form.priceInput)
            val category = form.category.takeIf { it.isNotBlank() }
            val result = runCatching {
                if (form.isEdit) {
                    repo.update(form.id!!, name = form.name, retailPrice = price, category = category, isActive = form.isActive)
                } else {
                    repo.create(name = form.name, retailPrice = price, category = category)
                }
            }
            result
                .onSuccess { _ui.update { it.copy(form = null, message = if (form.isEdit) "Saved." else "Product added.") }; load() }
                .onFailure { e -> updateForm { it.copy(saving = false, error = friendlyError(e)) } }
        }
    }

    fun delete(product: Product) {
        viewModelScope.launch {
            runCatching { repo.delete(product.id) }
                .onSuccess { _ui.update { it.copy(message = "Deleted ${product.name}.") }; load() }
                .onFailure { e -> _ui.update { it.copy(message = friendlyError(e)) } }
        }
    }

    fun uploadImage(productId: String, bytes: ByteArray, fileName: String, mime: String) {
        updateForm { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.uploadImage(productId, bytes, fileName, mime) }
                .onSuccess { updated ->
                    updateForm { it.copy(saving = false, photoUrl = updated.photoUrl) }
                    load()
                }
                .onFailure { e -> updateForm { it.copy(saving = false, error = friendlyError(e)) } }
        }
    }

    fun dismissMessage() = _ui.update { it.copy(message = null) }

    // ── Bulk import ──
    fun openBulk() = _ui.update { it.copy(bulkSheet = true) }
    fun closeBulk() = _ui.update { it.copy(bulkSheet = false) }

    fun downloadSample() {
        _ui.update { it.copy(bulkBusy = true) }
        viewModelScope.launch {
            runCatching { repo.downloadSample() }
                .onSuccess { name -> _ui.update { it.copy(bulkBusy = false, message = "Saved to Downloads: $name") } }
                .onFailure { e -> _ui.update { it.copy(bulkBusy = false, message = friendlyError(e)) } }
        }
    }

    fun uploadSpreadsheet(bytes: ByteArray, fileName: String, mime: String) {
        _ui.update { it.copy(bulkBusy = true) }
        viewModelScope.launch {
            runCatching { repo.bulkUpload(bytes, fileName, mime) }
                .onSuccess { r -> _ui.update { it.copy(bulkBusy = false, bulkSheet = false, message = r.detail) }; load() }
                .onFailure { e -> _ui.update { it.copy(bulkBusy = false, message = friendlyError(e)) } }
        }
    }

    fun uploadPhotos(bytes: ByteArray, fileName: String, mime: String) {
        _ui.update { it.copy(bulkBusy = true) }
        viewModelScope.launch {
            runCatching { repo.bulkPhotos(bytes, fileName, mime) }
                .onSuccess { r -> _ui.update { it.copy(bulkBusy = false, bulkSheet = false, message = r.detail) }; load() }
                .onFailure { e -> _ui.update { it.copy(bulkBusy = false, message = friendlyError(e)) } }
        }
    }
}
