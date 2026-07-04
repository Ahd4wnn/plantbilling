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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plantora.billing.domain.BillListEntry
import com.plantora.billing.domain.daysSince
import com.plantora.billing.domain.formatBillTime
import com.plantora.billing.ui.components.EmptyState
import com.plantora.billing.ui.components.ErrorState
import com.plantora.billing.ui.components.LoadingState
import com.plantora.billing.ui.components.MoneyText
import com.plantora.billing.ui.components.PlantoraCard
import com.plantora.billing.ui.components.PrimaryButton
import com.plantora.billing.ui.components.SecondaryButton
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

    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); viewModel.dismissMessage() } }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Dues") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        when {
            ui.loading -> LoadingState(Modifier.padding(padding))
            ui.error != null -> ErrorState(ui.error!!, onRetry = viewModel::load, icon = Icons.Rounded.AccountBalanceWallet, modifier = Modifier.padding(padding))
            ui.dues.isEmpty() -> EmptyState(
                icon = Icons.Rounded.AccountBalanceWallet,
                title = "No dues",
                message = "Everyone has paid up. Bills with money owed will appear here.",
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(Dimens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.md),
            ) {
                item {
                    PlantoraCard {
                        Text("Total outstanding", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MoneyText(ui.totalOwed, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.error)
                        Text(
                            "${ui.dues.size} customer(s) yet to pay — shared across all staff.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        value = ui.query,
                        onValueChange = viewModel::setQuery,
                        placeholder = { Text("Search by name or phone") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (!ui.hasResults) {
                    item {
                        Text(
                            "No dues match \"${ui.query}\".",
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
                                "  Priority — overdue 30+ days",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    items(ui.priorityDues, key = { it.id }) { entry ->
                        DueRow(
                            entry = entry,
                            settling = ui.settlingId == entry.id,
                            overdue = true,
                            onOpen = { onOpenBill(entry.id) },
                            onCash = { viewModel.markPaid(entry, viaUpi = false) },
                            onUpi = { viewModel.markPaid(entry, viaUpi = true) },
                        )
                    }
                }

                if (ui.otherDues.isNotEmpty()) {
                    if (ui.priorityDues.isNotEmpty()) {
                        item {
                            Text(
                                "Other dues",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    items(ui.otherDues, key = { it.id }) { entry ->
                        DueRow(
                            entry = entry,
                            settling = ui.settlingId == entry.id,
                            overdue = false,
                            onOpen = { onOpenBill(entry.id) },
                            onCash = { viewModel.markPaid(entry, viaUpi = false) },
                            onUpi = { viewModel.markPaid(entry, viaUpi = true) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DueRow(
    entry: BillListEntry,
    settling: Boolean,
    overdue: Boolean,
    onOpen: () -> Unit,
    onCash: () -> Unit,
    onUpi: () -> Unit,
) {
    val days = daysSince(entry.createdAt)
    PlantoraCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onOpen)) {
            Column(Modifier.weight(1f)) {
                // Show the phone number when no name was recorded — a due bill always
                // has a phone (required at checkout), so this identifies the customer.
                Text(
                    entry.customerName ?: entry.customerPhone ?: "Walk-in",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${formatBillTime(entry.createdAt)} • bill ${entry.total.format()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (overdue && days != null) {
                    Text(
                        "Overdue $days days",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Owes", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MoneyText(entry.dueAmount, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            }
        }
        // Explicit, discoverable way to open the underlying bill.
        androidx.compose.material3.TextButton(onClick = onOpen, modifier = Modifier.padding(top = Dimens.xs)) {
            Icon(Icons.Rounded.ReceiptLong, contentDescription = null, modifier = Modifier.padding(end = Dimens.xs))
            Text("View bill")
        }
        Row(Modifier.fillMaxWidth().padding(top = Dimens.sm), horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
            SecondaryButton(
                text = if (settling) "Saving…" else "Paid in cash",
                onClick = onCash,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                text = "Paid by UPI",
                onClick = onUpi,
                loading = settling,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
