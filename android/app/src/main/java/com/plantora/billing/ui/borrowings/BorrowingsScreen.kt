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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plantora.billing.R
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

/** Localized label for a stored payment-method code ("cash"/"upi"/"split"). */
@Composable
private fun methodLabel(method: String): String = when (method.lowercase()) {
    "cash" -> stringResource(R.string.pay_cash)
    "upi" -> stringResource(R.string.pay_upi)
    "split" -> stringResource(R.string.pay_split)
    else -> method
}

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

    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it.resolve(ctx)); viewModel.dismissMessage() } }

    androidx.compose.material3.Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.more_borrowings)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back)) } },
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
                        Text(stringResource(R.string.borrow_still_to_pay), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                    Text(when (f) { BorrowFilter.ALL -> stringResource(R.string.filter_all); BorrowFilter.OPEN -> stringResource(R.string.borrow_filter_open); BorrowFilter.PAID -> stringResource(R.string.borrow_filter_paid) })
                                }
                            }
                        }
                    }
                }
                item {
                    PrimaryButton(text = stringResource(R.string.borrow_add), onClick = viewModel::openAdd, leadingIcon = Icons.Rounded.Add, modifier = Modifier.fillMaxWidth())
                }
                if (ui.items.isEmpty()) {
                    item {
                        Text(
                            if (ui.filter == BorrowFilter.PAID) stringResource(R.string.borrow_empty_paid) else stringResource(R.string.borrow_empty_open),
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
            title = { Text(stringResource(R.string.borrow_delete_title)) },
            text = { Text(stringResource(R.string.borrow_delete_msg, b.amount.format(), b.lenderName)) },
            confirmButton = { TextButton(onClick = { viewModel.delete(b.id); confirmDelete = null }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.action_cancel)) } },
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
                            stringResource(R.string.borrow_paid),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else if (b.outstanding < b.amount) {
                        Text(
                            stringResource(R.string.borrow_partly_paid),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
                b.lenderPhone?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "${formatBillTime(b.createdAt)} • " + stringResource(R.string.borrow_received_by, methodLabel(b.method)) +
                        if (b.isPaid) " • " + stringResource(R.string.borrow_paid_by, methodLabel(b.paidMethod)) else "",
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
                if (!b.isPaid && b.outstanding < b.amount) {
                    Text(
                        stringResource(R.string.borrow_left, b.outstanding.format()),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!b.isPaid) {
                        TextButton(onClick = onPay) {
                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.height(18.dp))
                            Text(" " + stringResource(R.string.borrow_pay_back))
                        }
                    }
                    IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.cd_delete_borrowing)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSheet(editor: AddEditor, vm: BorrowingsViewModel) {
    Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.lg).padding(bottom = Dimens.xl)) {
        Text(stringResource(R.string.borrow_add), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(Dimens.lg))
        PlantoraTextField(editor.name, vm::setName, label = stringResource(R.string.borrow_from_name))
        Spacer(Modifier.height(Dimens.md))
        PlantoraTextField(editor.phone, vm::setPhone, label = stringResource(R.string.borrow_phone), keyboardType = KeyboardType.Phone)
        Spacer(Modifier.height(Dimens.md))
        PlantoraTextField(editor.amount, vm::setAmount, label = stringResource(R.string.label_amount_rupees), keyboardType = KeyboardType.Decimal)
        Spacer(Modifier.height(Dimens.md))
        MethodPicker(editor.mode, editor.total, editor.splitCash, vm::setAddMode, vm::setAddSplitCash)
        Spacer(Modifier.height(Dimens.md))
        PlantoraTextField(editor.remarks, vm::setRemarks, label = stringResource(R.string.cart_remarks))
        editor.error?.let { Spacer(Modifier.height(Dimens.md)); Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(Dimens.xl))
        PrimaryButton(text = stringResource(R.string.borrow_save), onClick = vm::saveAdd, enabled = editor.canSave, loading = editor.saving, modifier = Modifier.fillMaxWidth())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaySheet(editor: PayEditor, vm: BorrowingsViewModel) {
    val b = editor.borrowing
    Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.lg).padding(bottom = Dimens.xl)) {
        Text(stringResource(R.string.borrow_pay_title, b.lenderName), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(Dimens.md))
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
            Row(Modifier.fillMaxWidth().padding(Dimens.md), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.borrow_still_owed), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                MoneyText(b.outstanding, style = MaterialTheme.typography.titleLarge)
            }
        }
        Spacer(Modifier.height(Dimens.md))
        PlantoraTextField(editor.amount, vm::setPayAmount, label = stringResource(R.string.borrow_paying_now), keyboardType = KeyboardType.Decimal)
        if (editor.total > b.outstanding) {
            Spacer(Modifier.height(Dimens.xs))
            Text(stringResource(R.string.settle_too_much, b.outstanding.format()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(Dimens.md))
        Text(stringResource(R.string.borrow_how_paid), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Dimens.sm))
        MethodPicker(editor.mode, editor.total, editor.splitCash, vm::setPayMode, vm::setPaySplitCash)
        editor.error?.let { Spacer(Modifier.height(Dimens.md)); Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(Dimens.xl))
        PrimaryButton(text = stringResource(R.string.borrow_pay_amount_btn, editor.total.format()), onClick = vm::savePay, enabled = editor.canSave, loading = editor.saving, modifier = Modifier.fillMaxWidth())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MethodPicker(mode: BorrowMode, total: Money, splitCash: String, onMode: (BorrowMode) -> Unit, onSplitCash: (String) -> Unit) {
    Text(stringResource(R.string.borrow_method), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(Dimens.xs))
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        val modes = BorrowMode.entries
        modes.forEachIndexed { i, m ->
            SegmentedButton(selected = mode == m, onClick = { onMode(m) }, shape = SegmentedButtonDefaults.itemShape(i, modes.size)) {
                Text(when (m) { BorrowMode.CASH -> stringResource(R.string.pay_cash); BorrowMode.UPI -> stringResource(R.string.pay_upi); BorrowMode.SPLIT -> stringResource(R.string.pay_split) })
            }
        }
    }
    if (mode == BorrowMode.SPLIT) {
        val cash = Money.parse(splitCash).let { if (it > total) total else it }
        val upi = total - cash
        Spacer(Modifier.height(Dimens.md))
        PlantoraTextField(splitCash, onSplitCash, label = stringResource(R.string.settle_cash_part), keyboardType = KeyboardType.Decimal)
        Spacer(Modifier.height(Dimens.xs))
        Text(stringResource(R.string.settle_upi_part, upi.format()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
