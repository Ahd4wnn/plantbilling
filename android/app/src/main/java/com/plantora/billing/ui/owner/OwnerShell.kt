package com.plantora.billing.ui.owner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.rounded.AccountBalanceWallet
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import com.plantora.billing.R
import com.plantora.billing.ui.components.paymentLabel
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
import com.plantora.billing.domain.OwnerDues
import com.plantora.billing.domain.toDisplay
import com.plantora.billing.ui.components.ErrorState
import com.plantora.billing.ui.components.LoadingState
import com.plantora.billing.ui.components.MoneyText
import com.plantora.billing.ui.components.PlantoraCard
import com.plantora.billing.ui.components.PlantoraTextField
import com.plantora.billing.ui.components.PrimaryButton
import com.plantora.billing.ui.components.SectionHeader
import com.plantora.billing.ui.theme.Dimens
import com.plantora.billing.ui.theme.DueAmber

/** The signed-in shell for the multi-shop OWNER (oversight only). */
@Composable
fun OwnerShell(user: User, onLogout: () -> Unit) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "owner_dashboard") {
        composable("owner_dashboard") {
            OwnerDashboardScreen(
                email = user.email,
                onOpenShop = { id -> nav.navigate("owner_shop/$id") },
                onOpenDues = { id -> nav.navigate("owner_dues/$id") },
                onLogout = onLogout,
            )
        }
        composable(
            "owner_dues/{shopId}",
            arguments = listOf(navArgument("shopId") { type = NavType.StringType }),
        ) {
            OwnerDuesScreen(onBack = { nav.popBackStack() })
        }
        composable(
            "owner_shop/{shopId}",
            arguments = listOf(navArgument("shopId") { type = NavType.StringType }),
        ) {
            OwnerShopScreen(onBack = { nav.popBackStack() })
        }
    }
}

/** Localized chip label for an [OwnerPeriod]; the enum's [OwnerPeriod.label] stays English. */
@Composable
private fun ownerPeriodLabel(p: OwnerPeriod): String = when (p) {
    OwnerPeriod.TODAY -> stringResource(R.string.own_today)
    OwnerPeriod.WEEK -> stringResource(R.string.own_week)
    OwnerPeriod.MONTH -> stringResource(R.string.own_month)
    OwnerPeriod.CUSTOM -> stringResource(R.string.period_custom)
}

/** Localized gender label; stored value stays "male"/"female". */
@Composable
private fun ownerGenderLabel(gender: String): String = when (gender) {
    "female" -> stringResource(R.string.lab_gender_female)
    else -> stringResource(R.string.lab_gender_male)
}

/** Localized role label for a stored role code. */
@Composable
private fun roleLabel(role: String): String = when (role.lowercase()) {
    "manager" -> stringResource(R.string.own_role_manager)
    "salesperson" -> stringResource(R.string.own_role_salesperson)
    "shop_owner", "owner" -> stringResource(R.string.own_role_owner)
    else -> role
}

/** Localized label for a stored payment-method code ("cash"/"upi"/"split"/"due"/"none"). */
@Composable
private fun ownerMethodLabel(method: String): String = when (method.lowercase()) {
    "cash" -> stringResource(R.string.pay_cash)
    "upi" -> stringResource(R.string.pay_upi)
    "split" -> stringResource(R.string.pay_split)
    "due" -> stringResource(R.string.pay_due)
    else -> method
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
                            ownerPeriodLabel(p),
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
            DateStepper(stringResource(R.string.report_from), customFrom, onFrom)
            DateStepper(stringResource(R.string.report_to), customTo, onTo)
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
        // Tap to pick any date from a calendar.
        com.plantora.billing.ui.components.DatePickerField(
            date = date,
            onDate = onChange,
            maxDate = com.plantora.billing.domain.todayInShopZone(),
            modifier = Modifier.weight(1f),
        )
    }
}

/** Horizontal comparison of each shop's sales for the selected period. */
@Composable
private fun SalesByShopCard(shops: List<ShopOverviewRow>) {
    PlantoraCard {
        Text(stringResource(R.string.own_sales_by_shop), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Dimens.md))
        com.plantora.billing.ui.components.charts.BarChart(
            data = shops.map {
                com.plantora.billing.ui.components.charts.BarDatum(
                    label = it.shopName,
                    value = it.totalSales.amount.toFloat(),
                    valueLabel = it.totalSales.format(),
                    sub = pluralStringResource(R.plurals.own_shop_bills, it.billCount, it.billCount),
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
        Text(stringResource(R.string.own_payment_mix), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Dimens.md))
        MixBar(stringResource(R.string.pay_cash), cash, cash.amount.toFloat() / total, com.plantora.billing.ui.theme.CashGreen)
        Spacer(Modifier.height(Dimens.sm))
        MixBar(stringResource(R.string.pay_upi), upi, upi.amount.toFloat() / total, com.plantora.billing.ui.theme.UpiBlue)
        Spacer(Modifier.height(Dimens.sm))
        MixBar(stringResource(R.string.pay_due), due, due.amount.toFloat() / total, MaterialTheme.colorScheme.error)
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
    onOpenDues: (String) -> Unit,
    onLogout: () -> Unit,
    viewModel: OwnerDashboardViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.own_your_business)) },
                actions = {
                    IconButton(onClick = onLogout) { Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = stringResource(R.string.action_logout)) }
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
                                KpiCard(stringResource(R.string.own_total_sales), o.totalSales.format(), Modifier.weight(1f))
                                KpiCard(stringResource(R.string.report_net_income), o.netSales.format(), Modifier.weight(1f))
                            }
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                                KpiCard(stringResource(R.string.label_expenses), o.totalExpenses.format(), Modifier.weight(1f))
                                KpiCard(stringResource(R.string.report_bills), o.billCount.toString(), Modifier.weight(1f))
                            }
                        }
                        // Graphs: per-shop comparison + payment split for the period.
                        if (o.shops.any { it.totalSales.isPositive() }) {
                            item { SalesByShopCard(o.shops) }
                        }
                        if ((o.cashTotal.amount + o.upiTotal.amount + o.dueTotal.amount).signum() > 0) {
                            item { PaymentMixCard(o.cashTotal, o.upiTotal, o.dueTotal) }
                        }
                        ui.dues?.takeIf { it.totalOutstanding.isPositive() }?.let { dues ->
                            item { DuesCard(dues, onOpenDues) }
                        }
                        item { SectionHeader(stringResource(R.string.own_shops_count, o.shopCount)) }
                        if (o.shops.isEmpty()) {
                            item { Text(stringResource(R.string.own_no_shops), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        items(o.shops, key = { it.shopId }) { s -> ShopRow(s, onClick = { onOpenShop(s.shopId) }) }

                        item { SectionHeader(stringResource(R.string.own_top_sellers)) }
                        if (o.staff.isEmpty()) {
                            item { Text(stringResource(R.string.own_no_sales_period), color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
                    Text(st.email ?: stringResource(R.string.pay_none), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(
                            R.string.own_staff_line,
                            st.shopName,
                            roleLabel(st.role),
                            pluralStringResource(R.plurals.own_shop_bills, st.billCount, st.billCount),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MoneyText(st.totalSales, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/**
 * Money still to collect, across every owned shop.
 *
 * Deliberately outside the period filter: the `dueTotal` on each [ShopRow] is
 * "due created in the chosen dates" and falls to zero when the owner switches to
 * Today. This is the standing balance — what customers actually still owe — so it
 * says "all time" on its face to keep the two from being confused.
 */
@Composable
private fun DuesCard(dues: OwnerDues, onOpenDues: (String) -> Unit) {
    PlantoraCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.AccountBalanceWallet,
                contentDescription = null,
                tint = DueAmber,
                modifier = Modifier.padding(end = Dimens.sm),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.own_dues_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.own_dues_all_time),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MoneyText(
                dues.totalOutstanding,
                style = MaterialTheme.typography.titleLarge,
                color = DueAmber,
            )
        }
        dues.shops.filter { it.outstanding.isPositive() }.forEach { shop ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpenDues(shop.shopId) }
                    .padding(vertical = Dimens.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(shop.shopName, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        pluralStringResource(
                            R.plurals.own_dues_customers, shop.customerCount, shop.customerCount,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MoneyText(shop.outstanding, style = MaterialTheme.typography.titleMedium)
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                Text(
                    stringResource(
                        R.string.own_shop_line,
                        pluralStringResource(R.plurals.own_shop_bills, s.billCount, s.billCount),
                        s.netSales.format(),
                        s.dueTotal.format(),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
    var pendingStaffDelete by remember { mutableStateOf<OwnerStaff?>(null) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it.resolve(ctx)); viewModel.dismissMessage() } }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(ui.shop?.name ?: stringResource(R.string.own_shop_fallback)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back)) } },
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
                        KpiCard(stringResource(R.string.label_sales), r.totalSales.format(), Modifier.weight(1f))
                        KpiCard(stringResource(R.string.label_expenses), r.totalExpenses.format(), Modifier.weight(1f))
                        KpiCard(stringResource(R.string.own_net), r.netSales.format(), Modifier.weight(1f))
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                        KpiCard(stringResource(R.string.pay_cash), r.cashTotal.format(), Modifier.weight(1f))
                        KpiCard(stringResource(R.string.pay_upi), r.upiTotal.format(), Modifier.weight(1f))
                        KpiCard(stringResource(R.string.pay_due), r.dueTotal.format(), Modifier.weight(1f))
                    }
                }
                item {
                    // Real cash in the drawer from the server: running all-time
                    // carry-over, or just this day's (cash sales − cash expenses − cash labour).
                    CashInHandCard(ui.cashInHand, ui.cashFull, viewModel::setCashFull)
                }
                // Expense breakdown for the period so the owner can see where money went.
                if (r.expenses.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.label_expenses)) }
                    items(r.expenses, key = { it.id }) { e -> OwnerExpenseRow(e) }
                }
                if (r.topProducts.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.report_top_products)) }
                    items(r.topProducts.take(8), key = { it.productName }) { p ->
                        Row(Modifier.fillMaxWidth().padding(vertical = Dimens.xs), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.own_product_qty, p.productName, p.quantity.toString()), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            MoneyText(p.totalSales, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            // Saved bills for the period — who sold, when, to whom, how paid.
            item { SectionHeader(stringResource(R.string.sales_bills)) }
            if (ui.billsLoading && ui.bills.isEmpty()) {
                item { Text(stringResource(R.string.own_loading_bills), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else if (ui.bills.isEmpty()) {
                item { Text(stringResource(R.string.own_no_bills_period), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(ui.bills, key = { it.id }) { b -> OwnerBillRow(b, onClick = { viewModel.openBill(b.id) }) }
            }

            // Labour roster (read-only) — workers, wage, days worked, balance.
            item { SectionHeader(stringResource(R.string.more_labour)) }
            if (ui.labourers.isEmpty()) {
                item { Text(stringResource(R.string.own_no_workers), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(ui.labourers, key = { it.id }) { l -> OwnerLabourerRow(l, onOpen = { viewModel.openLabourer(l) }) }
            }

            item { SectionHeader(stringResource(R.string.own_staff)) }
            items(ui.staff, key = { it.id }) { s -> StaffRow(s, onRemove = { pendingStaffDelete = s }) }
            item { AddStaff(ui.newStaff, viewModel) }
        }
    }

    ui.labourerDetail?.let { detail ->
        ModalBottomSheet(onDismissRequest = viewModel::closeLabourer, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            OwnerLabourerDetailSheet(detail)
        }
    }

    ui.billDetail?.let { detail ->
        ModalBottomSheet(onDismissRequest = viewModel::closeBill, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            OwnerBillDetailSheet(detail)
        }
    }

    pendingStaffDelete?.let { s ->
        com.plantora.billing.ui.components.TypeEmailToDeleteDialog(
            email = s.email,
            title = stringResource(R.string.staff_remove_title),
            body = stringResource(R.string.staff_remove_body, s.email),
            confirmLabel = stringResource(R.string.del_confirm),
            onConfirm = { viewModel.deleteStaff(s); pendingStaffDelete = null },
            onDismiss = { pendingStaffDelete = null },
        )
    }
}

@Composable
private fun CashInHandCard(cih: OwnerCashInHand?, full: Boolean, onFull: (Boolean) -> Unit) {
    PlantoraCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (full) stringResource(R.string.own_cih_all) else stringResource(R.string.own_cih_day),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            FilterChip(selected = full, onClick = { onFull(true) }, label = { Text(stringResource(R.string.own_full)) })
            Spacer(Modifier.width(Dimens.xs))
            FilterChip(selected = !full, onClick = { onFull(false) }, label = { Text(stringResource(R.string.own_per_day_chip)) })
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
                    ownerGenderLabel(l.gender) + " • " + stringResource(R.string.lab_per_day, l.defaultWage.format()) +
                        " • " + stringResource(R.string.lab_days_count, l.daysWorked) + (l.phone?.let { " • $it" } ?: ""),
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
                    if (l.balanceToPay.isNegative()) stringResource(R.string.lab_paid_ahead_label) else stringResource(R.string.borrow_filter_open),
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
            ownerGenderLabel(l.gender) + (l.phone?.let { " • $it" } ?: "") + (l.aadhaar?.let { " • " + stringResource(R.string.lab_aadhaar_val, it) } ?: ""),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Dimens.md))
        OwnerStatementRow(stringResource(R.string.lab_days_worked), stringResource(R.string.lab_days_count, l.daysWorked))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        OwnerStatementRow(stringResource(R.string.lab_earned, l.defaultWage.format()), l.earned.format())
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        OwnerStatementRow(stringResource(R.string.lab_total_paid), l.totalPaid.format())
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        Row(Modifier.fillMaxWidth().padding(vertical = Dimens.sm), verticalAlignment = Alignment.CenterVertically) {
            Text(if (l.balanceToPay.isNegative()) stringResource(R.string.lab_paid_ahead_label) else stringResource(R.string.lab_balance_to_pay_label), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
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
        Text(stringResource(R.string.lab_payment_history), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Dimens.sm))
        if (detail.loading) {
            Text(stringResource(R.string.action_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (detail.payments.isEmpty()) {
            Text(stringResource(R.string.lab_no_payments_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            detail.payments.take(50).forEach { p ->
                Row(Modifier.fillMaxWidth().padding(vertical = Dimens.xs), verticalAlignment = Alignment.CenterVertically) {
                    val tag = when (p.kind) {
                        "advance" -> " • " + stringResource(R.string.lab_tag_advance)
                        "due_clear" -> " • " + stringResource(R.string.lab_tag_due_cleared)
                        else -> if (p.days != null) " • " + stringResource(R.string.lab_days_count, p.days) else ""
                    }
                    Text(formatBillTime(p.createdAt) + " • " + paymentLabel(p.paymentMethod) + tag, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    MoneyText(p.totalAmount, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/**
 * One expense in the owner's shop view. Always shows category + date/time + amount;
 * tapping the row reveals its remarks (the note) and how it was paid.
 */
@Composable
private fun OwnerExpenseRow(e: com.plantora.billing.domain.Expense) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = Dimens.xs),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(e.displayName, style = MaterialTheme.typography.bodyLarge)
                Text(
                    formatBillTime(e.createdAt) + " • " + ownerMethodLabel(e.paymentMethod),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MoneyText(e.amount, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
        }
        if (expanded) {
            Text(
                e.note?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.own_expense_remarks, it) }
                    ?: stringResource(R.string.own_expense_no_remarks),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Dimens.xs),
            )
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
private fun OwnerBillRow(b: com.plantora.billing.domain.OwnerBill, onClick: () -> Unit) {
    PlantoraCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    b.customerName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.own_walkin),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.own_by, com.plantora.billing.domain.formatBillTime(b.createdAt), salespersonName(b.salespersonEmail)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    pluralStringResource(R.plurals.item_count, b.itemCount, b.itemCount) + " • " + ownerMethodLabel(b.paymentMethod) +
                        if (b.dueAmount.isPositive()) " • " + stringResource(R.string.own_due, b.dueAmount.format()) else "",
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
private fun OwnerBillDetailSheet(detail: BillDetailState) {
    Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.lg).padding(bottom = Dimens.xl)) {
        Text(stringResource(R.string.own_bill_details), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(Dimens.md))
        val b = detail.bill
        if (detail.loading || b == null) {
            Text(stringResource(R.string.action_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Dimens.lg))
            return@Column
        }
        Text(
            stringResource(R.string.own_by, com.plantora.billing.domain.formatBillTime(b.createdAt), salespersonName(b.salespersonEmail)) + if (b.isEdited) " • " + stringResource(R.string.status_edited) else "",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Dimens.xs))
        Text(b.customerName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.own_walkin), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        b.customerPhone?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(Dimens.md))
        if (b.items.isEmpty()) {
            Text(stringResource(R.string.own_no_items), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            b.items.forEach { it2 ->
                Row(Modifier.fillMaxWidth().padding(vertical = Dimens.xs), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(it2.productName, style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.own_price_qty, it2.unitPrice.format(), it2.quantity.toString()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    MoneyText(it2.lineTotal, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Spacer(Modifier.height(Dimens.sm))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        Spacer(Modifier.height(Dimens.sm))
        OwnerStatementRow(stringResource(R.string.label_subtotal), b.subtotal.format())
        if (b.discountAmount.isPositive()) OwnerStatementRow(stringResource(R.string.label_discount), "− ${b.discountAmount.format()}")
        OwnerStatementRow(stringResource(R.string.label_total), b.total.format())
        if (b.cashAmount.isPositive()) OwnerStatementRow(stringResource(R.string.pay_cash), b.cashAmount.format())
        if (b.upiAmount.isPositive()) OwnerStatementRow(stringResource(R.string.pay_upi), b.upiAmount.format())
        if (b.dueAmount.isPositive()) OwnerStatementRow(stringResource(R.string.pay_due), b.dueAmount.format())
        b.remarks?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(Dimens.sm))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StaffRow(s: OwnerStaff, onRemove: () -> Unit) {
    PlantoraCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.email, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.own_staff_role_line, roleLabel(s.role), if (s.isActive) stringResource(R.string.staff_active) else stringResource(R.string.label_inactive)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.material3.TextButton(onClick = onRemove) { Text(stringResource(R.string.own_remove), color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun AddStaff(form: NewStaffForm, viewModel: OwnerShopViewModel) {
    PlantoraCard {
        Text(stringResource(R.string.staff_add_salesperson), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Dimens.sm))
        PlantoraTextField(form.email, viewModel::setStaffEmail, label = stringResource(R.string.staff_login_email))
        Spacer(Modifier.height(Dimens.sm))
        PlantoraTextField(form.password, viewModel::setStaffPassword, label = stringResource(R.string.own_password_min))
        form.error?.let { Spacer(Modifier.height(Dimens.sm)); Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(Dimens.md))
        PrimaryButton(stringResource(R.string.staff_add_salesperson), onClick = viewModel::addStaff, enabled = form.canSave, loading = form.saving)
    }
}
