package com.plantora.billing.ui.products

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.plantora.billing.domain.Product
import com.plantora.billing.ui.components.EmptyState
import com.plantora.billing.ui.components.ErrorState
import com.plantora.billing.ui.components.LoadingState
import com.plantora.billing.ui.components.MoneyText
import com.plantora.billing.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(
    canManage: Boolean = true,
    viewModel: ProductsViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snackbar = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<Product?>(null) }

    LaunchedEffect(ui.message) {
        ui.message?.let { snackbar.showSnackbar(it); viewModel.dismissMessage() }
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (canManage) {
                Surface(tonalElevation = 2.dp, shadowElevation = 8.dp) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Dimens.screenPadding, vertical = Dimens.md),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Dimens.md),
                    ) {
                        com.plantora.billing.ui.components.SecondaryButton(
                            text = "Bulk import",
                            onClick = viewModel::openBulk,
                            leadingIcon = Icons.Rounded.UploadFile,
                            modifier = Modifier
                                .weight(1f)
                                .height(Dimens.primaryButtonHeight),
                        )
                        com.plantora.billing.ui.components.PrimaryButton(
                            text = "Add product",
                            onClick = viewModel::openCreate,
                            leadingIcon = Icons.Rounded.Add,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = ui.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text("Search products") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(Dimens.screenPadding),
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = Dimens.screenPadding),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Dimens.sm),
            ) {
                item {
                    FilterChip(
                        selected = ui.categoryFilter == null,
                        onClick = { viewModel.setCategoryFilter(null) },
                        label = { Text("All") },
                    )
                }
                items(ui.categories) { cat ->
                    FilterChip(
                        selected = ui.categoryFilter == cat,
                        onClick = { viewModel.setCategoryFilter(cat) },
                        label = { Text(cat) },
                    )
                }
                item {
                    FilterChip(
                        selected = ui.showInactive,
                        onClick = { viewModel.toggleShowInactive() },
                        label = { Text("Include inactive") },
                    )
                }
            }

            when {
                ui.loading -> LoadingState()
                ui.error != null -> ErrorState(ui.error!!, onRetry = viewModel::load, icon = Icons.Rounded.LocalFlorist)
                ui.visibleProducts.isEmpty() -> EmptyState(
                    icon = Icons.Rounded.LocalFlorist,
                    title = "No products yet",
                    message = "Tap “Add product” to create your first one.",
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(Dimens.screenPadding),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Dimens.md),
                ) {
                    items(ui.visibleProducts, key = { it.id }) { product ->
                        ProductRow(
                            product = product,
                            canManage = canManage,
                            onClick = { if (canManage) viewModel.openEdit(product) },
                            onDelete = { pendingDelete = product },
                        )
                    }
                }
            }
        }
    }

    ui.form?.let { form ->
        ModalBottomSheet(onDismissRequest = viewModel::closeForm, sheetState = sheetState) {
            ProductFormSheet(
                form = form,
                onUpdate = viewModel::updateForm,
                onSave = viewModel::save,
                onPickImage = { bytes, name, mime ->
                    form.id?.let { viewModel.uploadImage(it, bytes, name, mime) }
                },
            )
        }
    }

    pendingDelete?.let { product ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete product?") },
            text = { Text("Remove “${product.name}” from your catalog? Past bills are unaffected.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(product); pendingDelete = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }

    if (ui.bulkSheet) {
        ModalBottomSheet(onDismissRequest = viewModel::closeBulk, sheetState = sheetState) {
            BulkImportSheet(
                busy = ui.bulkBusy,
                onDownloadSample = viewModel::downloadSample,
                onSpreadsheet = viewModel::uploadSpreadsheet,
                onPhotos = viewModel::uploadPhotos,
            )
        }
    }
}

@Composable
private fun BulkImportSheet(
    busy: Boolean,
    onDownloadSample: () -> Unit,
    onSpreadsheet: (ByteArray, String, String) -> Unit,
    onPhotos: (ByteArray, String, String) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    fun read(uri: android.net.Uri?, onRead: (ByteArray, String, String) -> Unit) {
        if (uri == null) return
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val name = resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) { val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME); if (i >= 0) c.getString(i) else null } else null
        } ?: "upload"
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return
        onRead(bytes, name, mime)
    }

    val sheetPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri -> read(uri) { b, n, m -> onSpreadsheet(b, n, m) } }
    val zipPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri -> read(uri) { b, n, m -> onPhotos(b, n, m) } }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = Dimens.lg).padding(bottom = Dimens.xl),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Dimens.md),
    ) {
        Text("Bulk import", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Add many products at once from a spreadsheet, then attach photos by file name.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        com.plantora.billing.ui.components.SecondaryButton("Download Excel template", onClick = onDownloadSample, leadingIcon = Icons.Rounded.Download, modifier = Modifier.fillMaxWidth())
        com.plantora.billing.ui.components.PrimaryButton(
            text = if (busy) "Working…" else "Upload spreadsheet (.xlsx/.csv)",
            onClick = { sheetPicker.launch("*/*") },
            loading = busy,
            leadingIcon = Icons.Rounded.UploadFile,
        )
        com.plantora.billing.ui.components.SecondaryButton("Upload photos (.zip)", onClick = { zipPicker.launch("application/zip") }, leadingIcon = Icons.Rounded.Image, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ProductRow(product: Product, canManage: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (canManage) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(Modifier.padding(Dimens.md), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(56.dp),
            ) {
                if (product.photoUrl != null) {
                    AsyncImage(
                        model = product.photoUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(56.dp),
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.LocalFlorist, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = Dimens.md)) {
                Text(
                    product.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append(product.category ?: "Uncategorised")
                        if (!product.isActive) append(" • Inactive")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MoneyText(product.retailPrice, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            if (canManage) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete ${product.name}")
                }
            }
        }
    }
}
