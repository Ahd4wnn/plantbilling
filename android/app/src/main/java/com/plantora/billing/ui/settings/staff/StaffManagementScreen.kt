package com.plantora.billing.ui.settings.staff

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plantora.billing.domain.Salesperson
import com.plantora.billing.ui.components.EmptyState
import com.plantora.billing.ui.components.ErrorState
import com.plantora.billing.ui.components.LoadingState
import com.plantora.billing.ui.components.PlantoraCard
import com.plantora.billing.ui.components.PlantoraTextField
import com.plantora.billing.ui.components.PrimaryButton
import com.plantora.billing.ui.components.SecondaryButton
import com.plantora.billing.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffManagementScreen(
    onBack: () -> Unit,
    viewModel: StaffViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pendingDelete by remember { mutableStateOf<Salesperson?>(null) }

    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); viewModel.dismissMessage() } }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Salespeople") },
                navigationIcon = { androidx.compose.material3.IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = viewModel::openCreate,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Add staff") },
            )
        },
    ) { padding ->
        when {
            ui.loading -> LoadingState(Modifier.padding(padding))
            ui.error != null -> ErrorState(ui.error!!, onRetry = viewModel::load, icon = Icons.Rounded.Group, modifier = Modifier.padding(padding))
            ui.staff.isEmpty() -> EmptyState(
                icon = Icons.Rounded.Group,
                title = "No staff yet",
                message = "Add a salesperson so they can bill at the counter.",
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(Dimens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.md),
            ) {
                items(ui.staff, key = { it.id }) { sp ->
                    StaffRow(
                        sp = sp,
                        onToggle = { viewModel.toggleActive(sp) },
                        onReset = { viewModel.resetPassword(sp) },
                        onDelete = { pendingDelete = sp },
                    )
                }
            }
        }
    }

    ui.createForm?.let { form ->
        ModalBottomSheet(onDismissRequest = viewModel::closeCreate, sheetState = sheetState) {
            Column(Modifier.fillMaxWidth().padding(horizontal = Dimens.lg).padding(bottom = Dimens.xl)) {
                Text("Add salesperson", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(Dimens.lg))
                PlantoraTextField(form.email, viewModel::setEmail, label = "Login email")
                Spacer(Modifier.height(Dimens.md))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Password: ", style = MaterialTheme.typography.bodyLarge)
                    Text(form.password, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    AssistChip(onClick = viewModel::regeneratePassword, label = { Text("Regenerate") })
                }
                form.error?.let { Spacer(Modifier.height(Dimens.sm)); Text(it, color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.height(Dimens.xl))
                PrimaryButton("Create account", onClick = viewModel::createStaff, enabled = form.canSave, loading = form.saving)
            }
        }
    }

    ui.credentials?.let { cred ->
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = viewModel::dismissCredentials,
            title = { Text(if (cred.isReset) "Password reset" else "Account created") },
            text = {
                Column {
                    Text("Share these credentials with your staff now — the password is shown only once.")
                    Spacer(Modifier.height(Dimens.md))
                    PlantoraCard(contentPadding = PaddingValues(Dimens.md)) {
                        Text("Email: ${cred.email}", style = MaterialTheme.typography.bodyLarge)
                        Text("Password: ${cred.password}", style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { copyToClipboard(context, "${cred.email}\n${cred.password}"); viewModel.dismissCredentials() }) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null); Text("  Copy & close")
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissCredentials) { Text("Close") } },
        )
    }

    pendingDelete?.let { sp ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove salesperson?") },
            text = { Text("Remove ${sp.email}? Their past bills are kept.") },
            confirmButton = { TextButton(onClick = { viewModel.deleteStaff(sp); pendingDelete = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StaffRow(sp: Salesperson, onToggle: () -> Unit, onReset: () -> Unit, onDelete: () -> Unit) {
    PlantoraCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(sp.email, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (sp.isActive) "Active" else "Inactive",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (sp.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
        }
        Spacer(Modifier.height(Dimens.md))
        // Two actions per row so nothing gets squeezed off-screen on a phone.
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
            SecondaryButton(
                if (sp.isActive) "Deactivate" else "Activate",
                onClick = onToggle,
                modifier = Modifier.weight(1f),
            )
            SecondaryButton("Reset password", onClick = onReset, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(Dimens.sm))
        TextButton(
            onClick = onDelete,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.height(20.dp).width(20.dp))
            Spacer(Modifier.width(Dimens.sm))
            Text("Remove salesperson")
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("credentials", text))
}
