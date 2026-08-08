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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.plantora.billing.R
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
    // The review dismisses normally (scrim tap / back / swipe-down) via
    // onDismissRequest → dismissReview, which only HIDES it — the cart is kept, so
    // the Cart bar reopens a fresh sheet. The old design rejected the Hidden state
    // here AND used a no-op onDismissRequest; that desynced the sheet's internal
    // anchor state from showReview and hard-froze the app (ANR) when the review was
    // dismissed via back/swipe-down and then reopened. Let it settle to Hidden.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Once the cart list has scrolled as far down as it can, swallow any remaining
    // DOWNWARD drag/fling so the sheet body can't collapse the review — you scroll
    // the cart freely and only the drag handle at the top (or ✕ / Add item) closes
    // it. This lives INSIDE the sheet's own nested-scroll connection, so its
    // onPostScroll runs first and consumes the delta before the dismiss path sees
    // it. It never touches sheetState / onDismissRequest, so the earlier freeze
    // (from rejecting the Hidden state) cannot return. Upward drags and normal
    // scrolling are untouched — verticalScroll consumes what it needs first.
    val blockSheetDismissDrag = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                if (available.y > 0f && source == NestedScrollSource.UserInput) available.copy(x = 0f) else Offset.Zero

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                if (available.y > 0f) available.copy(x = 0f) else Velocity.Zero
        }
    }
    val quickAddSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snackbar = remember { SnackbarHostState() }
    val ctx = androidx.compose.ui.platform.LocalContext.current

    // The review's open/closed state lives in the ViewModel (state.showReview): it
    // opens on every add and closes only via ✕ / Add item / Clear cart / save, and
    // can't desync from the cart across tab switches or process death.
    LaunchedEffect(state.toast) { state.toast?.let { snackbar.showSnackbar(it.resolve(ctx)); viewModel.dismissToast() } }

    // Success replaces the whole screen, mirroring the web SuccessView.
    state.success?.let { bill ->
        SuccessView(
            bill = bill,
            printPhase = state.printPhase,
            printMessage = state.printMessage?.resolve(ctx),
            onPrint = viewModel::printSuccessBill,
            onNewBill = viewModel::startNewBill,
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        // Quick-add is a small round button in the corner so the product grid keeps
        // its full height (the old full-width bar ate a lot of visible catalogue).
        floatingActionButton = {
            FloatingActionButton(
                onClick = viewModel::openQuickAdd,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.bill_quick_add))
            }
        },
        bottomBar = {
            // imePadding lifts the action bar (Cart, Held bills) above the keyboard
            // when the product search field is focused. The app is edge-to-edge, so
            // the window doesn't resize for the IME — the inset must be consumed here,
            // mirroring every other screen. Shown only when there's something to show.
            if (!state.isCartEmpty || heldBills.isNotEmpty()) {
                Surface(tonalElevation = 2.dp, shadowElevation = 8.dp, modifier = Modifier.imePadding()) {
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
                                if (heldBills.isNotEmpty()) Spacer(Modifier.height(Dimens.sm))
                            }
                        }
                        if (heldBills.isNotEmpty()) {
                            com.plantora.billing.ui.components.SecondaryButton(
                                text = stringResource(R.string.bill_held_count, heldBills.size),
                                onClick = { showHeld = true },
                                leadingIcon = Icons.Rounded.Pause,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text(stringResource(R.string.bill_search)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    val voiceUnavailable = stringResource(R.string.bill_voice_unavailable)
                    VoiceSearchButton(
                        onResults = viewModel::onVoiceTranscript,
                        onUnavailable = { viewModel.showToast(voiceUnavailable) },
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
                        label = { Text(stringResource(R.string.filter_all)) },
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
                    message = state.productsError!!.resolve(ctx),
                    onRetry = viewModel::loadProducts,
                    icon = Icons.Rounded.LocalFlorist,
                )
                state.filteredProducts.isEmpty() -> EmptyState(
                    icon = Icons.Rounded.LocalFlorist,
                    title = stringResource(R.string.bill_no_products_title),
                    message = if (state.query.isBlank())
                        stringResource(R.string.bill_no_products_hint)
                    else stringResource(R.string.bill_no_products_search, state.query),
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
            // Dismiss (scrim / back / swipe-down) just hides the review; the cart is
            // preserved, so the Cart bar reopens it. Syncing showReview is what keeps
            // the sheet from desyncing with sheetState and freezing on reopen.
            onDismissRequest = viewModel::dismissReview,
            sheetState = sheetState,
        ) {
            CartSheetContent(
                modifier = Modifier.nestedScroll(blockSheetDismissDrag),
                state = state,
                onSetQuantity = viewModel::setQuantity,
                onSetQuantityText = viewModel::setQuantityText,
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
        Text(stringResource(R.string.held_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(Dimens.xs))
        Text(
            stringResource(R.string.held_desc),
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
                            pluralStringResource(R.plurals.item_count, bill.itemCount, bill.itemCount) + " • " +
                                com.plantora.billing.domain.Money.parse(bill.total).format() +
                                " • ${timeFmt.format(java.util.Date(bill.savedAt))}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    androidx.compose.material3.TextButton(onClick = { onDiscard(bill.id) }) {
                        Text(stringResource(R.string.action_discard), color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.height(Dimens.sm))
                com.plantora.billing.ui.components.PrimaryButton(
                    text = stringResource(R.string.held_resume),
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
                stringResource(R.string.bill_review_pay),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.weight(1f).padding(start = Dimens.sm),
            )
            Text(totalLabel, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}
