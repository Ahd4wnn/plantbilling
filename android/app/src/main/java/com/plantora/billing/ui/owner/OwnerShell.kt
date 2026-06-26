package com.plantora.billing.ui.owner

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
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.plantora.billing.domain.Money
import com.plantora.billing.domain.OwnerStaff
import com.plantora.billing.domain.ShopOverviewRow
import com.plantora.billing.domain.StaffPerf
import com.plantora.billing.domain.User
import com.plantora.billing.ui.components.ErrorState
import com.plantora.billing.ui.components.LoadingState
import com.plantora.billing.ui.components.MoneyText
import com.plantora.billing.ui.components.PlantoraCard
import com.plantora.billing.ui.components.PlantoraTextField
import com.plantora.billing.ui.components.PrimaryButton
import com.plantora.billing.ui.components.SectionHeader
import com.plantora.billing.ui.theme.Dimens

/** The signed-in shell for the multi-shop OWNER (oversight only). */
@Composable
fun OwnerShell(user: User, onLogout: () -> Unit) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "owner_dashboard") {
        composable("owner_dashboard") {
            OwnerDashboardScreen(
                email = user.email,
                onOpenShop = { id -> nav.navigate("owner_shop/$id") },
                onLogout = onLogout,
            )
        }
        composable(
            "owner_shop/{shopId}",
            arguments = listOf(navArgument("shopId") { type = NavType.StringType }),
        ) {
            OwnerShopScreen(onBack = { nav.popBackStack() })
        }
    }
}

@Composable
private fun PeriodChips(period: OwnerPeriod, onSelect: (OwnerPeriod) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
        OwnerPeriod.entries.forEach { p ->
            FilterChip(selected = period == p, onClick = { onSelect(p) }, label = { Text(p.label) })
        }
    }
}

@Composable
private fun KpiCard(label: String, value: String, modifier: Modifier = Modifier) {
    PlantoraCard(modifier = modifier) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        // Large amounts (e.g. ₹13,40,000.00) must wrap within the card rather than
        // overflow its bounds and break the row layout. Up to two lines, no clipping.
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, softWrap = true)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OwnerDashboardScreen(
    email: String,
    onOpenShop: (String) -> Unit,
    onLogout: () -> Unit,
    viewModel: OwnerDashboardViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Your business") },
                actions = {
                    IconButton(onClick = onLogout) { Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = "Log out") }
                },
            )
        },
    ) { padding ->
        when {
            ui.loading && ui.overview == null -> LoadingState(Modifier.padding(padding))
            ui.error != null && ui.overview == null -> ErrorState(ui.error!!, onRetry = viewModel::load, icon = Icons.Rounded.Storefront, modifier = Modifier.padding(padding))
            else -> {
                val o = ui.overview
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(Dimens.screenPadding),
                    verticalArrangement = Arrangement.spacedBy(Dimens.md),
                ) {
                    item { PeriodChips(ui.period, viewModel::setPeriod) }
                    if (o != null) {
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                                KpiCard("Total sales", o.totalSales.format(), Modifier.weight(1f))
                                KpiCard("Net income", o.netSales.format(), Modifier.weight(1f))
                            }
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                                KpiCard("Expenses", o.totalExpenses.format(), Modifier.weight(1f))
                                KpiCard("Bills", o.billCount.toString(), Modifier.weight(1f))
                            }
                        }
                        item { SectionHeader("Shops (${o.shopCount})") }
                        if (o.shops.isEmpty()) {
                            item { Text("No shops linked yet. Ask the admin to assign your shops.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        items(o.shops, key = { it.shopId }) { s -> ShopRow(s, onClick = { onOpenShop(s.shopId) }) }

                        item { SectionHeader("Top sellers") }
                        if (o.staff.isEmpty()) {
                            item { Text("No sales in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        itemsIndexedStaff(o.staff)
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexedStaff(rows: List<StaffPerf>) {
    items(rows, key = { "${it.userId}-${it.shopId}" }) { st ->
        PlantoraCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(horizontal = Dimens.sm)) {
                    Text(st.email ?: "—", style = MaterialTheme.typography.titleMedium)
                    Text("${st.shopName} • ${st.role} • ${st.billCount} bills", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                MoneyText(st.totalSales, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun ShopRow(s: ShopOverviewRow, onClick: () -> Unit) {
    PlantoraCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.shopName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("${s.billCount} bills • Net ${s.netSales.format()} • Due ${s.dueTotal.format()}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            MoneyText(s.totalSales, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OwnerShopScreen(
    onBack: () -> Unit,
    viewModel: OwnerShopViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); viewModel.dismissMessage() } }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(ui.shop?.name ?: "Shop") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.md),
        ) {
            item { PeriodChips(ui.period, viewModel::setPeriod) }
            ui.report?.let { r ->
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                        KpiCard("Sales", r.totalSales.format(), Modifier.weight(1f))
                        KpiCard("Net", r.netSales.format(), Modifier.weight(1f))
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                        KpiCard("Cash", r.cashTotal.format(), Modifier.weight(1f))
                        KpiCard("UPI", r.upiTotal.format(), Modifier.weight(1f))
                        KpiCard("Due", r.dueTotal.format(), Modifier.weight(1f))
                    }
                }
                if (r.topProducts.isNotEmpty()) {
                    item { SectionHeader("Top products") }
                    items(r.topProducts.take(8), key = { it.productName }) { p ->
                        Row(Modifier.fillMaxWidth().padding(vertical = Dimens.xs), verticalAlignment = Alignment.CenterVertically) {
                            Text("${p.productName} ×${p.quantity}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            MoneyText(p.totalSales, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            item { SectionHeader("Staff") }
            items(ui.staff, key = { it.id }) { s -> StaffRow(s, onRemove = { viewModel.deleteStaff(s) }) }
            item { AddStaff(ui.newStaff, viewModel) }
        }
    }
}

@Composable
private fun StaffRow(s: OwnerStaff, onRemove: () -> Unit) {
    PlantoraCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.email, style = MaterialTheme.typography.titleMedium)
                Text("${s.role} • ${if (s.isActive) "Active" else "Inactive"}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.material3.TextButton(onClick = onRemove) { Text("Remove", color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun AddStaff(form: NewStaffForm, viewModel: OwnerShopViewModel) {
    PlantoraCard {
        Text("Add staff", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Dimens.sm))
        PlantoraTextField(form.email, viewModel::setStaffEmail, label = "Login email")
        Spacer(Modifier.height(Dimens.sm))
        PlantoraTextField(form.password, viewModel::setStaffPassword, label = "Password (min 8)")
        Spacer(Modifier.height(Dimens.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
            FilterChip(selected = form.role == "salesperson", onClick = { viewModel.setStaffRole("salesperson") }, label = { Text("Salesperson") })
            FilterChip(selected = form.role == "manager", onClick = { viewModel.setStaffRole("manager") }, label = { Text("Manager") })
        }
        form.error?.let { Spacer(Modifier.height(Dimens.sm)); Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(Dimens.md))
        PrimaryButton("Add staff", onClick = viewModel::addStaff, enabled = form.canSave, loading = form.saving)
    }
}
