package com.plantora.billing.ui.labour

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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.plantora.billing.domain.Labourer
import com.plantora.billing.domain.LabourPayment
import com.plantora.billing.domain.formatBillTime
import com.plantora.billing.ui.components.EmptyState
import com.plantora.billing.ui.components.ErrorState
import com.plantora.billing.ui.components.LoadingState
import com.plantora.billing.ui.components.MoneyText
import com.plantora.billing.ui.components.PlantoraCard
import com.plantora.billing.ui.components.PlantoraTextField
import com.plantora.billing.ui.components.PrimaryButton
import com.plantora.billing.ui.components.SecondaryButton
import com.plantora.billing.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabourScreen(
    onBack: () -> Unit,
    viewModel: LabourViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); viewModel.dismissMessage() } }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Labour") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        when {
            ui.loading -> LoadingState(Modifier.padding(padding))
            ui.error != null -> ErrorState(ui.error!!, onRetry = viewModel::load, icon = Icons.Rounded.Payments, modifier = Modifier.padding(padding))
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(Dimens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.md),
            ) {
                item {
                    PrimaryButton(
                        text = "Record a payment",
                        onClick = viewModel::openRecordPayment,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // ── Workers ──
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Workers", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (ui.isManager) {
                            SecondaryButton(text = "Add", onClick = viewModel::openAddWorker, leadingIcon = Icons.Rounded.Add)
                        }
                    }
                }
                if (ui.labourers.isEmpty()) {
                    item {
                        Text(
                            if (ui.isManager) "No workers yet. Tap Add to set up your first worker."
                            else "No workers have been set up yet. Ask your manager to add them.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(ui.labourers, key = { it.id }) { l ->
                    WorkerRow(
                        labourer = l,
                        canManage = ui.isManager,
                        onEdit = { viewModel.openEditWorker(l) },
                        onDelete = { viewModel.deleteWorker(l.id) },
                    )
                }

                // ── Payments ──
                item {
                    Text("Recent payments", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Dimens.sm))
                }
                if (ui.payments.isEmpty()) {
                    item {
                        Text(
                            "No payments recorded yet.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(ui.payments, key = { it.id }) { p ->
                    PaymentRow(
                        payment = p,
                        canManage = ui.isManager,
                        onEdit = { viewModel.openEditPayment(p) },
                        onDelete = { viewModel.deletePayment(p.id) },
                    )
                }
            }
        }
    }

    ui.workerEditor?.let { editor ->
        ModalBottomSheet(onDismissRequest = viewModel::closeWorker, sheetState = sheetState) {
            WorkerEditorSheet(
                editor = editor,
                onName = viewModel::setWorkerName,
                onPhone = viewModel::setWorkerPhone,
                onGender = viewModel::setWorkerGender,
                onWage = viewModel::setWorkerWage,
                onOtRate = viewModel::setWorkerOtRate,
                onSave = viewModel::saveWorker,
            )
        }
    }

    ui.paymentEditor?.let { editor ->
        ModalBottomSheet(onDismissRequest = viewModel::closePayment, sheetState = sheetState) {
            PaymentSheet(
                editor = editor,
                labourers = ui.labourers,
                onSelect = viewModel::selectPaymentLabourer,
                onWage = viewModel::setPaymentWage,
                onHours = viewModel::setPaymentHours,
                onNote = viewModel::setPaymentNote,
                onSave = viewModel::savePayment,
            )
        }
    }
}

@Composable
private fun WorkerRow(labourer: Labourer, canManage: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    PlantoraCard(modifier = if (canManage) Modifier.clickable(onClick = onEdit) else Modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(labourer.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${labourer.gender.replaceFirstChar { it.uppercase() }} • Wage ${labourer.defaultWage.format()} • OT ${labourer.overtimeRate.format()}/hr" +
                        (labourer.phone?.let { " • $it" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (canManage) {
                IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, contentDescription = "Edit worker") }
                IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove worker") }
            }
        }
    }
}

@Composable
private fun PaymentRow(payment: LabourPayment, canManage: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    PlantoraCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(payment.labourerName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val otPart = if (payment.overtimeAmount.isPositive())
                    " • OT ${payment.overtimeHours}h ${payment.overtimeAmount.format()}" else ""
                Text(
                    "${formatBillTime(payment.createdAt)} • wage ${payment.wageAmount.format()}$otPart",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                payment.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Column(horizontalAlignment = Alignment.End) {
                MoneyText(payment.totalAmount, style = MaterialTheme.typography.titleMedium)
                if (canManage) {
                    Row {
                        IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, contentDescription = "Edit payment") }
                        IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete payment") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkerEditorSheet(
    editor: WorkerEditor,
    onName: (String) -> Unit,
    onPhone: (String) -> Unit,
    onGender: (String) -> Unit,
    onWage: (String) -> Unit,
    onOtRate: (String) -> Unit,
    onSave: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Dimens.lg).padding(bottom = Dimens.xl),
    ) {
        Text(if (editor.id != null) "Edit worker" else "Add worker", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(Dimens.lg))
        PlantoraTextField(editor.name, onName, label = "Name")
        Spacer(Modifier.height(Dimens.md))
        PlantoraTextField(editor.phone, onPhone, label = "Phone (optional)", keyboardType = KeyboardType.Phone)
        Spacer(Modifier.height(Dimens.md))
        Text("Gender", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Dimens.xs))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = editor.gender == "male",
                onClick = { onGender("male") },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("Male") }
            SegmentedButton(
                selected = editor.gender == "female",
                onClick = { onGender("female") },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text("Female") }
        }
        Spacer(Modifier.height(Dimens.md))
        PlantoraTextField(editor.wage, onWage, label = "Default wage (₹)", keyboardType = KeyboardType.Decimal)
        Spacer(Modifier.height(Dimens.md))
        PlantoraTextField(editor.otRate, onOtRate, label = "Overtime rate per hour (₹)", keyboardType = KeyboardType.Decimal)
        editor.error?.let {
            Spacer(Modifier.height(Dimens.md))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(Dimens.xl))
        PrimaryButton(
            text = if (editor.id != null) "Save changes" else "Add worker",
            onClick = onSave,
            enabled = editor.canSave,
            loading = editor.saving,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PaymentSheet(
    editor: PaymentEditor,
    labourers: List<Labourer>,
    onSelect: (Labourer) -> Unit,
    onWage: (String) -> Unit,
    onHours: (String) -> Unit,
    onNote: (String) -> Unit,
    onSave: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Dimens.lg).padding(bottom = Dimens.xl),
    ) {
        Text(if (editor.id != null) "Edit payment" else "Record payment", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(Dimens.md))

        if (editor.id != null) {
            // Editing — the worker is fixed.
            Text(editor.labourerName, style = MaterialTheme.typography.titleMedium)
        } else {
            Text("Worker", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Dimens.xs))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                labourers.forEach { l ->
                    androidx.compose.material3.FilterChip(
                        selected = editor.labourerId == l.id,
                        onClick = { onSelect(l) },
                        label = { Text(l.name) },
                    )
                }
            }
        }

        Spacer(Modifier.height(Dimens.md))
        PlantoraTextField(editor.wage, onWage, label = "Wage paid (₹)", keyboardType = KeyboardType.Decimal)
        Spacer(Modifier.height(Dimens.md))
        PlantoraTextField(editor.otHours, onHours, label = "Overtime hours", keyboardType = KeyboardType.Decimal)
        if (editor.otRate.isPositive()) {
            Spacer(Modifier.height(Dimens.xs))
            Text(
                "Overtime: ${editor.overtimeAmount.format()} (at ${editor.otRate.format()}/hr)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Dimens.md))
        PlantoraTextField(editor.note, onNote, label = "Note (optional)")

        Spacer(Modifier.height(Dimens.md))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Total to pay", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            MoneyText(editor.total, style = MaterialTheme.typography.headlineSmall)
        }

        editor.error?.let {
            Spacer(Modifier.height(Dimens.md))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(Dimens.xl))
        PrimaryButton(
            text = if (editor.id != null) "Save changes" else "Record payment",
            onClick = onSave,
            enabled = editor.canSave,
            loading = editor.saving,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
