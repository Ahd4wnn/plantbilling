package com.plantora.billing.ui.owner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plantora.billing.R
import com.plantora.billing.domain.CustomerDue
import com.plantora.billing.domain.formatBillTime
import com.plantora.billing.domain.formatPlainDate
import com.plantora.billing.ui.components.EmptyState
import com.plantora.billing.ui.components.ErrorState
import com.plantora.billing.ui.components.LoadingState
import com.plantora.billing.ui.components.MoneyText
import com.plantora.billing.ui.components.PlantoraCard
import com.plantora.billing.ui.theme.Dimens
import com.plantora.billing.ui.theme.DueAmber

/**
 * Who owes one shop money, biggest balance first, with the unpaid bills behind
 * each one a tap away.
 *
 * Read-only by design: collecting a due moves real cash and has to go through the
 * shop's manager-approval flow, so the owner sees the position without being able
 * to close it from a distance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerDuesScreen(
    onBack: () -> Unit,
    viewModel: OwnerDuesViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.own_dues_title))
                        if (ui.shopName.isNotBlank()) {
                            Text(
                                ui.shopName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            ui.loading -> LoadingState(Modifier.fillMaxSize().padding(padding))
            ui.error != null -> ErrorState(
                ui.error!!,
                onRetry = viewModel::load,
                icon = Icons.Rounded.AccountBalanceWallet,
            )
            ui.customers.isEmpty() -> EmptyState(
                icon = Icons.Rounded.AccountBalanceWallet,
                title = stringResource(R.string.own_dues_none_title),
                message = stringResource(R.string.own_dues_none_msg),
            )
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(Dimens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.sm),
            ) {
                item {
                    PlantoraCard {
                        Text(
                            stringResource(R.string.own_dues_all_time).uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        MoneyText(
                            ui.total,
                            style = MaterialTheme.typography.displaySmall,
                            color = DueAmber,
                        )
                        Text(
                            pluralStringResource(
                                R.plurals.own_dues_customers, ui.customers.size, ui.customers.size,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(ui.customers, key = { it.customerId ?: WALK_IN_KEY }) { customer ->
                    val key = customer.customerId ?: WALK_IN_KEY
                    CustomerDueRow(
                        customer = customer,
                        expanded = ui.expandedId == key,
                        onToggle = { viewModel.toggle(key) },
                    )
                }
            }
        }
    }
}

/** Bills with no customer attached all group under one row; this stands in for its id. */
private const val WALK_IN_KEY = "walk-in"

@Composable
private fun CustomerDueRow(customer: CustomerDue, expanded: Boolean, onToggle: () -> Unit) {
    PlantoraCard(modifier = Modifier.clickable(onClick = onToggle)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    customer.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    buildString {
                        customer.phone?.let { append(it).append(" • ") }
                        append(
                            pluralStringResource(
                                R.plurals.own_dues_bills, customer.billCount, customer.billCount,
                            ),
                        )
                        customer.oldestDueDate?.let {
                            append(" • ")
                            append(stringResource(R.string.own_dues_since, formatPlainDate(it)))
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MoneyText(
                customer.outstanding,
                style = MaterialTheme.typography.titleLarge,
                color = DueAmber,
            )
            Icon(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = stringResource(
                    if (expanded) R.string.own_dues_hide_bills else R.string.own_dues_show_bills,
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(expanded) {
            Column {
                Spacer(Modifier.height(Dimens.sm))
                customer.bills.forEach { bill ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = Dimens.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                bill.billNo?.let { "#$it" } ?: stringResource(R.string.own_dues_bill),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                formatBillTime(bill.createdAt) + " • " +
                                    stringResource(R.string.own_dues_bill_total, bill.total.format()),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        MoneyText(bill.dueAmount, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}
