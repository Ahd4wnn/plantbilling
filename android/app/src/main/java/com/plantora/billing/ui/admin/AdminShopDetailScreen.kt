package com.plantora.billing.ui.admin

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
import androidx.compose.material.icons.rounded.Storefront
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
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.plantora.billing.data.AdminRepository
import com.plantora.billing.data.remote.friendlyError
import com.plantora.billing.domain.AdminShopDetail
import com.plantora.billing.domain.formatBillTime
import com.plantora.billing.ui.components.ErrorState
import com.plantora.billing.ui.components.LoadingState
import com.plantora.billing.ui.components.PlantoraCard
import com.plantora.billing.ui.components.SectionHeader
import com.plantora.billing.ui.theme.Dimens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminShopDetailState(
    val loading: Boolean = true,
    val error: String? = null,
    val detail: AdminShopDetail? = null,
)

@HiltViewModel
class AdminShopDetailViewModel @Inject constructor(
    private val repo: AdminRepository,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val shopId: String = savedState["shopId"] ?: ""
    private val _ui = MutableStateFlow(AdminShopDetailState())
    val ui: StateFlow<AdminShopDetailState> = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.shopDetail(shopId) }
                .onSuccess { d -> _ui.update { it.copy(loading = false, detail = d) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = friendlyError(e)) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminShopDetailScreen(
    onBack: () -> Unit,
    viewModel: AdminShopDetailViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(ui.detail?.shopName ?: "Shop") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when {
            ui.loading && ui.detail == null -> LoadingState(Modifier.padding(padding))
            ui.error != null && ui.detail == null ->
                ErrorState(ui.error!!, onRetry = viewModel::load, icon = Icons.Rounded.Storefront, modifier = Modifier.padding(padding))
            ui.detail != null -> {
                val d = ui.detail!!
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(Dimens.screenPadding),
                    verticalArrangement = Arrangement.spacedBy(Dimens.md),
                ) {
                    item {
                        PlantoraCard {
                            Text(
                                d.businessName ?: d.shopName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                if (d.isActive) "Active" else "Inactive",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (d.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.height(Dimens.sm))
                            d.ownerEmail?.let { InfoLine("Owner login", it) }
                            if (d.ownerEmails.isNotEmpty()) InfoLine("Linked owners", d.ownerEmails.joinToString(", "))
                            d.businessPhone?.takeIf { it.isNotBlank() }?.let { InfoLine("Phone", it) }
                            d.businessAddress?.takeIf { it.isNotBlank() }?.let { InfoLine("Address", it) }
                            d.businessUpi?.takeIf { it.isNotBlank() }?.let { InfoLine("UPI", it) }
                            InfoLine("Created", formatBillTime(d.createdAt))
                            d.lastBillAt?.let { InfoLine("Last bill", formatBillTime(it)) }
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                            AdminKpiCard("Staff", d.staffCount.toString(), Modifier.weight(1f))
                            AdminKpiCard("Products", d.productsCount.toString(), Modifier.weight(1f))
                            AdminKpiCard("Customers", d.customersCount.toString(), Modifier.weight(1f))
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                            AdminKpiCard("Bills 7d", d.bills7.toString(), Modifier.weight(1f))
                            AdminKpiCard("Bills 30d", d.bills30.toString(), Modifier.weight(1f))
                        }
                    }
                    item { SectionHeader("Recent activity") }
                    if (d.recentActivity.isEmpty()) {
                        item { Text("No bills yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else {
                        items(d.recentActivity) { act ->
                            PlantoraCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            act.salespersonEmail ?: "Unknown",
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                        Text(
                                            formatBillTime(act.createdAt),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        "${act.itemCount} items",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = Dimens.xs)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.6f))
    }
}
