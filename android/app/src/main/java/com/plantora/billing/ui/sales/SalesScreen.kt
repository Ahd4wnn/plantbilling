package com.plantora.billing.ui.sales

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plantora.billing.R
import com.plantora.billing.domain.BillListEntry
import com.plantora.billing.domain.formatBillTime
import com.plantora.billing.domain.toDisplay
import com.plantora.billing.ui.components.LoadingState
import com.plantora.billing.ui.components.MoneyText
import com.plantora.billing.ui.components.PlantoraCard
import com.plantora.billing.ui.components.SecondaryButton
import com.plantora.billing.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesScreen(
    onOpenBill: (String) -> Unit,
    onOpenReport: () -> Unit = {},
    onOpenDues: () -> Unit = {},
    canApprove: Boolean = false,
    onOpenApprovals: () -> Unit = {},
    viewModel: SalesViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    // Refresh every time the Sales tab is shown so a bill just made on the Bill tab
    // (or a deleted/edited bill) appears immediately, despite saved nav state.
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose { }
    }

    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(ui.message) {
        ui.message?.let { snackbar.showSnackbar(it.resolve(ctx)); viewModel.dismissMessage() }
    }

    // Recording/editing an expense takes over the whole screen — a dedicated page, not
    // a bottom sheet. The old sheet hosted a dropdown popup that broke the picker and
    // hid the day summary; a full page with plain chips avoids that entirely. Closing
    // it re-enters this composable, whose resume effect reloads the summary.
    ui.expenseEditor?.let { editor ->
        ExpenseEditorScreen(
            editor = editor,
            categories = ui.expenseCategories,
            canManage = ui.isOwner,
            onAmount = viewModel::setExpenseAmount,
            onCategory = viewModel::setExpenseCategory,
            onAddCategory = viewModel::addExpenseCategory,
            onNote = viewModel::setExpenseNote,
            onMethod = viewModel::setExpenseMethod,
            onSave = viewModel::saveExpense,
            onBack = viewModel::closeExpenseEditor,
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.md),
        ) {
            item {
                Text(stringResource(R.string.nav_sales), style = MaterialTheme.typography.headlineLarge)
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                    SecondaryButton(
                        text = stringResource(R.string.sales_dues),
                        onClick = onOpenDues,
                        leadingIcon = Icons.Rounded.AccountBalanceWallet,
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton(
                        text = stringResource(R.string.sales_reports),
                        onClick = onOpenReport,
                        leadingIcon = Icons.Rounded.BarChart,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Managers approve salesperson due-collections before they close.
            if (canApprove) {
                item {
                    SecondaryButton(
                        text = stringResource(R.string.sales_approve),
                        onClick = onOpenApprovals,
                        leadingIcon = Icons.Rounded.TaskAlt,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (ui.isOwner) {
                item { StaffFilter(ui) { id -> viewModel.selectStaff(id) } }
                if (ui.staffSales.isNotEmpty()) {
                    item { StaffLeaderboard(ui.staffSales) }
                }
            }

            item {
                DateSelector(
                    date = ui.date,
                    canGoNext = !ui.isToday,
                    onPrev = viewModel::goToPreviousDay,
                    onNext = viewModel::goToNextDay,
                    onPick = viewModel::changeDate,
                )
            }

            item {
                when {
                    ui.summaryLoading -> LoadingState(Modifier.fillMaxWidth().padding(Dimens.xl))
                    ui.summary != null -> SummaryHero(
                        summary = ui.summary!!,
                        isOwner = ui.isOwner,
                        cumulativeCashInHand = ui.cashInHandCumulative,
                        onAddExpense = viewModel::openCreateExpense,
                        onEditExpense = { e -> viewModel.openEditExpense(e.id, e.amount, e.categoryId, e.note, e.paymentMethod) },
                        onDeleteExpense = viewModel::deleteExpense,
                    )
                    ui.error != null -> Text(ui.error!!, color = MaterialTheme.colorScheme.error)
                }
            }

            item { Text(stringResource(R.string.sales_bills), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Dimens.sm)) }

            if (ui.bills.isEmpty() && !ui.billsLoading) {
                item { Text(stringResource(R.string.sales_no_bills), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            items(ui.bills, key = { it.id }) { bill ->
                BillRow(bill, onClick = { onOpenBill(bill.id) })
            }

            if (ui.hasMore) {
                item {
                    SecondaryButton(
                        text = if (ui.loadingMore) stringResource(R.string.action_loading) else stringResource(R.string.sales_load_more),
                        onClick = viewModel::loadMore,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun StaffFilter(ui: SalesUiState, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    PlantoraCard(modifier = Modifier.clickable { expanded = true }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(horizontal = Dimens.md)) {
                Text(stringResource(R.string.sales_view_by_staff), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(ui.selectedStaffEmail ?: stringResource(R.string.vm_all_staff), style = MaterialTheme.typography.titleMedium)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.sales_all_staff)) }, onClick = { onSelect(null); expanded = false })
            ui.staff.forEach { sp ->
                DropdownMenuItem(text = { Text(sp.email) }, onClick = { onSelect(sp.id); expanded = false })
            }
        }
    }
}

@Composable
private fun StaffLeaderboard(rows: List<StaffSales>) {
    PlantoraCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                stringResource(R.string.sales_top_sellers),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = Dimens.sm),
            )
        }
        rows.forEachIndexed { i, row ->
            Row(
                Modifier.fillMaxWidth().padding(top = Dimens.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${i + 1}.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
                Text(
                    row.salesperson.email,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f).padding(horizontal = Dimens.sm),
                )
                MoneyText(row.sales, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun DateSelector(
    date: java.time.LocalDate,
    canGoNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPick: (java.time.LocalDate) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = stringResource(R.string.date_prev)) }
        // Tap the pill to jump to any past date from a calendar.
        com.plantora.billing.ui.components.DatePickerField(
            date = date,
            onDate = onPick,
            maxDate = com.plantora.billing.domain.todayInShopZone(),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext, enabled = canGoNext) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = stringResource(R.string.date_next)) }
    }
}

@Composable
private fun BillRow(bill: BillListEntry, onClick: () -> Unit) {
    PlantoraCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
            Column(Modifier.weight(1f).padding(horizontal = Dimens.md)) {
                Text(bill.customerName ?: stringResource(R.string.bill_walkin), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${formatBillTime(bill.createdAt)} • ${pluralStringResource(R.plurals.item_count, bill.itemCount, bill.itemCount)} • ${com.plantora.billing.ui.components.paymentLabel(bill.paymentMethod)}" +
                        if (bill.isEdited) " • " + stringResource(R.string.status_edited) else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MoneyText(bill.total, style = MaterialTheme.typography.titleMedium)
        }
    }
}
