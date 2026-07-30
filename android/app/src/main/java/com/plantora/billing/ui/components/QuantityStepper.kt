package com.plantora.billing.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.plantora.billing.R
import com.plantora.billing.ui.theme.Dimens

/**
 * Large +/- quantity stepper with 48dp targets for older thumbs. When
 * [onQuantityChange] is supplied, the number in the middle becomes an editable
 * field so a big quantity (e.g. 100) can be typed instead of tapping + repeatedly.
 */
@Composable
fun QuantityStepper(
    quantity: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    minValue: Int = 1,
    onQuantityChange: ((Int) -> Unit)? = null,
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
            Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.cd_decrease_qty))
        }
        if (onQuantityChange != null) {
            // TextFieldValue so we can select-all on focus: tapping the field
            // highlights the whole number, so typing a new quantity REPLACES it
            // instead of prepending (e.g. "1" + "0" was becoming "10" unintended).
            var field by remember {
                mutableStateOf(TextFieldValue(quantity.toString(), TextRange(quantity.toString().length)))
            }
            // Re-sync when the value changes externally (the +/- buttons), but
            // don't clobber what the user is mid-typing.
            LaunchedEffect(quantity) {
                if (field.text.toIntOrNull() != quantity) {
                    val s = quantity.toString()
                    field = TextFieldValue(s, TextRange(s.length))
                }
            }
            OutlinedTextField(
                value = field,
                onValueChange = { input ->
                    // Allow up to 7 digits so bulk orders (e.g. 200000) can be entered.
                    // toIntOrNull guards against overflow beyond Int range.
                    val digits = input.text.filter { it.isDigit() }.take(7)
                    field = input.copy(text = digits, selection = TextRange(digits.length))
                    digits.toIntOrNull()?.let { if (it >= minValue) onQuantityChange(it) }
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .width(120.dp)
                    .onFocusChanged { focus ->
                        if (focus.isFocused) {
                            field = field.copy(selection = TextRange(0, field.text.length))
                        }
                    },
            )
        } else {
            Text(
                text = quantity.toString(),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 36.dp),
            )
        }
        FilledTonalIconButton(
            onClick = onIncrement,
            modifier = Modifier.size(Dimens.minTouch),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.cd_increase_qty))
        }
    }
}
