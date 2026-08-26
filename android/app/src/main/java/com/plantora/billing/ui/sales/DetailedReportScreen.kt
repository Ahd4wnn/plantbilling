package com.plantora.billing.ui.sales

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Download
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plantora.billing.R
import com.plantora.billing.i18n.asString
import com.plantora.billing.domain.DetailedReport
import com.plantora.billing.domain.Money
import com.plantora.billing.domain.ReportPeriod
import com.plantora.billing.domain.formatBillTime
import com.plantora.billing.domain.toDisplay
import com.plantora.billing.ui.components.LoadingState
import com.plantora.billing.ui.components.PlantoraCard
import com.plantora.billing.ui.components.PrimaryButton
import com.plantora.billing.ui.components.SecondaryButton
import com.plantora.billing.ui.components.charts.BarChart
import com.plantora.billing.ui.components.charts.BarDatum
import com.plantora.billing.ui.components.charts.TrendLineChart
import com.plantora.billing.ui.theme.BrandPrimary
import com.plantora.billing.ui.theme.BrandPrimaryDark
import com.plantora.billing.ui.theme.CashGreen
import com.plantora.billing.ui.theme.Dimens
import com.plantora.billing.ui.theme.UpiBlue
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailedReportScreen(
    onBack: () -> Unit,
    viewModel: ReportViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it.resolve(ctx)); viewModel.dismissMessage() } }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.report_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.md),
        ) {
            item { PeriodPicker(ui, viewModel) }
            item {
                PrimaryButton(text = stringResource(R.string.report_generate), onClick = viewModel::generate, loading = ui.loading)
            }
            ui.error?.let { item { Text(it.asString(), color = MaterialTheme.colorScheme.error) } }

            if (ui.loading) item { LoadingState(Modifier.fillMaxWidth().height(160.dp)) }

            ui.report?.let { report ->
                item { TotalHero(report) }
                item { ExpensesNetRow(report) }
                item { PaymentRow(report) }
                if (report.trend.size >= 2) item { TrendCard(report) }
                if (report.categories.isNotEmpty()) item { CategoryCard(report) }
                if (report.topProducts.isNotEmpty()) item { TopProductsCard(report) }
                if (report.expensesByCategory.isNotEmpty()) item { ExpensesByCategoryCard(report) }
                if (report.expenses.isNotEmpty()) item { ExpensesLogCard(report) }
                item {
                    SecondaryButton(
                        text = if (ui.downloading) stringResource(R.string.action_saving) else stringResource(R.string.report_download),
                        onClick = viewModel::downloadCsv,
                        leadingIcon = Icons.Rounded.Download,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PeriodPicker(ui: ReportUiState, vm: ReportViewModel) {
    PlantoraCard {
        Text(stringResource(R.string.report_period), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Dimens.sm))
        // FlowRow so all four chips (Daily/Weekly/Monthly/Custom) wrap onto a second
        // line on narrow screens instead of the last "Custom" chip overflowing.
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
            verticalArrangement = Arrangement.spacedBy(Dimens.sm),
        ) {
            ReportPeriod.entries.forEach { p ->
                FilterChip(
                    selected = ui.period == p,
                    onClick = { vm.setPeriod(p) },
                    label = { Text(periodLabel(p)) },
                )
            }
        }
        Spacer(Modifier.height(Dimens.md))
        when (ui.period) {
            ReportPeriod.DAILY -> com.plantora.billing.ui.components.DatePickerField(
                date = ui.anchorDate,
                onDate = { vm.setAnchorDate(it) },
                maxDate = com.plantora.billing.domain.todayInShopZone(),
                modifier = Modifier.fillMaxWidth(),
            )
            ReportPeriod.WEEKLY -> {
                val (f, t) = ui.bounds()
                Stepper("${f.toDisplay()} – ${t.toDisplay()}", { vm.setAnchorDate(ui.anchorDate.minusWeeks(1)) }, { vm.setAnchorDate(ui.anchorDate.plusWeeks(1)) })
            }
            ReportPeriod.MONTHLY -> {
                val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
                Stepper(ui.anchorDate.format(monthFmt), { vm.setAnchorDate(ui.anchorDate.minusMonths(1)) }, { vm.setAnchorDate(ui.anchorDate.plusMonths(1)) })
            }
            ReportPeriod.CUSTOM -> {
                Text(stringResource(R.string.report_from), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                com.plantora.billing.ui.components.DatePickerField(
                    date = ui.customFrom,
                    onDate = { vm.setCustomFrom(it) },
                    maxDate = com.plantora.billing.domain.todayInShopZone(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Dimens.sm))
                Text(stringResource(R.string.report_to), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                com.plantora.billing.ui.components.DatePickerField(
                    date = ui.customTo,
                    onDate = { vm.setCustomTo(it) },
                    maxDate = com.plantora.billing.domain.todayInShopZone(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun periodLabel(p: ReportPeriod): String = when (p) {
    ReportPeriod.DAILY -> stringResource(R.string.period_daily)
    ReportPeriod.WEEKLY -> stringResource(R.string.period_weekly)
    ReportPeriod.MONTHLY -> stringResource(R.string.period_monthly)
    ReportPeriod.CUSTOM -> stringResource(R.string.period_custom)
}

@Composable
private fun Stepper(label: String, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = stringResource(R.string.cd_earlier)) }
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = stringResource(R.string.cd_later)) }
    }
}

@Composable
private fun TotalHero(report: DetailedReport) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(BrandPrimary, BrandPrimaryDark)),
                MaterialTheme.shapes.large,
            )
            .padding(Dimens.xl),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.report_total_revenue), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
            Text(report.totalSales.format(), color = Color.White, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(Dimens.md))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                HeroStat(stringResource(R.string.report_bills), "${report.billCount}")
                HeroStat(stringResource(R.string.report_avg_bill), report.averageBillValue.format())
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
        Text(value, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ExpensesNetRow(report: DetailedReport) {
    val netNeg = report.netSales.amount.signum() < 0
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.md)) {
        StatBox(stringResource(R.string.report_total_expenses), "− " + report.totalExpenses.format(), MaterialTheme.colorScheme.error, Modifier.weight(1f))
        StatBox(
            stringResource(R.string.report_net_income),
            (if (netNeg) "− " else "") + Money(report.netSales.amount.abs()).format(),
            if (netNeg) MaterialTheme.colorScheme.error else CashGreen,
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun PaymentRow(report: DetailedReport) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
        StatBox(stringResource(R.string.pay_cash), report.cashTotal.format(), CashGreen, Modifier.weight(1f))
        StatBox(stringResource(R.string.pay_upi), report.upiTotal.format(), UpiBlue, Modifier.weight(1f))
        StatBox(stringResource(R.string.pay_due), report.dueTotal.format(), MaterialTheme.colorScheme.error, Modifier.weight(1f))
    }
}

@Composable
private fun StatBox(label: String, value: String, color: Color, modifier: Modifier) {
    PlantoraCard(modifier = modifier, contentPadding = PaddingValues(Dimens.md)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color, maxLines = 2, softWrap = true)
    }
}

@Composable
private fun TrendCard(report: DetailedReport) {
    PlantoraCard {
        Text(stringResource(R.string.report_sales_trend), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Dimens.md))
        TrendLineChart(values = report.trend.map { it.sales.amount.toFloat() })
        Spacer(Modifier.height(Dimens.sm))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(report.startDate, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(report.endDate, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CategoryCard(report: DetailedReport) {
    PlantoraCard {
        Text(stringResource(R.string.report_by_category), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Dimens.md))
        BarChart(
            data = report.categories.map {
                BarDatum(it.category, it.totalSales.amount.toFloat(), it.totalSales.format(), pluralStringResource(R.plurals.report_qty_sold, it.quantity, it.quantity))
            },
        )
    }
}

@Composable
private fun TopProductsCard(report: DetailedReport) {
    PlantoraCard {
        Text(stringResource(R.string.report_top_products), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Dimens.md))
        BarChart(
            data = report.topProducts.take(10).map {
                BarDatum(it.productName, it.totalSales.amount.toFloat(), it.totalSales.format(), pluralStringResource(R.plurals.report_qty_sold, it.quantity, it.quantity))
            },
            barColor = UpiBlue,
        )
    }
}

@Composable
private fun ExpensesByCategoryCard(report: DetailedReport) {
    PlantoraCard {
        Text(stringResource(R.string.report_expenses_by_category), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Dimens.sm))
        report.expensesByCategory.forEach { c ->
            Row(Modifier.fillMaxWidth().padding(vertical = Dimens.xs), verticalAlignment = Alignment.CenterVertically) {
                Text(c.category, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text("− " + c.total.format(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun ExpensesLogCard(report: DetailedReport) {
    PlantoraCard {
        Text(stringResource(R.string.report_expenses_log), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Dimens.sm))
        report.expenses.forEach { e ->
            Row(Modifier.fillMaxWidth().padding(vertical = Dimens.xs)) {
                Column(Modifier.weight(1f)) {
                    Text(e.displayName, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        (e.note?.let { "$it • " } ?: "") + formatBillTime(e.createdAt),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("− " + e.amount.format(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
