package com.plantora.billing.ui.customers

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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plantora.billing.R
import com.plantora.billing.domain.BillListEntry
import com.plantora.billing.domain.formatBillTime
import com.plantora.billing.ui.components.ErrorState
import com.plantora.billing.ui.components.LoadingState
import com.plantora.billing.ui.components.MoneyText
import com.plantora.billing.ui.components.PlantoraCard
import com.plantora.billing.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    onBack: () -> Unit,
    onOpenBill: (String) -> Unit,
    viewModel: CustomerDetailViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(ui.name ?: stringResource(R.string.vm_customer_default)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { padding ->
        when {
            ui.loading -> LoadingState(Modifier.padding(padding))
            ui.error != null -> ErrorState(ui.error!!, onRetry = viewModel::load, icon = Icons.Rounded.Group, modifier = Modifier.padding(padding))
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(Dimens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.md),
            ) {
                item {
                    PlantoraCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            LedgerStat(stringResource(R.string.cust_total_spent)) { MoneyText(ui.totalSpent, style = MaterialTheme.typography.titleLarge) }
                            LedgerStat(stringResource(R.string.report_bills)) { Text("${ui.bills.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
                            LedgerStat(stringResource(R.string.cust_on_credit)) {
                                Text(
                                    "${ui.creditBillCount}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (ui.creditBillCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
                item {
                    Text(stringResource(R.string.cust_purchase_history), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Dimens.sm))
                }
                if (ui.bills.isEmpty()) {
                    item { Text(stringResource(R.string.cust_no_bills), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(ui.bills, key = { it.id }) { bill ->
                    LedgerBillRow(bill, onClick = { onOpenBill(bill.id) })
                }
            }
        }
    }
}

@Composable
private fun LedgerStat(label: String, value: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        value()
    }
}

@Composable
private fun LedgerBillRow(bill: BillListEntry, onClick: () -> Unit) {
    PlantoraCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
            Column(Modifier.weight(1f).padding(horizontal = Dimens.md)) {
                Text(formatBillTime(bill.createdAt), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(
                        R.string.cust_bill_line,
                        pluralStringResource(R.plurals.item_count, bill.itemCount, bill.itemCount),
                        com.plantora.billing.ui.components.paymentLabel(bill.paymentMethod),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MoneyText(bill.total, style = MaterialTheme.typography.titleMedium)
        }
    }
}
