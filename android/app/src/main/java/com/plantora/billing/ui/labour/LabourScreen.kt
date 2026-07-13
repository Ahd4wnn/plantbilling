package com.plantora.billing.ui.labour

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import com.plantora.billing.domain.Attendance
import com.plantora.billing.domain.Labourer
import com.plantora.billing.domain.LabourPayment
import com.plantora.billing.domain.Money
import com.plantora.billing.domain.formatBillTime
import com.plantora.billing.ui.components.ErrorState
import com.plantora.billing.ui.components.LoadingState
import com.plantora.billing.ui.components.MoneyText
import com.plantora.billing.ui.components.PlantoraCard
import com.plantora.billing.ui.components.PlantoraTextField
import com.plantora.billing.ui.components.PrimaryButton
import com.plantora.billing.ui.components.SecondaryButton
import com.plantora.billing.ui.theme.Dimens

/** Absolute value, for showing a negative balance ("paid ahead") as a positive amount. */
private fun Money.abs(): Money = Money(amount.abs())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabourScreen(
    onBack: () -> Unit,
    viewModel: LabourViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); viewModel.dismissMessage() } }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Labour") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        when {
            ui.loading -> LoadingState(Modifier.padding(padding))
            ui.error != null -> ErrorState(ui.error!!, onRetry = viewModel::load, icon = Icons.Rounded.Payments, modifier = Modifier.padding(padding))
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding).imePadding(),
                contentPadding = PaddingValues(Dimens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.md),
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                        PrimaryButton(text = "Record payment", onClick = { viewModel.openRecordPayment() }, modifier = Modifier.weight(1f))
                        SecondaryButton(text = "Attendance", onClick = viewModel::openAttendance, leadingIcon = Icons.Rounded.CalendarMonth, modifier = Modifier.weight(1f))
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Workers", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        SecondaryButton(text = "Add", onClick = viewModel::openAddWorker, leadingIcon = Icons.Rounded.Add)
                    }
                }
                item {
                    OutlinedTextField(
                        value = ui.query, onValueChange = viewModel::setQuery,
                        placeholder = { Text("Search workers") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (ui.labourers.isEmpty()) {
                    item { Text("No workers yet. Tap Add to set up your first worker.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(ui.filteredLabourers, key = { it.id }) { l ->
                    WorkerRow(l, canManage = ui.isManager, onOpen = { viewModel.openDetail(l) }, onEdit = { viewModel.openEditWorker(l) }, onDelete = { viewModel.deleteWorker(l.id) })
                }

                item { Text("Recent payments", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Dimens.sm)) }
                if (ui.payments.isEmpty()) {
                    item { Text("No payments recorded yet.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(ui.payments, key = { it.id }) { p ->
                    PaymentRow(p, canManage = ui.isManager, onEdit = { viewModel.openEditPayment(p) }, onDelete = { viewModel.deletePayment(p.id) })
                }
            }
        }
    }

    ui.workerEditor?.let { editor ->
        ModalBottomSheet(onDismissRequest = viewModel::closeWorker, sheetState = sheet) {
            WorkerEditorSheet(editor, viewModel::setWorkerName, viewModel::setWorkerPhone, viewModel::setWorkerAadhaar, viewModel::setWorkerGender, viewModel::setWorkerWage, viewModel::saveWorker)
        }
    }
    ui.paymentEditor?.let { editor ->
        ModalBottomSheet(onDismissRequest = viewModel::closePayment, sheetState = sheet) {
            PaymentSheet(editor, ui.labourers, viewModel::selectPaymentLabourer, viewModel::setPaymentAdvance, viewModel::setPaymentDays, viewModel::setPaymentAmount, viewModel::setPaymentMode, viewModel::setPaymentSplitCash, viewModel::setPaymentNote, viewModel::savePayment)
        }
    }
    ui.detail?.let { detail ->
        ModalBottomSheet(onDismissRequest = viewModel::closeDetail, sheetState = sheet) {
            WorkerDetailSheet(
                detail,
                onRecord = { viewModel.closeDetail(); viewModel.openRecordPayment(detail.labourer, advance = false) },
                onAdvance = { viewModel.closeDetail(); viewModel.openRecordPayment(detail.labourer, advance = true) },
            )
        }
    }
    if (ui.showAttendance) {
        ModalBottomSheet(onDismissRequest = viewModel::closeAttendance, sheetState = sheet) {
            AttendanceSheet(ui.labourers, ui.attendance, ui.attendanceBusyId, viewModel::mark)
        }
    }
}

@Composable
private fun WorkerRow(l: Labourer, canManage: Boolean, onOpen: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    PlantoraCard(modifier = Modifier.clickable(onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(l.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${l.gender.replaceFirstChar { it.uppercase() }} • ${l.defaultWage.format()}/day" + (l.phone?.let { " • $it" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when {
                    l.balanceToPay.isPositive() -> Text("Balance to pay ${l.balanceToPay.format()}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                    l.balanceToPay.isNegative() -> Text("Paid ahead ${l.balanceToPay.abs().format()}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (canManage) {
                IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, contentDescription = "Edit worker") }
                IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove worker") }
            }
        }
    }
}

@Composable
private fun PaymentRow(p: LabourPayment, canManage: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    PlantoraCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val tag = when (p.kind) { "advance" -> " • advance"; "due_clear" -> " • due cleared"; else -> "" }
                Text(p.labourerName + tag, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val daysPart = if (p.kind == "wage" && p.days != null) " • ${p.days} day(s)" else ""
                Text("${formatBillTime(p.createdAt)} • ${p.paymentMethod.label}$daysPart", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                MoneyText(p.totalAmount, style = MaterialTheme.typography.titleMedium)
                if (canManage) Row {
                    IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, contentDescription = "Edit payment") }
                    IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete payment") }
                }
            }
        }
    }
}

@Composable
private fun WorkerEditorSheet(editor: WorkerEditor, onName: (String) -> Unit, onPhone: (String) -> Unit, onAadhaar: (String) -> Unit, onGender: (String) -> Unit, onWage: (String) -> Unit, onSave: () -> Unit) {
    Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.lg).padding(bottom = Dimens.xl)) {
        Text(if (editor.id != null) "Edit worker" else "Add worker", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(Dimens.lg))
        PlantoraTextField(editor.name, onName, label = "Name")
        Spacer(Modifier.height(Dimens.md))
        PlantoraTextField(editor.phone, onPhone, label = "Phone number", keyboardType = KeyboardType.Phone)
        Spacer(Modifier.height(Dimens.md))
        PlantoraTextField(editor.aadhaar, onAadhaar, label = "Aadhaar number (optional)", keyboardType = KeyboardType.Number)
        Spacer(Modifier.height(Dimens.md))
        GenderToggle(editor.gender, onGender)
        Spacer(Modifier.height(Dimens.md))
        PlantoraTextField(editor.wage, onWage, label = "Wage per day (₹)", keyboardType = KeyboardType.Decimal)
        editor.error?.let { Spacer(Modifier.height(Dimens.md)); Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(Dimens.xl))
        PrimaryButton(text = if (editor.id != null) "Save changes" else "Add worker", onClick = onSave, enabled = editor.canSave, loading = editor.saving, modifier = Modifier.fillMaxWidth())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenderToggle(gender: String, onGender: (String) -> Unit) {
    Text("Gender", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(Dimens.xs))
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SegmentedButton(selected = gender == "male", onClick = { onGender("male") }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Male") }
        SegmentedButton(selected = gender == "female", onClick = { onGender("female") }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Female") }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PaymentSheet(
    editor: PaymentEditor, labourers: List<Labourer>,
    onSelect: (Labourer) -> Unit, onAdvance: (Boolean) -> Unit, onDays: (String) -> Unit, onAmount: (String) -> Unit,
    onMode: (LabourPayMode) -> Unit, onSplitCash: (String) -> Unit, onNote: (String) -> Unit, onSave: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.lg).padding(bottom = Dimens.xl)) {
        Text(if (editor.id != null) "Edit payment" else if (editor.isAdvance) "Give advance" else "Record payment", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(Dimens.md))
        if (editor.id != null) {
            Text(editor.labourerName, style = MaterialTheme.typography.titleMedium)
        } else {
            Text("Worker", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Dimens.xs))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                labourers.forEach { l -> FilterChip(selected = editor.labourerId == l.id, onClick = { onSelect(l) }, label = { Text(l.name) }) }
            }
        }

        // Wage payment vs advance
        if (editor.id == null) {
            Spacer(Modifier.height(Dimens.md))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = !editor.isAdvance, onClick = { onAdvance(false) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Wage payment") }
                SegmentedButton(selected = editor.isAdvance, onClick = { onAdvance(true) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Advance") }
            }
        }

        if (!editor.isAdvance) {
            Spacer(Modifier.height(Dimens.md))
            Text("Wage per day: ${editor.wagePerDay.format()}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Dimens.xs))
            PlantoraTextField(editor.days, onDays, label = "Number of days", keyboardType = KeyboardType.Decimal)
        }
        Spacer(Modifier.height(Dimens.md))
        PlantoraTextField(editor.amount, onAmount, label = if (editor.isAdvance) "Advance amount (₹)" else "Amount (₹)", keyboardType = KeyboardType.Decimal)

        Spacer(Modifier.height(Dimens.md))
        Text("Payment method", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Dimens.xs))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val modes = LabourPayMode.entries
            modes.forEachIndexed { i, m ->
                SegmentedButton(selected = editor.mode == m, onClick = { onMode(m) }, shape = SegmentedButtonDefaults.itemShape(i, modes.size)) {
                    Text(when (m) { LabourPayMode.CASH -> "Cash"; LabourPayMode.UPI -> "UPI"; LabourPayMode.SPLIT -> "Split" })
                }
            }
        }
        if (editor.mode == LabourPayMode.SPLIT) {
            Spacer(Modifier.height(Dimens.md))
            PlantoraTextField(editor.splitCash, onSplitCash, label = "Cash part (₹)", keyboardType = KeyboardType.Decimal)
            Spacer(Modifier.height(Dimens.xs))
            Text("UPI part: ${editor.upi.format()}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(Dimens.md))
        PlantoraTextField(editor.note, onNote, label = "Note (optional)")
        Spacer(Modifier.height(Dimens.md))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Total", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            MoneyText(editor.total, style = MaterialTheme.typography.headlineSmall)
        }
        editor.error?.let { Spacer(Modifier.height(Dimens.md)); Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(Dimens.xl))
        PrimaryButton(text = if (editor.id != null) "Save changes" else if (editor.isAdvance) "Give advance" else "Record payment", onClick = onSave, enabled = editor.canSave, loading = editor.saving, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun WorkerDetailSheet(detail: WorkerDetail, onRecord: () -> Unit, onAdvance: () -> Unit) {
    val l = detail.labourer
    Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.lg).padding(bottom = Dimens.xl)) {
        Text(l.name, style = MaterialTheme.typography.headlineMedium)
        Text("${l.gender.replaceFirstChar { it.uppercase() }}" + (l.phone?.let { " • $it" } ?: "") + (l.aadhaar?.let { " • Aadhaar $it" } ?: ""), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Dimens.md))

        // Statement of pay
        StatementRow("Days worked", "${l.daysWorked} day(s)")
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        StatementRow("Earned (${l.defaultWage.format()}/day)", l.earned.format())
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        StatementRow("Total paid", l.totalPaid.format())
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        Row(Modifier.fillMaxWidth().padding(vertical = Dimens.sm), verticalAlignment = Alignment.CenterVertically) {
            Text(if (l.balanceToPay.isNegative()) "Paid ahead" else "Balance to pay", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            MoneyText(
                l.balanceToPay.abs(),
                style = MaterialTheme.typography.titleLarge,
                color = when {
                    l.balanceToPay.isPositive() -> MaterialTheme.colorScheme.error
                    l.balanceToPay.isNegative() -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }

        Spacer(Modifier.height(Dimens.md))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
            SecondaryButton(text = "Give advance", onClick = onAdvance, modifier = Modifier.weight(1f))
            PrimaryButton(text = "Record payment", onClick = onRecord, modifier = Modifier.weight(1f))
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
private fun StatementRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = Dimens.sm), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AttendanceSheet(labourers: List<Labourer>, attendance: Map<String, Attendance>, busyId: String?, onMark: (String, String) -> Unit) {
    Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.lg).padding(bottom = Dimens.xl)) {
        Text("Today's attendance", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(Dimens.md))
        if (labourers.isEmpty()) {
            Text("Add a worker first.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        labourers.forEach { l ->
            val rec = attendance[l.id]
            Column(Modifier.fillMaxWidth().padding(vertical = Dimens.sm)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(l.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("${l.daysWorked} day(s) worked", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(Dimens.xs))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                    AttChip("Present", rec?.status == "present", busyId == l.id) { onMark(l.id, "present") }
                    AttChip("Half-day", rec?.status == "half_day", busyId == l.id) { onMark(l.id, "half_day") }
                    AttChip("Absent", rec?.status == "absent", busyId == l.id) { onMark(l.id, "absent") }
                }
            }
        }
    }
}

@Composable
private fun AttChip(label: String, selected: Boolean, busy: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, enabled = !busy, onClick = onClick, label = { Text(label) })
}
