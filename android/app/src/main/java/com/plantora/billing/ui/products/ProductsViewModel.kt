package com.plantora.billing.ui.products

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantora.billing.R
import com.plantora.billing.data.ImageCompressor
import com.plantora.billing.data.ImageReadException
import com.plantora.billing.data.ProductRepository
import com.plantora.billing.data.remote.friendlyError
import com.plantora.billing.domain.Money
import com.plantora.billing.domain.Product
import com.plantora.billing.domain.ProductViewMode
import com.plantora.billing.data.local.AppPreferences
import com.plantora.billing.i18n.UiText
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
    val error: UiText? = null,
) {
    val isEdit: Boolean get() = id != null
    // Price may be ₹0 (e.g. a free giveaway plant) — only a blank or negative
    // price blocks saving.
    val canSave: Boolean get() = name.isNotBlank() && priceInput.isNotBlank() && !Money.parse(priceInput).isNegative() && !saving
}

data class ProductsUiState(
    val loading: Boolean = true,
    val products: List<Product> = emptyList(),
    val error: String? = null,
    val query: String = "",
    val categoryFilter: String? = null,
    val showInactive: Boolean = false,
    val form: ProductFormState? = null,
    val message: UiText? = null,
    val bulkSheet: Boolean = false,
    val bulkBusy: Boolean = false,
    /** Blocks or compact list; the same per-device setting the Bill picker uses. */
    val viewMode: ProductViewMode = ProductViewMode.GRID,
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
    private val prefs: AppPreferences,
    private val compressor: ImageCompressor,
) : ViewModel() {

    private val _ui = MutableStateFlow(ProductsUiState())
    val ui: StateFlow<ProductsUiState> = _ui.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            prefs.productViewMode.collect { mode -> _ui.update { it.copy(viewMode = mode) } }
        }
    }

    fun setViewMode(mode: ProductViewMode) {
        viewModelScope.launch { prefs.setProductViewMode(mode) }
    }

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
                .onSuccess { _ui.update { it.copy(form = null, message = UiText.res(if (form.isEdit) R.string.vm_saved else R.string.vm_product_added)) }; load() }
                .onFailure { e -> updateForm { it.copy(saving = false, error = UiText.err(e, R.string.err_generic)) } }
        }
    }

    fun delete(product: Product) {
        viewModelScope.launch {
            runCatching { repo.delete(product.id) }
                .onSuccess { _ui.update { it.copy(message = UiText.res(R.string.vm_product_deleted, product.name)) }; load() }
                .onFailure { e -> _ui.update { it.copy(message = UiText.err(e, R.string.err_generic)) } }
        }
    }

    /**
     * Shrink the picked photo, then upload it. Compression happens off the main
     * thread inside [ImageCompressor]; it is what keeps the request small enough
     * to survive the server's size cap and a weak mobile connection.
     */
    fun uploadImage(productId: String, uri: Uri) {
        updateForm { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val image = compressor.compress(uri)
                repo.uploadImage(productId, image.bytes, image.fileName, image.mimeType)
            }
                .onSuccess { updated ->
                    updateForm { it.copy(saving = false, photoUrl = updated.photoUrl) }
                    load()
                }
                .onFailure { e ->
                    val message = if (e is ImageReadException) {
                        UiText.res(R.string.err_photo_unreadable)
                    } else {
                        UiText.err(e, R.string.err_generic)
                    }
                    updateForm { it.copy(saving = false, error = message) }
                }
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
                .onSuccess { name -> _ui.update { it.copy(bulkBusy = false, message = UiText.res(R.string.vm_saved_downloads, name)) } }
                .onFailure { e -> _ui.update { it.copy(bulkBusy = false, message = UiText.err(e, R.string.err_generic)) } }
        }
    }

    fun uploadSpreadsheet(bytes: ByteArray, fileName: String, mime: String) {
        _ui.update { it.copy(bulkBusy = true) }
        viewModelScope.launch {
            runCatching { repo.bulkUpload(bytes, fileName, mime) }
                .onSuccess { r -> _ui.update { it.copy(bulkBusy = false, bulkSheet = false, message = UiText.of(r.detail)) }; load() }
                .onFailure { e -> _ui.update { it.copy(bulkBusy = false, message = UiText.err(e, R.string.err_generic)) } }
        }
    }

    fun uploadPhotos(bytes: ByteArray, fileName: String, mime: String) {
        _ui.update { it.copy(bulkBusy = true) }
        viewModelScope.launch {
            runCatching { repo.bulkPhotos(bytes, fileName, mime) }
                .onSuccess { r -> _ui.update { it.copy(bulkBusy = false, bulkSheet = false, message = UiText.of(r.detail)) }; load() }
                .onFailure { e -> _ui.update { it.copy(bulkBusy = false, message = UiText.err(e, R.string.err_generic)) } }
        }
    }
}
