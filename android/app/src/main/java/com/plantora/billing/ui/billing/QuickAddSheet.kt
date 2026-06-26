package com.plantora.billing.ui.billing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
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
            .padding(horizontal = Dimens.lg)
            .padding(bottom = Dimens.xl),
    ) {
        Text("Quick add custom item", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Adds a one-off item to this bill (saved under “Quick Add”).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Dimens.lg))
        PlantoraTextField(state.name, onName, label = "Item name", placeholder = "e.g. Ad-hoc plant, pot, soil")
        Spacer(Modifier.height(Dimens.md))
        PlantoraTextField(state.price, onPrice, label = "Price (₹)", keyboardType = KeyboardType.Decimal)
        Spacer(Modifier.height(Dimens.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Quantity", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
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
        PrimaryButton(text = "Add to bill", onClick = onSave, enabled = state.canSave, loading = state.saving)
    }
}
