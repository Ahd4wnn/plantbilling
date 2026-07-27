package com.plantora.billing.ui.billing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.plantora.billing.R
import com.plantora.billing.ui.components.PlantoraTextField
import com.plantora.billing.ui.components.PrimaryButton
import com.plantora.billing.ui.components.QuantityStepper
import com.plantora.billing.ui.theme.Dimens

@Composable
fun QuickAddSheet(
    state: QuickAddState,
    onName: (String) -> Unit,
    onPrice: (String) -> Unit,
    onQuantity: (Int) -> Unit,
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
        Text(stringResource(R.string.quickadd_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.quickadd_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Dimens.lg))
        PlantoraTextField(state.name, onName, label = stringResource(R.string.quickadd_name_label), placeholder = stringResource(R.string.quickadd_name_hint))
        Spacer(Modifier.height(Dimens.md))
        PlantoraTextField(state.price, onPrice, label = stringResource(R.string.price_label), keyboardType = KeyboardType.Decimal)
        Spacer(Modifier.height(Dimens.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.quantity_label), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            QuantityStepper(
                quantity = state.quantity,
                onDecrement = { onQuantity(state.quantity - 1) },
                onIncrement = { onQuantity(state.quantity + 1) },
                onQuantityChange = { q -> onQuantity(q) },
            )
        }
        state.error?.let {
            Spacer(Modifier.height(Dimens.md))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(Dimens.xl))
        PrimaryButton(text = stringResource(R.string.quickadd_add), onClick = onSave, enabled = state.canSave, loading = state.saving)
    }
}
