package com.plantora.billing.ui.billing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plantora.billing.ui.billing.voice.VoiceSearchButton
import com.plantora.billing.ui.components.EmptyState
import com.plantora.billing.ui.components.ErrorState
import com.plantora.billing.ui.components.LoadingState
import com.plantora.billing.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillScreen(viewModel: BillingViewModel = hiltViewModel()) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val heldBills by viewModel.heldBills.collectAsStateWithLifecycle()
    var showHeld by remember { mutableStateOf(false) }
    val heldSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Swipe-to-dismiss is disabled: the review closes only via the ✕ or "Add item"
    // (confirmValueChange rejects the Hidden state the drag gesture targets).
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != androidx.compose.material3.SheetValue.Hidden },
    )
    val quickAddSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snackbar = remember { SnackbarHostState() }

    // The review's open/closed state lives in the ViewModel (state.showReview): it
    // opens on every add and closes only via ✕ / Add item / Clear cart / save, and
    // can't desync from the cart across tab switches or process death.
    LaunchedEffect(state.toast) { state.toast?.let { snackbar.showSnackbar(it); viewModel.dismissToast() } }

    // Success replaces the whole screen, mirroring the web SuccessView.
    state.success?.let { bill ->
        SuccessView(
            bill = bill,
            printPhase = state.printPhase,
            printMessage = state.printMessage,
            onPrint = viewModel::printSuccessBill,
            onNewBill = viewModel::startNewBill,
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Surface(tonalElevation = 2.dp, shadowElevation = 8.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = Dimens.screenPadding, vertical = Dimens.md)) {
                    AnimatedVisibility(
                        visible = !state.isCartEmpty,
                        enter = slideInVertically { it },
                        exit = slideOutVertically { it },
                    ) {
                        Column {
                            CartBar(
                                itemCount = state.itemCount,
                                totalLabel = state.totals.total.format(),
                                onClick = viewModel::openReview,
                            )
                            Spacer(Modifier.height(Dimens.sm))
                        }
                    }
                    if (heldBills.isNotEmpty()) {
                        com.plantora.billing.ui.components.SecondaryButton(
                            text = "Held bills (${heldBills.size})",
                            onClick = { showHeld = true },
                            leadingIcon = Icons.Rounded.Pause,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(Dimens.sm))
                    }
                    com.plantora.billing.ui.components.SecondaryButton(
                        text = "Quick add item",
                        onClick = viewModel::openQuickAdd,
                        leadingIcon = Icons.Rounded.Add,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text("Search products") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    VoiceSearchButton(
                        onResults = viewModel::onVoiceTranscript,
                        onUnavailable = { viewModel.showToast("Voice search isn't available on this device.") },
                    )
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.screenPadding)
                    .padding(top = Dimens.sm),
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = Dimens.screenPadding, vertical = Dimens.sm),
                horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
            ) {
                item {
                    FilterChip(
                        selected = state.categoryFilter == null,
                        onClick = { viewModel.setCategoryFilter(null) },
                        label = { Text("All") },
                    )
                }
                items(state.categories) { cat ->
                    FilterChip(
                        selected = state.categoryFilter == cat,
                        onClick = { viewModel.setCategoryFilter(cat) },
                        label = { Text(cat) },
                    )
                }
            }

            when {
                state.productsLoading -> LoadingState()
                state.productsError != null -> ErrorState(
                    message = state.productsError!!,
                    onRetry = viewModel::loadProducts,
                    icon = Icons.Rounded.LocalFlorist,
                )
                state.filteredProducts.isEmpty() -> EmptyState(
                    icon = Icons.Rounded.LocalFlorist,
                    title = "No products",
                    message = if (state.query.isBlank())
                        "Add products in the Products tab, or use Quick add."
                    else "No products match \"${state.query}\".",
                )
                else -> ProductGrid(
                    products = state.filteredProducts,
                    onAdd = viewModel::addProduct,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (state.showReview) {
        ModalBottomSheet(
            // No-op: tapping the scrim or the back gesture must NOT close the review.
            // It closes only through the ✕, "Add item", "Clear cart", or a saved bill.
            onDismissRequest = { },
            sheetState = sheetState,
        ) {
            CartSheetContent(
                state = state,
                onSetQuantity = viewModel::setQuantity,
                onSetUnitPrice = viewModel::setUnitPrice,
                onRemoveLine = viewModel::removeLine,
                onSetDiscountType = viewModel::setDiscountType,
                onSetDiscountInput = viewModel::setDiscountInput,
                onSetPaymentMode = viewModel::setPaymentMode,
                onSetCashInput = viewModel::setCashInput,
                onSetDueInput = viewModel::setDueInput,
                onSetCustomerName = viewModel::setCustomerName,
                onSetCustomerPhone = viewModel::setCustomerPhone,
                onSetRemarks = viewModel::setRemarks,
                onClose = viewModel::dismissReview,
                onClearCart = viewModel::clearCart,
                onAddItem = viewModel::dismissReview,
                onHold = viewModel::holdBill,
                onCheckout = viewModel::checkout,
            )
        }
    }

    if (showHeld) {
        ModalBottomSheet(onDismissRequest = { showHeld = false }, sheetState = heldSheetState) {
            HeldBillsSheet(
                held = heldBills,
                onResume = { bill ->
                    showHeld = false
                    viewModel.resumeBill(bill)
                },
                onDiscard = viewModel::discardHeld,
            )
        }
    }

    state.quickAdd?.let { qa ->
        ModalBottomSheet(onDismissRequest = viewModel::closeQuickAdd, sheetState = quickAddSheetState) {
            QuickAddSheet(
                state = qa,
                onName = viewModel::setQuickAddName,
                onPrice = viewModel::setQuickAddPrice,
                onQuantity = viewModel::setQuickAddQuantity,
                onSave = viewModel::saveQuickAdd,
            )
        }
    }
}

@Composable
private fun HeldBillsSheet(
    held: List<com.plantora.billing.data.local.HeldBill>,
    onResume: (com.plantora.billing.data.local.HeldBill) -> Unit,
    onDiscard: (String) -> Unit,
) {
    val timeFmt = remember { java.text.SimpleDateFormat("d MMM, h:mm a", java.util.Locale.getDefault()) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.lg)
            .padding(bottom = Dimens.xl),
    ) {
        Text("Held bills", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(Dimens.xs))
        Text(
            "Bills you parked to serve another customer. Tap Resume to continue one.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Dimens.md))
        held.forEach { bill ->
            com.plantora.billing.ui.components.PlantoraCard(modifier = Modifier.padding(vertical = Dimens.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            bill.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        )
                        Text(
                            "${bill.itemCount} item${if (bill.itemCount == 1) "" else "s"} • " +
                                com.plantora.billing.domain.Money.parse(bill.total).format() +
                                " • ${timeFmt.format(java.util.Date(bill.savedAt))}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    androidx.compose.material3.TextButton(onClick = { onDiscard(bill.id) }) {
                        Text("Discard", color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.height(Dimens.sm))
                com.plantora.billing.ui.components.PrimaryButton(
                    text = "Resume this bill",
                    onClick = { onResume(bill) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun CartBar(itemCount: Int, totalLabel: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimens.lg),
        shape = MaterialTheme.shapes.large,
        onClick = onClick,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.lg, vertical = Dimens.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BadgedBox(badge = { Badge { Text("$itemCount") } }) {
                Icon(Icons.Rounded.ShoppingCart, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
            }
            Text(
                "  Review & pay",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.weight(1f).padding(start = Dimens.sm),
            )
            Text(totalLabel, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}
