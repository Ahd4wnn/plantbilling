package com.plantora.billing.ui.admin

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.plantora.billing.data.AdminRepository
import com.plantora.billing.data.remote.friendlyError
import com.plantora.billing.domain.AdminExpense
import com.plantora.billing.domain.AdminSale
import com.plantora.billing.domain.LedgerSummary
import com.plantora.billing.domain.Money
import com.plantora.billing.domain.toApiDate
import com.plantora.billing.domain.todayInShopZone
import com.plantora.billing.ui.components.LoadingState
import com.plantora.billing.ui.components.MoneyText
import com.plantora.billing.ui.components.NumberField
import com.plantora.billing.ui.components.PlantoraCard
import com.plantora.billing.ui.components.PlantoraTextField
import com.plantora.billing.ui.components.PrimaryButton
import com.plantora.billing.ui.components.SectionHeader
import com.plantora.billing.ui.theme.Dimens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminBooksState(
    val loading: Boolean = true,
    val error: String? = null,
    val period: AdminPeriod = AdminPeriod.MONTH,
    val summary: LedgerSummary? = null,
    val sales: List<AdminSale> = emptyList(),
    val expenses: List<AdminExpense> = emptyList(),
    val saving: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class AdminBooksViewModel @Inject constructor(
    private val repo: AdminRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(AdminBooksState())
    val ui: StateFlow<AdminBooksState> = _ui.asStateFlow()

    init { load() }

    fun dismissMessage() = _ui.update { it.copy(message = null) }

    fun setPeriod(p: AdminPeriod) {
        if (p == _ui.value.period) return
        _ui.update { it.copy(period = p) }
        load()
    }

    private fun range(): Pair<String, String> {
        val today = todayInShopZone()
        val from = when (_ui.value.period) {
            AdminPeriod.TODAY -> today
            AdminPeriod.WEEK -> today.minusDays(6)
            AdminPeriod.MONTH -> today.minusDays(29)
        }
        return from.toApiDate() to today.toApiDate()
    }

    fun load() {
        val (from, to) = range()
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                Triple(repo.ledgerSummary(from, to), repo.sales(from, to), repo.expenses(from, to))
            }.onSuccess { (summary, sales, expenses) ->
                _ui.update { it.copy(loading = false, summary = summary, sales = sales, expenses = expenses) }
            }.onFailure { e -> _ui.update { it.copy(loading = false, error = friendlyError(e)) } }
        }
    }

    fun createSale(title: String, amount: Money, method: String, note: String?, onDone: () -> Unit) {
        _ui.update { it.copy(saving = true) }
        viewModelScope.launch {
            runCatching { repo.createSale(title, amount, method, note) }
                .onSuccess {
                    _ui.update { it.copy(saving = false, message = "Sale saved.") }
                    onDone()
                    load()
                }
                .onFailure { e -> _ui.update { it.copy(saving = false, message = friendlyError(e)) } }
        }
    }

    fun createExpense(reason: String, amount: Money, method: String, note: String?, onDone: () -> Unit) {
        _ui.update { it.copy(saving = true) }
        viewModelScope.launch {
            runCatching { repo.createExpense(reason, amount, method, note) }
                .onSuccess {
                    _ui.update { it.copy(saving = false, message = "Expense saved.") }
                    onDone()
                    load()
                }
                .onFailure { e -> _ui.update { it.copy(saving = false, message = friendlyError(e)) } }
        }
    }

    fun updateSale(id: String, title: String, amount: Money, method: String, note: String?, onDone: () -> Unit) {
        _ui.update { it.copy(saving = true) }
        viewModelScope.launch {
            runCatching { repo.updateSale(id, title, amount, method, note) }
                .onSuccess {
                    _ui.update { it.copy(saving = false, message = "Sale updated.") }
                    onDone()
                    load()
                }
                .onFailure { e -> _ui.update { it.copy(saving = false, message = friendlyError(e)) } }
        }
    }

    fun updateExpense(id: String, reason: String, amount: Money, method: String, note: String?, onDone: () -> Unit) {
        _ui.update { it.copy(saving = true) }
        viewModelScope.launch {
            runCatching { repo.updateExpense(id, reason, amount, method, note) }
                .onSuccess {
                    _ui.update { it.copy(saving = false, message = "Expense updated.") }
                    onDone()
                    load()
                }
                .onFailure { e -> _ui.update { it.copy(saving = false, message = friendlyError(e)) } }
        }
    }

    fun deleteSale(id: String) {
        viewModelScope.launch {
            runCatching { repo.deleteSale(id) }
                .onSuccess { _ui.update { it.copy(sales = it.sales.filterNot { s -> s.id == id }, message = "Sale removed.") }; load() }
                .onFailure { e -> _ui.update { it.copy(message = friendlyError(e)) } }
        }
    }

    fun deleteExpense(id: String) {
        viewModelScope.launch {
            runCatching { repo.deleteExpense(id) }
                .onSuccess { _ui.update { it.copy(expenses = it.expenses.filterNot { x -> x.id == id }, message = "Expense removed.") }; load() }
                .onFailure { e -> _ui.update { it.copy(message = friendlyError(e)) } }
        }
    }
}

private enum class EntryType { SALE, EXPENSE }
private data class PendingDelete(val id: String, val type: EntryType, val label: String)

/** Drives the add/edit bottom sheet. [editId] null = adding; non-null = editing that row. */
private data class BookSheet(
    val type: EntryType,
    val editId: String? = null,
    val title: String = "",
    val amount: String = "",
    val method: String = "cash",
    val note: String = "",
) {
    val isEdit: Boolean get() = editId != null
}

/** The single bucket a sale's amount sits in (we only ever write single-bucket). */
private fun saleMethod(s: AdminSale): String = when {
    s.dueAmount.isPositive() -> "due"
    s.upiAmount.isPositive() && !s.cashAmount.isPositive() -> "upi"
    else -> "cash"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminBooksScreen(
    onLogout: () -> Unit,
    viewModel: AdminBooksViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var sheet by remember { mutableStateOf<BookSheet?>(null) }
    var pendingDelete by remember { mutableStateOf<PendingDelete?>(null) }
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); viewModel.dismissMessage() } }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Text("My books") }) },
    ) { padding ->
        when {
            ui.loading && ui.summary == null -> LoadingState(Modifier.padding(padding))
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(Dimens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.md),
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                        AdminPeriod.entries.forEach { p ->
                            FilterChip(
                                selected = ui.period == p,
                                onClick = { viewModel.setPeriod(p) },
                                label = { Text(p.label, maxLines = 1) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                ui.summary?.let { s ->
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                            MoneyKpi("Sales", s.totalSales, Modifier.weight(1f))
                            MoneyKpi("Expenses", s.totalExpenses, Modifier.weight(1f))
                            MoneyKpi("Net", s.netCollected, Modifier.weight(1f))
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                            MoneyKpi("Cash", s.cashCollected, Modifier.weight(1f))
                            MoneyKpi("UPI", s.upiCollected, Modifier.weight(1f))
                            MoneyKpi("Due", s.outstandingDue, Modifier.weight(1f))
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                        PrimaryButton("Add sale", onClick = { sheet = BookSheet(EntryType.SALE) }, leadingIcon = Icons.Rounded.Add, modifier = Modifier.weight(1f))
                        PrimaryButton("Add expense", onClick = { sheet = BookSheet(EntryType.EXPENSE) }, leadingIcon = Icons.Rounded.Add, modifier = Modifier.weight(1f))
                    }
                }

                item { SectionHeader("Sales") }
                if (ui.sales.isEmpty()) {
                    item { Text("No sales in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    items(ui.sales, key = { it.id }) { sale ->
                        SaleRow(
                            sale,
                            onEdit = {
                                sheet = BookSheet(
                                    type = EntryType.SALE, editId = sale.id, title = sale.title,
                                    amount = sale.amount.toInput(), method = saleMethod(sale), note = sale.note ?: "",
                                )
                            },
                            onDelete = { pendingDelete = PendingDelete(sale.id, EntryType.SALE, sale.title) },
                        )
                    }
                }

                item { SectionHeader("Expenses") }
                if (ui.expenses.isEmpty()) {
                    item { Text("No expenses in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    items(ui.expenses, key = { it.id }) { exp ->
                        ExpenseRow(
                            exp,
                            onEdit = {
                                sheet = BookSheet(
                                    type = EntryType.EXPENSE, editId = exp.id, title = exp.reason,
                                    amount = exp.amount.toInput(), method = exp.paymentMethod, note = exp.note ?: "",
                                )
                            },
                            onDelete = { pendingDelete = PendingDelete(exp.id, EntryType.EXPENSE, exp.reason) },
                        )
                    }
                }
            }
        }
    }

    sheet?.let { s ->
        ModalBottomSheet(
            onDismissRequest = { sheet = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            EntryForm(
                sheet = s,
                saving = ui.saving,
                onSubmit = { title, amount, method, note ->
                    when {
                        s.type == EntryType.SALE && s.editId != null ->
                            viewModel.updateSale(s.editId, title, amount, method, note) { sheet = null }
                        s.type == EntryType.SALE ->
                            viewModel.createSale(title, amount, method, note) { sheet = null }
                        s.editId != null ->
                            viewModel.updateExpense(s.editId, title, amount, method, note) { sheet = null }
                        else ->
                            viewModel.createExpense(title, amount, method, note) { sheet = null }
                    }
                },
            )
        }
    }

    pendingDelete?.let { pd ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(if (pd.type == EntryType.SALE) "Remove sale?" else "Remove expense?") },
            text = { Text("\"${pd.label}\" will be permanently removed from your books.") },
            confirmButton = {
                TextButton(onClick = {
                    if (pd.type == EntryType.SALE) viewModel.deleteSale(pd.id) else viewModel.deleteExpense(pd.id)
                    pendingDelete = null
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MoneyKpi(label: String, money: Money, modifier: Modifier = Modifier) {
    PlantoraCard(modifier = modifier) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        MoneyText(money, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun SaleRow(sale: AdminSale, onEdit: () -> Unit, onDelete: () -> Unit) {
    PlantoraCard(modifier = Modifier.clickable(onClick = onEdit)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(sale.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val due = if (sale.dueAmount.isPositive()) " • ${sale.dueAmount.format()} due" else ""
                Text(
                    formatDayDate(sale.occurredOn) + due,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (sale.dueAmount.isPositive()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MoneyText(sale.amount, style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ExpenseRow(exp: AdminExpense, onEdit: () -> Unit, onDelete: () -> Unit) {
    PlantoraCard(modifier = Modifier.clickable(onClick = onEdit)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(exp.reason, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    formatDayDate(exp.occurredOn) + " • " + exp.paymentMethod.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MoneyText(exp.amount, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun EntryForm(
    sheet: BookSheet,
    saving: Boolean,
    onSubmit: (title: String, amount: Money, method: String, note: String?) -> Unit,
) {
    val isSale = sheet.type == EntryType.SALE
    // Re-seed the form whenever a different row (or add) opens the sheet.
    var title by remember(sheet.editId, sheet.type) { mutableStateOf(sheet.title) }
    var amountText by remember(sheet.editId, sheet.type) { mutableStateOf(sheet.amount) }
    var note by remember(sheet.editId, sheet.type) { mutableStateOf(sheet.note) }
    var method by remember(sheet.editId, sheet.type) { mutableStateOf(sheet.method) }
    // Sales can be cash/upi/due; expenses only cash/upi.
    val methods = if (isSale) listOf("cash", "upi", "due") else listOf("cash", "upi")

    val amount = Money.parse(amountText)
    val canSave = title.isNotBlank() && amount.isPositive() && !saving

    val heading = when {
        sheet.isEdit && isSale -> "Edit sale"
        sheet.isEdit -> "Edit expense"
        isSale -> "Add sale"
        else -> "Add expense"
    }

    Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = Dimens.lg).padding(bottom = Dimens.xl)) {
        Text(heading, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(Dimens.md))
        PlantoraTextField(title, { title = it }, label = if (isSale) "What was sold" else "What for")
        Spacer(Modifier.height(Dimens.sm))
        NumberField(amountText, { amountText = it }, label = "Amount (₹)")
        Spacer(Modifier.height(Dimens.md))
        Text("Paid by", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Dimens.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
            methods.forEach { m ->
                FilterChip(
                    selected = method == m,
                    onClick = { method = m },
                    label = { Text(if (m == "due") "Due" else m.uppercase()) },
                )
            }
        }
        Spacer(Modifier.height(Dimens.sm))
        PlantoraTextField(note, { note = it }, label = "Note (optional)", singleLine = false)
        Spacer(Modifier.height(Dimens.lg))
        PrimaryButton(
            text = if (sheet.isEdit) "Save changes" else if (isSale) "Save sale" else "Save expense",
            onClick = { onSubmit(title, amount, method, note.ifBlank { null }) },
            enabled = canSave,
            loading = saving,
        )
    }
}
