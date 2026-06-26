package com.plantora.billing.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.plantora.billing.ui.theme.Dimens

/** Large +/- quantity stepper with 48dp targets for older thumbs. */
@Composable
fun QuantityStepper(
    quantity: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    minValue: Int = 1,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
    ) {
        FilledTonalIconButton(
            onClick = onDecrement,
            enabled = quantity > minValue,
            modifier = Modifier.size(Dimens.minTouch),
        ) {
            Icon(Icons.Rounded.Remove, contentDescription = "Decrease quantity")
        }
        Text(
            text = quantity.toString(),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 36.dp),
        )
        FilledTonalIconButton(
            onClick = onIncrement,
            modifier = Modifier.size(Dimens.minTouch),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "Increase quantity")
        }
    }
}
