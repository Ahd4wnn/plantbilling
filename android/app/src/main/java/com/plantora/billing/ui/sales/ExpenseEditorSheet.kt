package com.plantora.billing.ui.sales

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.plantora.billing.R
import com.plantora.billing.domain.ExpenseCategory
import com.plantora.billing.ui.components.PlantoraTextField
import com.plantora.billing.ui.components.PrimaryButton
import com.plantora.billing.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseEditorSheet(
    editor: ExpenseEditor,
    categories: List<ExpenseCategory>,
    canManage: Boolean,
    onAmount: (String) -> Unit,
    onCategory: (String?) -> Unit,
    onAddCategory: (String) -> Unit,
    onNote: (String) -> Unit,
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

        CategorySelector(
            categories = categories,
            selectedId = editor.categoryId,
            canManage = canManage,
            onSelect = onCategory,
            onAddCategory = onAddCategory,
        )

        Spacer(Modifier.height(Dimens.md))
        PlantoraTextField(editor.note, onNote, label = stringResource(R.string.label_remark_optional))

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategorySelector(
    categories: List<ExpenseCategory>,
    selectedId: String?,
    canManage: Boolean,
    onSelect: (String?) -> Unit,
    onAddCategory: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    val selectedName = categories.find { it.id == selectedId }?.name ?: ""

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.label_category)) },
            placeholder = { Text(stringResource(R.string.expense_select_category)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            categories.forEach { cat ->
                DropdownMenuItem(
                    text = { Text(cat.name) },
                    onClick = {
                        onSelect(cat.id)
                        expanded = false
                    },
                )
            }
        }
    }

    if (categories.isEmpty() && !canManage) {
        Spacer(Modifier.height(Dimens.xs))
        Text(
            stringResource(R.string.expense_no_categories),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (canManage) {
        Spacer(Modifier.height(Dimens.sm))
        if (!adding) {
            OutlinedButton(onClick = { adding = true }) {
                Text("+ " + stringResource(R.string.expense_add_category))
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    PlantoraTextField(newName, { newName = it }, label = stringResource(R.string.expense_new_category_hint))
                }
                Spacer(Modifier.width(Dimens.sm))
                PrimaryButton(
                    text = stringResource(R.string.action_add),
                    onClick = {
                        onAddCategory(newName)
                        newName = ""
                        adding = false
                    },
                    enabled = newName.isNotBlank(),
                )
            }
        }
    }
}
