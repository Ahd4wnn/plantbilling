package com.plantora.billing.ui.sales

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.plantora.billing.R
import com.plantora.billing.domain.ExpenseCategory
import com.plantora.billing.ui.components.PlantoraTextField
import com.plantora.billing.ui.components.PrimaryButton
import com.plantora.billing.ui.theme.Dimens

/**
 * Full-screen expense entry (replaces the old bottom-sheet editor). A dedicated page
 * gives the category picker room and — crucially — avoids the fragile dropdown-inside-a-
 * bottom-sheet combo that broke the picker and the day summary. Everyone can record an
 * expense; only managers ([canManage]) get the "+ New category" affordance.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExpenseEditorScreen(
    editor: ExpenseEditor,
    categories: List<ExpenseCategory>,
    canManage: Boolean,
    onAmount: (String) -> Unit,
    onCategory: (String?) -> Unit,
    onAddCategory: (String) -> Unit,
    onNote: (String) -> Unit,
    onMethod: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (editor.id != null) stringResource(R.string.expense_edit_title)
                        else stringResource(R.string.expense_record_title),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = Dimens.lg)
                .padding(bottom = Dimens.xl),
        ) {
            Spacer(Modifier.height(Dimens.md))
            PlantoraTextField(editor.amount, onAmount, label = stringResource(R.string.label_amount_rupees), keyboardType = KeyboardType.Decimal)
            Spacer(Modifier.height(Dimens.lg))

            // ── Category picker: plain wrapping chips (no popup) ──
            Text(stringResource(R.string.label_category), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Dimens.xs))
            if (categories.isEmpty() && !canManage) {
                Text(
                    stringResource(R.string.expense_no_categories),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = editor.categoryId == cat.id,
                        onClick = { onCategory(cat.id) },
                        label = { Text(cat.name) },
                    )
                }
                if (canManage) {
                    FilterChip(
                        selected = adding,
                        onClick = { adding = !adding },
                        label = { Text("+ " + stringResource(R.string.expense_add_category)) },
                    )
                }
            }
            if (canManage && adding) {
                // Stack the field and button vertically. They must NOT share a Row:
                // both PlantoraTextField and PrimaryButton force fillMaxWidth(), so a
                // side-by-side Row collapses the field to zero width.
                Spacer(Modifier.height(Dimens.sm))
                PlantoraTextField(newName, { newName = it }, label = stringResource(R.string.expense_new_category_hint))
                Spacer(Modifier.height(Dimens.sm))
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

            Spacer(Modifier.height(Dimens.lg))
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
}
