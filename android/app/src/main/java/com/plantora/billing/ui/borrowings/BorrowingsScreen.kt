package com.plantora.billing.ui.borrowings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plantora.billing.domain.Borrowing
import com.plantora.billing.domain.Money
import com.plantora.billing.domain.formatBillTime
import com.plantora.billing.ui.components.ErrorState
import com.plantora.billing.ui.components.LoadingState
import com.plantora.billing.ui.components.MoneyText
import com.plantora.billing.ui.components.PlantoraCard
import com.plantora.billing.ui.components.PlantoraTextField
import com.plantora.billing.ui.components.PrimaryButton
import com.plantora.billing.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BorrowingsScreen(
    onBack: () -> Unit,
    viewModel: BorrowingsViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirmDelete by remember { mutableStateOf<Borrowing?>(null) }

    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); viewModel.dismissMessage() } }

    androidx.compose.material3.Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Money borrowed") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        when {
            ui.loading -> LoadingState(Modifier.padding(padding))
            ui.error != null -> ErrorState(ui.error!!, onRetry = viewModel::load, icon = Icons.Rounded.Payments, modifier = Modifier.padding(padding))
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding).imePadding(),
                contentPadding = PaddingValues(Dimens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.md),
            ) {
                item {
                    PlantoraCard {
                        Text("Still to pay back", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(Dimens.xs))
                        MoneyText(ui.totalOutstanding, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.error)
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                        SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                            val filters = BorrowFilter.entries
                            filters.forEachIndexed { i, f ->
                                SegmentedButton(selected = ui.filter == f, onClick = { viewModel.setFilter(f) }, shape = SegmentedButtonDefaults.itemShape(i, filters.size)) {
                                    Text(when (f) { BorrowFilter.ALL -> "All"; BorrowFilter.OPEN -> "To pay"; BorrowFilter.PAID -> "Paid" })
                                }
                            }
                        }
                    }
                }
                item {
                    PrimaryButton(text = "Add borrowing", onClick = viewModel::openAdd, leadingIcon = Icons.Rounded.Add, modifier = Modifier.fillMaxWidth())
                }
                if (ui.items.isEmpty()) {
                    item {
                        Text(
                            if (ui.filter == BorrowFilter.PAID) "No paid-off borrowings yet." else "Nothing borrowed. Tap Add to record a borrowing.",
                            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(ui.items, key = { it.id }) { b ->
                    BorrowingRow(b, onPay = { viewModel.openPay(b) }, onDelete = { confirmDelete = b })
                }
            }
        }
    }

    ui.addEditor?.let { editor ->
        ModalBottomSheet(onDismissRequest = viewModel::closeAdd, sheetState = sheet) {
            AddSheet(editor, viewModel)
        }
    }
    ui.payEditor?.let { editor ->
        ModalBottomSheet(onDismissRequest = viewModel::closePay, sheetState = sheet) {
            PaySheet(editor, viewModel)
        }
    }

    confirmDelete?.let { b ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this borrowing?") },
            text = { Text("Remove the ${b.amount.format()} borrowed from ${b.lenderName}? This can't be undone.") },
            confirmButton = { TextButton(onClick = { viewModel.delete(b.id); confirmDelete = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BorrowingRow(b: Borrowing, onPay: () -> Unit, onDelete: () -> Unit) {
    PlantoraCard {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                    Text(b.lenderName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (b.isPaid) {
                        Text(
                            "Paid",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                b.lenderPhone?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "${formatBillTime(b.createdAt)} • received by ${b.method}" + if (b.isPaid) " • paid by ${b.paidMethod}" else "",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                b.remarks?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    b.amount.format(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    textDecoration = if (b.isPaid) TextDecoration.LineThrough else null,
                    color = if (b.isPaid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!b.isPaid) {
                        TextButton(onClick = onPay) {
                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.height(18.dp))
                            Text(" Mark paid")
                        }
                    }
                    IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete borrowing") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSheet(editor: AddEditor, vm: BorrowingsViewModel) {
    Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.lg).padding(bottom = Dimens.xl)) {
        Text("Add borrowing", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(Dimens.lg))
        PlantoraTextField(editor.name, vm::setName, label = "Borrowed from (name)")
        Spacer(Modifier.height(Dimens.md))
        PlantoraTextField(editor.phone, vm::setPhone, label = "Phone number (optional)", keyboardType = KeyboardType.Phone)
        Spacer(Modifier.height(Dimens.md))
        PlantoraTextField(editor.amount, vm::setAmount, label = "Amount (₹)", keyboardType = KeyboardType.Decimal)
        Spacer(Modifier.height(Dimens.md))
        MethodPicker(editor.mode, editor.total, editor.splitCash, vm::setAddMode, vm::setAddSplitCash)
        Spacer(Modifier.height(Dimens.md))
        PlantoraTextField(editor.remarks, vm::setRemarks, label = "Remarks (optional)")
        editor.error?.let { Spacer(Modifier.height(Dimens.md)); Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(Dimens.xl))
        PrimaryButton(text = "Save borrowing", onClick = vm::saveAdd, enabled = editor.canSave, loading = editor.saving, modifier = Modifier.fillMaxWidth())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaySheet(editor: PayEditor, vm: BorrowingsViewModel) {
    val b = editor.borrowing
    Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.lg).padding(bottom = Dimens.xl)) {
        Text("Mark paid — ${b.lenderName}", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(Dimens.md))
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
            Row(Modifier.fillMaxWidth().padding(Dimens.md), verticalAlignment = Alignment.CenterVertically) {
                Text("Amount owed", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                MoneyText(b.amount, style = MaterialTheme.typography.titleLarge)
            }
        }
        Spacer(Modifier.height(Dimens.md))
        Text("How did you pay it back?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Dimens.sm))
        MethodPicker(editor.mode, b.amount, editor.splitCash, vm::setPayMode, vm::setPaySplitCash)
        editor.error?.let { Spacer(Modifier.height(Dimens.md)); Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(Dimens.xl))
        PrimaryButton(text = "Mark ${b.amount.format()} paid", onClick = vm::savePay, enabled = editor.canSave, loading = editor.saving, modifier = Modifier.fillMaxWidth())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MethodPicker(mode: BorrowMode, total: Money, splitCash: String, onMode: (BorrowMode) -> Unit, onSplitCash: (String) -> Unit) {
    Text("Method", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(Dimens.xs))
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        val modes = BorrowMode.entries
        modes.forEachIndexed { i, m ->
            SegmentedButton(selected = mode == m, onClick = { onMode(m) }, shape = SegmentedButtonDefaults.itemShape(i, modes.size)) {
                Text(when (m) { BorrowMode.CASH -> "Cash"; BorrowMode.UPI -> "UPI"; BorrowMode.SPLIT -> "Split" })
            }
        }
    }
    if (mode == BorrowMode.SPLIT) {
        val cash = Money.parse(splitCash).let { if (it > total) total else it }
        val upi = total - cash
        Spacer(Modifier.height(Dimens.md))
        PlantoraTextField(splitCash, onSplitCash, label = "Cash part (₹)", keyboardType = KeyboardType.Decimal)
        Spacer(Modifier.height(Dimens.xs))
        Text("UPI part: ${upi.format()}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
