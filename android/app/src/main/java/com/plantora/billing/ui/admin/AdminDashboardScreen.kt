package com.plantora.billing.ui.admin

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
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.plantora.billing.data.AdminRepository
import com.plantora.billing.data.remote.friendlyError
import com.plantora.billing.domain.AdminAttention
import com.plantora.billing.domain.AdminOverview
import com.plantora.billing.domain.AdminShopRow
import com.plantora.billing.domain.toApiDate
import com.plantora.billing.domain.todayInShopZone
import com.plantora.billing.ui.components.ErrorState
import com.plantora.billing.ui.components.LoadingState
import com.plantora.billing.ui.components.PlantoraCard
import com.plantora.billing.ui.components.SectionHeader
import com.plantora.billing.ui.components.charts.BarChart
import com.plantora.billing.ui.components.charts.BarDatum
import com.plantora.billing.ui.theme.Dimens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AdminPeriod(val label: String) { TODAY("Today"), WEEK("7 days"), MONTH("30 days") }

data class AdminDashboardState(
    val loading: Boolean = true,
    val error: String? = null,
    val overview: AdminOverview? = null,
    val period: AdminPeriod = AdminPeriod.WEEK,
)

@HiltViewModel
class AdminDashboardViewModel @Inject constructor(
    private val repo: AdminRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(AdminDashboardState())
    val ui: StateFlow<AdminDashboardState> = _ui.asStateFlow()

    init { load() }

    fun setPeriod(p: AdminPeriod) {
        if (p == _ui.value.period) return
        _ui.update { it.copy(period = p) }
        load()
    }

    fun load() {
        val today = todayInShopZone()
        val from = when (_ui.value.period) {
            AdminPeriod.TODAY -> today
            AdminPeriod.WEEK -> today.minusDays(6)
            AdminPeriod.MONTH -> today.minusDays(29)
        }
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.overview(from.toApiDate(), today.toApiDate()) }
                .onSuccess { o -> _ui.update { it.copy(loading = false, overview = o) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = friendlyError(e)) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    adminEmail: String,
    onOpenShop: (String) -> Unit,
    onLogout: () -> Unit,
    viewModel: AdminDashboardViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Admin") },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = "Log out")
                    }
                },
            )
        },
    ) { padding ->
        when {
            ui.loading && ui.overview == null -> LoadingState(Modifier.padding(padding))
            ui.error != null && ui.overview == null ->
                ErrorState(ui.error!!, onRetry = viewModel::load, icon = Icons.Rounded.Storefront, modifier = Modifier.padding(padding))
            else -> {
                val o = ui.overview
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(Dimens.screenPadding),
                    verticalArrangement = Arrangement.spacedBy(Dimens.md),
                ) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                            AdminPeriod.entries.forEach { p ->
                                FilterChip(
                                    selected = ui.period == p,
                                    onClick = { viewModel.setPeriod(p) },
                                    label = { Text(p.label, maxLines = 1) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    if (o != null) {
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                                AdminKpiCard("Shops", o.totalShops.toString(), Modifier.weight(1f))
                                AdminKpiCard("Active", o.activeShops.toString(), Modifier.weight(1f))
                                AdminKpiCard("Inactive", o.inactiveShops.toString(), Modifier.weight(1f))
                            }
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                                AdminKpiCard("New", o.newShops.toString(), Modifier.weight(1f))
                                AdminKpiCard("Staff", o.totalStaff.toString(), Modifier.weight(1f))
                                AdminKpiCard("Bills", o.totalBills.toString(), Modifier.weight(1f))
                            }
                        }

                        // Bills-per-day usage trend (a count, not money).
                        if (o.trend.any { it.bills > 0 }) {
                            item {
                                PlantoraCard {
                                    Text("Bills per day", style = MaterialTheme.typography.titleMedium)
                                    Spacer(Modifier.height(Dimens.md))
                                    BarChart(
                                        data = o.trend.map { t ->
                                            BarDatum(
                                                label = formatDayDate(t.date),
                                                value = t.bills.toFloat(),
                                                valueLabel = t.bills.toString(),
                                            )
                                        },
                                    )
                                }
                            }
                        }

                        // Shops that need attention (inactive / no owner / gone quiet).
                        if (o.attention.isNotEmpty()) {
                            item { SectionHeader("Needs attention") }
                            items(o.attention, key = { "${it.kind}-${it.shopId}" }) { a ->
                                AttentionRow(a, onClick = { onOpenShop(a.shopId) })
                            }
                        }

                        item { SectionHeader("All shops (${o.totalShops})") }
                        if (o.shops.isEmpty()) {
                            item { Text("No shops yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        items(o.shops, key = { it.shopId }) { s -> ShopRow(s, onClick = { onOpenShop(s.shopId) }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttentionRow(a: AdminAttention, onClick: () -> Unit) {
    PlantoraCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(a.shopName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(a.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            Text(
                a.kind.replace('_', ' '),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ShopRow(s: AdminShopRow, onClick: () -> Unit) {
    PlantoraCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    s.shopName + if (!s.isActive) "  (inactive)" else "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    (s.ownerEmail ?: "No owner login") +
                        " • ${s.billsInPeriod} bills • ${s.staffCount} staff",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
