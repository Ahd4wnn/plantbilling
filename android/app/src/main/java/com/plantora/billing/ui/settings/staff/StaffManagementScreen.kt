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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plantora.billing.R
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

    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it.resolve(ctx)); viewModel.dismissMessage() } }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.more_salespeople)) },
                navigationIcon = { androidx.compose.material3.IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back)) } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = viewModel::openCreate,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.staff_add)) },
            )
        },
    ) { padding ->
        when {
            ui.loading -> LoadingState(Modifier.padding(padding))
            ui.error != null -> ErrorState(ui.error!!, onRetry = viewModel::load, icon = Icons.Rounded.Group, modifier = Modifier.padding(padding))
            ui.staff.isEmpty() -> EmptyState(
                icon = Icons.Rounded.Group,
                title = stringResource(R.string.staff_empty_title),
                message = stringResource(R.string.staff_empty_msg),
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
                        onReset = { viewModel.openReset(sp) },
                        onDelete = { pendingDelete = sp },
                    )
                }
            }
        }
    }

    ui.createForm?.let { form ->
        ModalBottomSheet(onDismissRequest = viewModel::closeCreate, sheetState = sheetState) {
            Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.lg).padding(bottom = Dimens.xl)) {
                Text(stringResource(R.string.staff_add_salesperson), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(Dimens.lg))
                PlantoraTextField(form.email, viewModel::setEmail, label = stringResource(R.string.staff_login_email))
                Spacer(Modifier.height(Dimens.md))
                PlantoraTextField(
                    form.password,
                    viewModel::setPassword,
                    label = stringResource(R.string.staff_password),
                )
                Spacer(Modifier.height(Dimens.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.staff_set_own_or),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(Dimens.sm))
                    AssistChip(onClick = viewModel::regeneratePassword, label = { Text(stringResource(R.string.staff_generate_pw)) })
                }
                form.error?.let { Spacer(Modifier.height(Dimens.sm)); Text(it, color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.height(Dimens.xl))
                PrimaryButton(stringResource(R.string.staff_create_account), onClick = viewModel::createStaff, enabled = form.canSave, loading = form.saving)
            }
        }
    }

    ui.resetForm?.let { reset ->
        ModalBottomSheet(onDismissRequest = viewModel::closeReset, sheetState = sheetState) {
            Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.lg).padding(bottom = Dimens.xl)) {
                Text(stringResource(R.string.staff_reset_password), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(Dimens.sm))
                Text(
                    reset.sp.email,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Dimens.lg))
                PlantoraTextField(
                    reset.password,
                    viewModel::setResetPassword,
                    label = stringResource(R.string.staff_new_password),
                )
                Spacer(Modifier.height(Dimens.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.staff_set_own_or),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(Dimens.sm))
                    AssistChip(onClick = viewModel::regenerateResetPassword, label = { Text(stringResource(R.string.staff_generate_pw)) })
                }
                reset.error?.let { Spacer(Modifier.height(Dimens.sm)); Text(it, color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.height(Dimens.xl))
                PrimaryButton(stringResource(R.string.staff_reset_password), onClick = viewModel::confirmReset, enabled = reset.canSave, loading = reset.saving)
            }
        }
    }

    ui.credentials?.let { cred ->
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = viewModel::dismissCredentials,
            title = { Text(if (cred.isReset) stringResource(R.string.staff_password_reset_title) else stringResource(R.string.staff_account_created)) },
            text = {
                Column {
                    Text(stringResource(R.string.staff_share_note))
                    Spacer(Modifier.height(Dimens.md))
                    PlantoraCard(contentPadding = PaddingValues(Dimens.md)) {
                        Text(stringResource(R.string.staff_email_line, cred.email), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.staff_password_line, cred.password), style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { copyToClipboard(context, "${cred.email}\n${cred.password}"); viewModel.dismissCredentials() }) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null); Text("  " + stringResource(R.string.staff_copy_close))
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissCredentials) { Text(stringResource(R.string.staff_close)) } },
        )
    }

    pendingDelete?.let { sp ->
        com.plantora.billing.ui.components.TypeEmailToDeleteDialog(
            email = sp.email,
            title = stringResource(R.string.staff_remove_title),
            body = stringResource(R.string.staff_remove_body, sp.email),
            confirmLabel = stringResource(R.string.del_confirm),
            onConfirm = { viewModel.deleteStaff(sp); pendingDelete = null },
            onDismiss = { pendingDelete = null },
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
                    if (sp.isActive) stringResource(R.string.staff_active) else stringResource(R.string.label_inactive),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (sp.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
        }
        Spacer(Modifier.height(Dimens.md))
        // Two actions per row so nothing gets squeezed off-screen on a phone.
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
            SecondaryButton(
                if (sp.isActive) stringResource(R.string.staff_deactivate) else stringResource(R.string.staff_activate),
                onClick = onToggle,
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(stringResource(R.string.staff_reset_password), onClick = onReset, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(Dimens.sm))
        TextButton(
            onClick = onDelete,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.height(20.dp).width(20.dp))
            Spacer(Modifier.width(Dimens.sm))
            Text(stringResource(R.string.staff_remove_salesperson))
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("credentials", text))
}
