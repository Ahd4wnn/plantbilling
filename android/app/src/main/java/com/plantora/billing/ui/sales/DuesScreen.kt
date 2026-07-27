package com.plantora.billing.ui.sales

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import com.plantora.billing.R
import com.plantora.billing.i18n.asString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plantora.billing.domain.BillListEntry
import com.plantora.billing.domain.daysSince
import com.plantora.billing.domain.formatBillTime
import com.plantora.billing.ui.components.EmptyState
import com.plantora.billing.ui.components.ErrorState
import com.plantora.billing.ui.components.LoadingState
import com.plantora.billing.ui.components.MoneyText
import com.plantora.billing.ui.components.PlantoraCard
import com.plantora.billing.ui.components.PlantoraTextField
import com.plantora.billing.ui.components.PrimaryButton
import com.plantora.billing.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuesScreen(
    onBack: () -> Unit,
    onOpenBill: (String) -> Unit,
    viewModel: DuesViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it.resolve(ctx)); viewModel.dismissMessage() } }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text(stringResource(R.string.sales_dues)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back)) }
                },
            )
        },
    ) { padding ->
        when {
            ui.loading -> LoadingState(Modifier.padding(padding))
            ui.error != null -> ErrorState(ui.error!!.resolve(ctx), onRetry = viewModel::load, icon = Icons.Rounded.AccountBalanceWallet, modifier = Modifier.padding(padding))
            ui.dues.isEmpty() -> EmptyState(
                icon = Icons.Rounded.AccountBalanceWallet,
                title = stringResource(R.string.dues_empty_title),
                message = stringResource(R.string.dues_empty_msg),
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(Dimens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.md),
            ) {
                item {
                    PlantoraCard {
                        Text(stringResource(R.string.dues_total_outstanding), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MoneyText(ui.totalOwed, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.error)
                        Text(
                            pluralStringResource(R.plurals.dues_customers_count, ui.dues.size, ui.dues.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        value = ui.query,
                        onValueChange = viewModel::setQuery,
                        placeholder = { Text(stringResource(R.string.dues_search)) },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (!ui.hasResults) {
                    item {
                        Text(
                            stringResource(R.string.dues_no_match, ui.query),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Dimens.md),
                        )
                    }
                }

                // Priority: overdue 30+ days, oldest first — chase these first.
                if (ui.priorityDues.isNotEmpty()) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.PriorityHigh, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text(
                                "  " + stringResource(R.string.dues_priority),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    items(ui.priorityDues, key = { it.id }) { entry ->
                        DueRow(entry = entry, overdue = true, onOpen = { onOpenBill(entry.id) }, onCollect = { viewModel.openSettle(entry) })
                    }
                }

                if (ui.otherDues.isNotEmpty()) {
                    if (ui.priorityDues.isNotEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.dues_other),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    items(ui.otherDues, key = { it.id }) { entry ->
                        DueRow(entry = entry, overdue = false, onOpen = { onOpenBill(entry.id) }, onCollect = { viewModel.openSettle(entry) })
                    }
                }
            }
        }
    }

    ui.settle?.let { target ->
        ModalBottomSheet(onDismissRequest = viewModel::closeSettle, sheetState = sheetState) {
            SettleSheet(
                target = target,
                isManager = ui.isManager,
                onAmount = viewModel::setSettleAmount,
                onMode = viewModel::setSettleMode,
                onSplitCash = viewModel::setSettleSplitCash,
                onConfirm = viewModel::confirmSettle,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettleSheet(
    target: SettleTarget,
    isManager: Boolean,
    onAmount: (String) -> Unit,
    onMode: (SettleMode) -> Unit,
    onSplitCash: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.lg)
            .padding(bottom = Dimens.xl),
    ) {
        Text(stringResource(R.string.settle_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(Dimens.xs))
        Text(
            target.entry.customerName ?: target.entry.customerPhone ?: stringResource(R.string.bill_walkin),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Dimens.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settle_amount_owed), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            MoneyText(target.entry.dueAmount, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(Dimens.md))
        PlantoraTextField(target.amount, onAmount, label = stringResource(R.string.settle_collecting_now), keyboardType = KeyboardType.Decimal)
        if (target.total > target.entry.dueAmount) {
            Spacer(Modifier.height(Dimens.xs))
            Text(stringResource(R.string.settle_too_much, target.entry.dueAmount.format()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(Dimens.lg))
        Text(stringResource(R.string.settle_how_paid), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Dimens.xs))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = target.mode == SettleMode.CASH,
                onClick = { onMode(SettleMode.CASH) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
            ) { Text(stringResource(R.string.pay_cash)) }
            SegmentedButton(
                selected = target.mode == SettleMode.UPI,
                onClick = { onMode(SettleMode.UPI) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
            ) { Text(stringResource(R.string.pay_upi)) }
            SegmentedButton(
                selected = target.mode == SettleMode.SPLIT,
                onClick = { onMode(SettleMode.SPLIT) },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
            ) { Text(stringResource(R.string.pay_split)) }
        }

        if (target.mode == SettleMode.SPLIT) {
            Spacer(Modifier.height(Dimens.md))
            PlantoraTextField(target.splitCash, onSplitCash, label = stringResource(R.string.settle_cash_part), keyboardType = KeyboardType.Decimal)
            Spacer(Modifier.height(Dimens.xs))
            Text(stringResource(R.string.settle_upi_part, target.upiAmount.format()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        target.error?.let {
            Spacer(Modifier.height(Dimens.md))
            Text(it.asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        if (!isManager) {
            Spacer(Modifier.height(Dimens.md))
            Text(
                stringResource(R.string.settle_needs_approval),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(Dimens.xl))
        PrimaryButton(
            text = if (isManager) stringResource(R.string.settle_collect, target.total.format()) else stringResource(R.string.settle_send_approval),
            onClick = onConfirm,
            enabled = target.valid,
            loading = target.submitting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DueRow(
    entry: BillListEntry,
    overdue: Boolean,
    onOpen: () -> Unit,
    onCollect: () -> Unit,
) {
    val days = daysSince(entry.createdAt)
    PlantoraCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onOpen)) {
            Column(Modifier.weight(1f)) {
                // Show the phone number when no name was recorded — a due bill always
                // has a phone (required at checkout), so this identifies the customer.
                Text(
                    entry.customerName ?: entry.customerPhone ?: stringResource(R.string.bill_walkin),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.due_time_bill, formatBillTime(entry.createdAt), entry.total.format()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (overdue && days != null) {
                    Text(
                        pluralStringResource(R.plurals.due_overdue_days, days.toInt(), days.toInt()),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.due_owes), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MoneyText(entry.dueAmount, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            }
        }
        // Explicit, discoverable way to open the underlying bill.
        androidx.compose.material3.TextButton(onClick = onOpen, modifier = Modifier.padding(top = Dimens.xs)) {
            Icon(Icons.Rounded.ReceiptLong, contentDescription = null, modifier = Modifier.padding(end = Dimens.xs))
            Text(stringResource(R.string.due_view_bill))
        }
        if (entry.pendingSettlement) {
            // A collection is already awaiting manager approval — don't offer to
            // collect again until it's reviewed.
            Row(
                Modifier.fillMaxWidth().padding(top = Dimens.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.HourglassTop, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    "  " + stringResource(R.string.due_waiting_approval),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            PrimaryButton(
                text = stringResource(R.string.settle_collect, entry.dueAmount.format()),
                onClick = onCollect,
                modifier = Modifier.fillMaxWidth().padding(top = Dimens.sm),
            )
        }
    }
}
