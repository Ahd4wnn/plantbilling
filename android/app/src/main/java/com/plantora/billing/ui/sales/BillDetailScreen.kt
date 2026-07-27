package com.plantora.billing.ui.sales

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plantora.billing.R
import com.plantora.billing.domain.BillDetail
import com.plantora.billing.domain.Money
import com.plantora.billing.domain.formatBillTime
import com.plantora.billing.ui.billing.PrintPhase
import com.plantora.billing.ui.components.ErrorState
import com.plantora.billing.ui.components.LoadingState
import com.plantora.billing.ui.components.MoneyText
import com.plantora.billing.ui.components.PlantoraCard
import com.plantora.billing.ui.components.PrimaryButton
import com.plantora.billing.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillDetailScreen(
    onBack: () -> Unit,
    canEdit: Boolean = false,
    canDelete: Boolean = false,
    onEdit: () -> Unit = {},
    viewModel: BillDetailViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }

    // Refresh on return so an owner's edits show immediately.
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose { }
    }

    // Once the bill is deleted, leave the screen.
    androidx.compose.runtime.LaunchedEffect(ui.deleted) {
        if (ui.deleted) onBack()
    }

    if (confirmDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.bd_delete_title)) },
            text = { Text(stringResource(R.string.bd_delete_msg)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDelete = false; viewModel.delete() }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_bill)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    if (canEdit && ui.detail != null) {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.cd_edit_bill))
                        }
                    }
                    if (canDelete && ui.detail != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = stringResource(R.string.cd_delete_bill),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            ui.loading -> LoadingState(Modifier.padding(padding))
            ui.error != null -> ErrorState(ui.error!!.resolve(ctx), onRetry = viewModel::load, icon = Icons.Rounded.Print, modifier = Modifier.padding(padding))
            ui.detail != null -> BillDetailBody(
                detail = ui.detail!!,
                printPhase = ui.printPhase,
                printMessage = ui.printMessage?.resolve(ctx),
                onPrint = viewModel::print,
                canEdit = canEdit,
                onEdit = onEdit,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun BillDetailBody(
    detail: BillDetail,
    printPhase: PrintPhase,
    printMessage: String?,
    onPrint: () -> Unit,
    canEdit: Boolean = false,
    onEdit: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.md),
    ) {
        PlantoraCard {
            Text(
                detail.businessName ?: detail.shopName ?: stringResource(R.string.bd_receipt),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(formatBillTime(detail.createdAt), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            detail.customerName?.let {
                Text(
                    detail.customerPhone?.let { p -> stringResource(R.string.bd_customer_phone, it, p) }
                        ?: stringResource(R.string.bd_customer, it),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = Dimens.sm),
                )
            }
            if (detail.isEdited) {
                Text(stringResource(R.string.bd_edited), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            }
        }

        PlantoraCard {
            detail.items.forEach { item ->
                Row(Modifier.fillMaxWidth().padding(vertical = Dimens.xs)) {
                    Column(Modifier.weight(1f)) {
                        Text(item.productName, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.bd_qty_price, item.quantity.toString(), item.unitPrice.format()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    MoneyText(item.lineTotal, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(Modifier.height(Dimens.sm))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            Spacer(Modifier.height(Dimens.sm))
            DetailRow(stringResource(R.string.label_subtotal), detail.subtotal)
            if (detail.discountAmount.isPositive()) DetailRow(stringResource(R.string.label_discount), Money.ZERO - detail.discountAmount)
            Row(Modifier.fillMaxWidth().padding(top = Dimens.xs), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.label_total), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                MoneyText(detail.total, style = MaterialTheme.typography.titleLarge)
            }
        }

        PlantoraCard {
            Text(stringResource(R.string.bd_payment), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Dimens.sm))
            if (detail.cashAmount.isPositive()) DetailRow(stringResource(R.string.pay_cash), detail.cashAmount)
            if (detail.upiAmount.isPositive()) DetailRow(stringResource(R.string.pay_upi), detail.upiAmount)
            if (detail.dueAmount.isPositive()) DetailRow(stringResource(R.string.pay_due), detail.dueAmount)
            detail.remarks?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(Dimens.sm))
                Text(stringResource(R.string.bd_remarks, it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (canEdit) {
            com.plantora.billing.ui.components.SecondaryButton(
                text = stringResource(R.string.cd_edit_bill),
                onClick = onEdit,
                leadingIcon = Icons.Rounded.Edit,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        PrimaryButton(
            text = if (printPhase == PrintPhase.DONE) stringResource(R.string.print_again) else stringResource(R.string.print_receipt),
            onClick = onPrint,
            loading = printPhase == PrintPhase.PRINTING || printPhase == PrintPhase.CONNECTING,
            leadingIcon = Icons.Rounded.Print,
        )
        printMessage?.let {
            Text(
                it,
                color = if (printPhase == PrintPhase.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, money: Money) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoneyText(money, style = MaterialTheme.typography.bodyLarge, emphasize = false)
    }
}
