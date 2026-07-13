package com.plantora.billing.ui.owner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.plantora.billing.domain.Labourer
import com.plantora.billing.domain.LabourPayment
import com.plantora.billing.domain.Money
import com.plantora.billing.domain.OwnerCashInHand
import com.plantora.billing.domain.OwnerStaff
import com.plantora.billing.domain.ShopOverviewRow
import com.plantora.billing.domain.StaffPerf
import com.plantora.billing.domain.User
import com.plantora.billing.domain.formatBillTime
import com.plantora.billing.domain.toDisplay
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

/**
 * Period selector: four equal-width chips (Today / Week / Month / Custom) — each
 * takes the same width via weight(1f). When Custom is chosen, two date steppers
 * appear for an arbitrary range.
 */
@Composable
private fun PeriodSelector(
    period: OwnerPeriod,
    customFrom: java.time.LocalDate,
    customTo: java.time.LocalDate,
    onPeriod: (OwnerPeriod) -> Unit,
    onFrom: (java.time.LocalDate) -> Unit,
    onTo: (java.time.LocalDate) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
            OwnerPeriod.entries.forEach { p ->
                FilterChip(
                    selected = period == p,
                    onClick = { onPeriod(p) },
                    label = {
                        Text(
                            p.label,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (period == OwnerPeriod.CUSTOM) {
            DateStepper("From", customFrom, onFrom)
            DateStepper("To", customTo, onTo)
        }
    }
}

@Composable
private fun DateStepper(label: String, date: java.time.LocalDate, onChange: (java.time.LocalDate) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(44.dp),
        )
        IconButton(onClick = { onChange(date.minusDays(1)) }) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Earlier")
        }
        Text(
            date.toDisplay(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        IconButton(
            onClick = { onChange(date.plusDays(1)) },
            enabled = date.isBefore(com.plantora.billing.domain.todayInShopZone()),
        ) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Later")
        }
    }
}

/** Horizontal comparison of each shop's sales for the selected period. */
@Composable
private fun SalesByShopCard(shops: List<ShopOverviewRow>) {
    PlantoraCard {
        Text("Sales by shop", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Dimens.md))
        com.plantora.billing.ui.components.charts.BarChart(
            data = shops.map {
                com.plantora.billing.ui.components.charts.BarDatum(
                    label = it.shopName,
                    value = it.totalSales.amount.toFloat(),
                    valueLabel = it.totalSales.format(),
                    sub = "${it.billCount} bills",
                )
            },
        )
    }
}

/** Cash vs UPI vs Due split across all owned shops. */
@Composable
private fun PaymentMixCard(cash: Money, upi: Money, due: Money) {
    val total = (cash.amount + upi.amount + due.amount).toFloat().coerceAtLeast(0.0001f)
    PlantoraCard {
        Text("Payment mix", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Dimens.md))
        MixBar("Cash", cash, cash.amount.toFloat() / total, com.plantora.billing.ui.theme.CashGreen)
        Spacer(Modifier.height(Dimens.sm))
        MixBar("UPI", upi, upi.amount.toFloat() / total, com.plantora.billing.ui.theme.UpiBlue)
        Spacer(Modifier.height(Dimens.sm))
        MixBar("Due", due, due.amount.toFloat() / total, MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun MixBar(label: String, money: Money, frac: Float, color: androidx.compose.ui.graphics.Color) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MoneyText(money, style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(Dimens.xs))
        androidx.compose.foundation.layout.Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
        ) {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .fillMaxWidth(frac.coerceIn(0f, 1f))
                    .height(8.dp)
                    .background(color, androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
            )
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
                    item {
                        PeriodSelector(
                            period = ui.period,
                            customFrom = ui.customFrom,
                            customTo = ui.customTo,
                            onPeriod = viewModel::setPeriod,
                            onFrom = viewModel::setCustomFrom,
                            onTo = viewModel::setCustomTo,
                        )
                    }
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
                        // Graphs: per-shop comparison + payment split for the period.
                        if (o.shops.any { it.totalSales.isPositive() }) {
                            item { SalesByShopCard(o.shops) }
                        }
                        if ((o.cashTotal.amount + o.upiTotal.amount + o.dueTotal.amount).signum() > 0) {
                            item { PaymentMixCard(o.cashTotal, o.upiTotal, o.dueTotal) }
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
            Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(Dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.md),
        ) {
            item {
                PeriodSelector(
                    period = ui.period,
                    customFrom = ui.customFrom,
                    customTo = ui.customTo,
                    onPeriod = viewModel::setPeriod,
                    onFrom = viewModel::setCustomFrom,
                    onTo = viewModel::setCustomTo,
                )
            }
            ui.report?.let { r ->
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                        KpiCard("Sales", r.totalSales.format(), Modifier.weight(1f))
                        KpiCard("Expenses", r.totalExpenses.format(), Modifier.weight(1f))
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
                item {
                    // Real cash in the drawer from the server: running all-time
                    // carry-over, or just this day's (cash sales − cash expenses − cash labour).
                    CashInHandCard(ui.cashInHand, ui.cashFull, viewModel::setCashFull)
                }
                // Expense breakdown for the period so the owner can see where money went.
                if (r.expenses.isNotEmpty()) {
                    item { SectionHeader("Expenses") }
                    items(r.expenses, key = { it.id }) { e ->
                        Row(Modifier.fillMaxWidth().padding(vertical = Dimens.xs), verticalAlignment = Alignment.CenterVertically) {
                            Text(e.reason, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            MoneyText(e.amount, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                        }
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

            // Saved bills for the period — who sold, when, to whom, how paid.
            item { SectionHeader("Bills") }
            if (ui.billsLoading && ui.bills.isEmpty()) {
                item { Text("Loading bills…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else if (ui.bills.isEmpty()) {
                item { Text("No bills in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(ui.bills, key = { it.id }) { b -> OwnerBillRow(b) }
            }

            // Labour roster (read-only) — workers, wage, days worked, balance.
            item { SectionHeader("Labour") }
            if (ui.labourers.isEmpty()) {
                item { Text("No workers yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(ui.labourers, key = { it.id }) { l -> OwnerLabourerRow(l, onOpen = { viewModel.openLabourer(l) }) }
            }

            item { SectionHeader("Staff") }
            items(ui.staff, key = { it.id }) { s -> StaffRow(s, onRemove = { viewModel.deleteStaff(s) }) }
            item { AddStaff(ui.newStaff, viewModel) }
        }
    }

    ui.labourerDetail?.let { detail ->
        ModalBottomSheet(onDismissRequest = viewModel::closeLabourer, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            OwnerLabourerDetailSheet(detail)
        }
    }
}

@Composable
private fun CashInHandCard(cih: OwnerCashInHand?, full: Boolean, onFull: (Boolean) -> Unit) {
    PlantoraCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Cash in hand" + if (full) " • all time" else " • this day",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            FilterChip(selected = full, onClick = { onFull(true) }, label = { Text("Full") })
            Spacer(Modifier.width(Dimens.xs))
            FilterChip(selected = !full, onClick = { onFull(false) }, label = { Text("Per day") })
        }
        Spacer(Modifier.height(Dimens.xs))
        MoneyText(
            (if (full) cih?.running else cih?.today) ?: Money.ZERO,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun OwnerLabourerRow(l: Labourer, onOpen: () -> Unit) {
    PlantoraCard(modifier = Modifier.clickable(onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(l.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${l.gender.replaceFirstChar { it.uppercase() }} • ${l.defaultWage.format()}/day • ${l.daysWorked} day(s)" + (l.phone?.let { " • $it" } ?: ""),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                MoneyText(
                    Money(l.balanceToPay.amount.abs()),
                    style = MaterialTheme.typography.titleMedium,
                    color = when {
                        l.balanceToPay.isPositive() -> MaterialTheme.colorScheme.error
                        l.balanceToPay.isNegative() -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    if (l.balanceToPay.isNegative()) "Paid ahead" else "To pay",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OwnerLabourerDetailSheet(detail: LabourerDetail) {
    val l = detail.labourer
    Column(Modifier.fillMaxWidth().padding(horizontal = Dimens.lg).padding(bottom = Dimens.xl)) {
        Text(l.name, style = MaterialTheme.typography.headlineMedium)
        Text(
            "${l.gender.replaceFirstChar { it.uppercase() }}" + (l.phone?.let { " • $it" } ?: "") + (l.aadhaar?.let { " • Aadhaar $it" } ?: ""),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Dimens.md))
        OwnerStatementRow("Days worked", "${l.daysWorked} day(s)")
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        OwnerStatementRow("Earned (${l.defaultWage.format()}/day)", l.earned.format())
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        OwnerStatementRow("Total paid", l.totalPaid.format())
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        Row(Modifier.fillMaxWidth().padding(vertical = Dimens.sm), verticalAlignment = Alignment.CenterVertically) {
            Text(if (l.balanceToPay.isNegative()) "Paid ahead" else "Balance to pay", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            MoneyText(
                Money(l.balanceToPay.amount.abs()),
                style = MaterialTheme.typography.titleLarge,
                color = when {
                    l.balanceToPay.isPositive() -> MaterialTheme.colorScheme.error
                    l.balanceToPay.isNegative() -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
        Spacer(Modifier.height(Dimens.md))
        Text("Payment history", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Dimens.sm))
        if (detail.loading) {
            Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (detail.payments.isEmpty()) {
            Text("No payments yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            detail.payments.take(50).forEach { p ->
                Row(Modifier.fillMaxWidth().padding(vertical = Dimens.xs), verticalAlignment = Alignment.CenterVertically) {
                    val tag = when (p.kind) { "advance" -> " • advance"; "due_clear" -> " • due cleared"; else -> if (p.days != null) " • ${p.days} day(s)" else "" }
                    Text(formatBillTime(p.createdAt) + " • " + p.paymentMethod.label + tag, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    MoneyText(p.totalAmount, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun OwnerStatementRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = Dimens.sm), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun OwnerBillRow(b: com.plantora.billing.domain.OwnerBill) {
    PlantoraCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    b.customerName?.takeIf { it.isNotBlank() } ?: "Walk-in customer",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${com.plantora.billing.domain.formatBillTime(b.createdAt)} • by ${salespersonName(b.salespersonEmail)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${b.itemCount} item${if (b.itemCount == 1) "" else "s"} • ${b.paymentMethod.uppercase()}" +
                        if (b.dueAmount.isPositive()) " • Due ${b.dueAmount.format()}" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (b.dueAmount.isPositive()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MoneyText(b.total, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun salespersonName(email: String?): String =
    email?.substringBefore("@")?.takeIf { it.isNotBlank() } ?: "Unknown"

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
        Text("Add salesperson", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Dimens.sm))
        PlantoraTextField(form.email, viewModel::setStaffEmail, label = "Login email")
        Spacer(Modifier.height(Dimens.sm))
        PlantoraTextField(form.password, viewModel::setStaffPassword, label = "Password (min 8)")
        form.error?.let { Spacer(Modifier.height(Dimens.sm)); Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(Dimens.md))
        PrimaryButton("Add salesperson", onClick = viewModel::addStaff, enabled = form.canSave, loading = form.saving)
    }
}
