package com.plantora.billing.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Delete
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.plantora.billing.data.AdminRepository
import com.plantora.billing.data.remote.friendlyError
import com.plantora.billing.domain.AdminNotification
import com.plantora.billing.domain.formatBillTime
import com.plantora.billing.ui.components.PlantoraCard
import com.plantora.billing.ui.components.PlantoraTextField
import com.plantora.billing.ui.components.PrimaryButton
import com.plantora.billing.ui.components.SectionHeader
import com.plantora.billing.ui.theme.Dimens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShopOption(val id: String, val name: String)

data class AdminNotifyState(
    val shops: List<ShopOption> = emptyList(),
    val title: String = "",
    val body: String = "",
    val toAll: Boolean = true,
    val selected: Set<String> = emptySet(),
    val sending: Boolean = false,
    val message: String? = null,
    val history: List<AdminNotification> = emptyList(),
) {
    val canSend: Boolean
        get() = title.isNotBlank() && body.isNotBlank() && !sending && (toAll || selected.isNotEmpty())
}

@HiltViewModel
class AdminNotifyViewModel @Inject constructor(
    private val repo: AdminRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(AdminNotifyState())
    val ui: StateFlow<AdminNotifyState> = _ui.asStateFlow()

    init {
        loadShops()
        loadHistory()
    }

    fun setTitle(v: String) = _ui.update { it.copy(title = v) }
    fun setBody(v: String) = _ui.update { it.copy(body = v) }
    fun setToAll(v: Boolean) = _ui.update { it.copy(toAll = v) }
    fun dismissMessage() = _ui.update { it.copy(message = null) }

    fun toggleShop(id: String) = _ui.update {
        it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id)
    }

    private fun loadShops() {
        viewModelScope.launch {
            runCatching { repo.overview(null, null) }
                .onSuccess { o -> _ui.update { it.copy(shops = o.shops.map { s -> ShopOption(s.shopId, s.shopName) }) } }
        }
    }

    fun loadHistory() {
        viewModelScope.launch {
            runCatching { repo.notifications() }
                .onSuccess { list -> _ui.update { it.copy(history = list) } }
        }
    }

    fun send() {
        val s = _ui.value
        if (!s.canSend) return
        _ui.update { it.copy(sending = true) }
        viewModelScope.launch {
            val shopIds = if (s.toAll) emptyList() else s.selected.toList()
            runCatching { repo.sendNotification(s.title, s.body, shopIds) }
                .onSuccess {
                    _ui.update {
                        it.copy(
                            sending = false,
                            title = "",
                            body = "",
                            selected = emptySet(),
                            message = "Sent to ${if (s.toAll) "all shops" else "${shopIds.size} shop(s)"}.",
                        )
                    }
                    loadHistory()
                }
                .onFailure { e -> _ui.update { it.copy(sending = false, message = friendlyError(e)) } }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            runCatching { repo.deleteNotification(id) }
                .onSuccess {
                    _ui.update { it.copy(history = it.history.filterNot { n -> n.id == id }, message = "Notification removed.") }
                }
                .onFailure { e -> _ui.update { it.copy(message = friendlyError(e)) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdminNotifyScreen(
    onLogout: () -> Unit,
    viewModel: AdminNotifyViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); viewModel.dismissMessage() } }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Text("Notify shops") }) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(Dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.md),
        ) {
            item {
                PlantoraCard {
                    PlantoraTextField(ui.title, viewModel::setTitle, label = "Title")
                    Spacer(Modifier.height(Dimens.sm))
                    PlantoraTextField(ui.body, viewModel::setBody, label = "Message", singleLine = false)
                    Spacer(Modifier.height(Dimens.md))
                    Text("Send to", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Dimens.xs))
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                        FilterChip(selected = ui.toAll, onClick = { viewModel.setToAll(true) }, label = { Text("All shops") })
                        FilterChip(selected = !ui.toAll, onClick = { viewModel.setToAll(false) }, label = { Text("Pick shops") })
                    }
                    if (!ui.toAll) {
                        Spacer(Modifier.height(Dimens.sm))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                            ui.shops.forEach { shop ->
                                FilterChip(
                                    selected = shop.id in ui.selected,
                                    onClick = { viewModel.toggleShop(shop.id) },
                                    label = { Text(shop.name) },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(Dimens.lg))
                    PrimaryButton(
                        text = "Send notification",
                        onClick = viewModel::send,
                        enabled = ui.canSend,
                        loading = ui.sending,
                        leadingIcon = Icons.AutoMirrored.Rounded.Send,
                    )
                }
            }

            item { SectionHeader("Sent") }
            if (ui.history.isEmpty()) {
                item { Text("Nothing sent yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(ui.history, key = { it.id }) { n -> HistoryRow(n, onDelete = { viewModel.delete(n.id) }) }
            }
        }
    }
}

@Composable
private fun HistoryRow(n: AdminNotification, onDelete: () -> Unit) {
    PlantoraCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(n.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(n.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Dimens.xs))
                val reach = if (n.target == "all") "All shops" else "${n.shopCount} shop(s)"
                Text(
                    "$reach • read by ${n.readCount} • ${formatBillTime(n.createdAt)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
