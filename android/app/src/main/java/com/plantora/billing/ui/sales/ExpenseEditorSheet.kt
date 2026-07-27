package com.plantora.billing.ui.sales

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.plantora.billing.R
import com.plantora.billing.ui.components.PlantoraTextField
import com.plantora.billing.ui.components.PrimaryButton
import com.plantora.billing.ui.theme.Dimens

// Value stored in the entry stays English; the chip shows a localized label.
private val QUICK_REASONS = listOf(
    "Tea & Snacks" to R.string.expense_reason_tea,
    "Soil & Pots" to R.string.expense_reason_soil,
    "Electricity" to R.string.expense_reason_electricity,
    "Labor Wages" to R.string.expense_reason_labor,
    "Transport" to R.string.expense_reason_transport,
    "Others" to R.string.expense_reason_others,
)

@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ExpenseEditorSheet(
    editor: ExpenseEditor,
    onAmount: (String) -> Unit,
    onReason: (String) -> Unit,
    onMethod: (String) -> Unit,
    onSave: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.lg)
            .padding(bottom = Dimens.xl),
    ) {
        Text(
            if (editor.id != null) stringResource(R.string.expense_edit_title) else stringResource(R.string.expense_record_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(Dimens.lg))
        PlantoraTextField(editor.amount, onAmount, label = stringResource(R.string.label_amount_rupees), keyboardType = KeyboardType.Decimal)
        Spacer(Modifier.height(Dimens.md))
        PlantoraTextField(editor.reason, onReason, label = stringResource(R.string.label_reason))
        Spacer(Modifier.height(Dimens.sm))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
            QUICK_REASONS.forEach { (value, labelRes) ->
                FilterChip(selected = editor.reason == value, onClick = { onReason(value) }, label = { Text(stringResource(labelRes)) })
            }
        }
        Spacer(Modifier.height(Dimens.lg))
        // How the money left the shop: cash comes out of the drawer, UPI out of
        // the day's UPI takings. Drives the day's Cash in Hand.
        Text(stringResource(R.string.expense_paid_from), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Dimens.xs))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = editor.paymentMethod == "cash",
                onClick = { onMethod("cash") },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text(stringResource(R.string.pay_cash)) }
            SegmentedButton(
                selected = editor.paymentMethod == "upi",
                onClick = { onMethod("upi") },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text(stringResource(R.string.pay_upi)) }
        }
        editor.error?.let {
            Spacer(Modifier.height(Dimens.md))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(Dimens.xl))
        PrimaryButton(
            text = if (editor.id != null) stringResource(R.string.action_save_changes) else stringResource(R.string.expense_save),
            onClick = onSave,
            enabled = editor.canSave,
            loading = editor.saving,
        )
    }
}
