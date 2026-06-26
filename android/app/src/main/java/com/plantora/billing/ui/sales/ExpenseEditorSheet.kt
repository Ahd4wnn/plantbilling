package com.plantora.billing.ui.sales

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.plantora.billing.ui.components.PlantoraTextField
import com.plantora.billing.ui.components.PrimaryButton
import com.plantora.billing.ui.theme.Dimens

private val QUICK_REASONS = listOf("Tea & Snacks", "Soil & Pots", "Electricity", "Labor Wages", "Transport", "Others")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpenseEditorSheet(
    editor: ExpenseEditor,
    onAmount: (String) -> Unit,
    onReason: (String) -> Unit,
    onSave: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.lg)
            .padding(bottom = Dimens.xl),
    ) {
        Text(
            if (editor.id != null) "Edit expense" else "Record expense",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(Dimens.lg))
        PlantoraTextField(editor.amount, onAmount, label = "Amount (₹)", keyboardType = KeyboardType.Decimal)
        Spacer(Modifier.height(Dimens.md))
        PlantoraTextField(editor.reason, onReason, label = "Reason")
        Spacer(Modifier.height(Dimens.sm))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
            QUICK_REASONS.forEach { r ->
                FilterChip(selected = editor.reason == r, onClick = { onReason(r) }, label = { Text(r) })
            }
        }
        editor.error?.let {
            Spacer(Modifier.height(Dimens.md))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(Dimens.xl))
        PrimaryButton(
            text = if (editor.id != null) "Save changes" else "Save expense",
            onClick = onSave,
            enabled = editor.canSave,
            loading = editor.saving,
        )
    }
}
