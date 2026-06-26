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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
                items(ui.dues, key = { it.id }) { entry ->
                    DueRow(
                        entry = entry,
                        settling = ui.settlingId == entry.id,
                        onOpen = { onOpenBill(entry.id) },
                        onCash = { viewModel.markPaid(entry, viaUpi = false) },
                        onUpi = { viewModel.markPaid(entry, viaUpi = true) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DueRow(
    entry: BillListEntry,
    settling: Boolean,
    onOpen: () -> Unit,
    onCash: () -> Unit,
    onUpi: () -> Unit,
) {
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
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Owes", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MoneyText(entry.dueAmount, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = Dimens.md), horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
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
