package com.plantora.billing.ui.notifications

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plantora.billing.R
import com.plantora.billing.domain.Notification
import com.plantora.billing.i18n.asString
import com.plantora.billing.ui.components.EmptyState
import com.plantora.billing.ui.components.ErrorState
import com.plantora.billing.ui.components.LoadingState
import com.plantora.billing.ui.components.SecondaryButton
import com.plantora.billing.ui.theme.Dimens

/**
 * Full-screen list of admin notifications for the shop. Opening it marks
 * everything read (clears the bell badge); items that were unread on entry stay
 * visually highlighted for this session so the shop can see what was new.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    // Snapshot which items were unread on entry (for the session highlight), then
    // mark everything read. Guarded so a later state change can't re-snapshot.
    var highlighted by remember { mutableStateOf<Set<String>>(emptySet()) }
    var snapped by remember { mutableStateOf(false) }
    LaunchedEffect(ui.loading) {
        if (!snapped && !ui.loading && ui.error == null) {
            highlighted = ui.items.filter { !it.read }.map { it.id }.toSet()
            snapped = true
            viewModel.markAllRead()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notif_title)) },
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
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                ui.loading -> LoadingState()
                ui.error != null && ui.items.isEmpty() -> ErrorState(
                    message = ui.error!!.asString(),
                    onRetry = viewModel::load,
                    icon = Icons.Rounded.CloudOff,
                )
                ui.items.isEmpty() -> EmptyState(
                    icon = Icons.Rounded.Notifications,
                    title = stringResource(R.string.notif_empty_title),
                    message = stringResource(R.string.notif_empty_message),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(Dimens.screenPadding),
                    verticalArrangement = Arrangement.spacedBy(Dimens.md),
                ) {
                    items(ui.items, key = { it.id }) { n ->
                        NotificationCard(notification = n, isNew = n.id in highlighted)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(notification: Notification, isNew: Boolean) {
    val uriHandler = LocalUriHandler.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isNew) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(Dimens.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isNew) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(10.dp),
                    ) {}
                    Spacer(Modifier.size(Dimens.sm))
                }
                Text(
                    notification.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(Dimens.sm))
            Text(
                notification.body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!notification.actionUrl.isNullOrBlank()) {
                Spacer(Modifier.height(Dimens.md))
                SecondaryButton(
                    text = stringResource(R.string.notif_action),
                    onClick = { runCatching { uriHandler.openUri(notification.actionUrl) } },
                )
            }
            Spacer(Modifier.height(Dimens.sm))
            Text(
                notification.displayTime,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
