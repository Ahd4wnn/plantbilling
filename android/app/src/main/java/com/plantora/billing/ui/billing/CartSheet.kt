package com.plantora.billing.ui.billing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.plantora.billing.R
import com.plantora.billing.i18n.asString
import com.plantora.billing.domain.DiscountType
import com.plantora.billing.domain.Money
import com.plantora.billing.ui.components.MoneyText
import com.plantora.billing.ui.components.PlantoraTextField
import com.plantora.billing.ui.components.PrimaryButton
import com.plantora.billing.ui.components.QuantityStepper
import com.plantora.billing.ui.components.SectionHeader
import com.plantora.billing.ui.theme.Dimens

@Composable
fun CartSheetContent(
    state: BillingUiState,
    onSetQuantity: (String, Int) -> Unit,
    onSetUnitPrice: (String, String) -> Unit,
    onRemoveLine: (String) -> Unit,
    onSetDiscountType: (DiscountType) -> Unit,
    onSetDiscountInput: (String) -> Unit,
    onSetPaymentMode: (PaymentMode) -> Unit,
    onSetCashInput: (String) -> Unit,
    onSetDueInput: (String) -> Unit,
    onSetCustomerName: (String) -> Unit,
    onSetCustomerPhone: (String) -> Unit,
    onSetRemarks: (String) -> Unit,
    onClose: () -> Unit,
    onClearCart: () -> Unit,
    onAddItem: () -> Unit,
    onHold: () -> Unit,
    onCheckout: () -> Unit,
) {
    val totals = state.totals
    val (cash, upi) = state.payment

    Column(
        Modifier
            .fillMaxWidth()
            // imePadding lifts the sheet above the keyboard so the field being typed
            // into (price, phone, remarks) is never hidden behind it.
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.lg)
            .padding(bottom = Dimens.xl),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.cart_review_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            androidx.compose.material3.TextButton(
                onClick = onClearCart,
                enabled = !state.isCartEmpty,
            ) {
                Icon(
                    Icons.Rounded.DeleteSweep,
                    contentDescription = null,
                    modifier = Modifier.padding(end = Dimens.xs).height(20.dp).width(20.dp),
                )
                Text(stringResource(R.string.cart_clear))
            }
            // Explicit close — the sheet no longer dismisses on swipe/scrim.
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cart_close_cd), modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.height(Dimens.md))

        // ── Cart lines ──
        // key(line.id): each line owns stateful editors (price NumberField, quantity
        // stepper). Without a stable key, removing a line would reassign those
        // remembered field states positionally to the wrong line.
        state.lines.forEach { line ->
            key(line.id) {
            Column(Modifier.padding(vertical = Dimens.sm)) {
                // Name + line total on one row so the total always has room (the
                // price field + stepper below would otherwise squeeze it to nothing).
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        line.product.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    MoneyText(
                        line.lineTotal,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = Dimens.sm),
                    )
                    IconButton(onClick = { onRemoveLine(line.id) }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cart_remove_cd, line.product.name))
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.md),
                ) {
                    com.plantora.billing.ui.components.NumberField(
                        value = line.unitPrice.toInput(),
                        onValueChange = { onSetUnitPrice(line.id, it) },
                        label = stringResource(R.string.cart_price),
                        modifier = Modifier.weight(1f),
                    )
                    QuantityStepper(
                        quantity = line.quantity,
                        onDecrement = { onSetQuantity(line.id, line.quantity - 1) },
                        onIncrement = { onSetQuantity(line.id, line.quantity + 1) },
                        onQuantityChange = { q -> onSetQuantity(line.id, q) },
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            }
        }

        Spacer(Modifier.height(Dimens.md))

        // Minimizes the review so the owner can tap more products; the sheet pops
        // back open automatically on the next add.
        com.plantora.billing.ui.components.SecondaryButton(
            text = stringResource(R.string.cart_add_item),
            onClick = onAddItem,
            leadingIcon = Icons.Rounded.Add,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(Dimens.lg))

        // ── Discount ──
        SectionHeader(stringResource(R.string.cart_discount))
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = state.discountType == DiscountType.FLAT,
                onClick = { onSetDiscountType(DiscountType.FLAT) },
                label = { Text(stringResource(R.string.discount_flat)) },
            )
            FilterChip(
                selected = state.discountType == DiscountType.PERCENT,
                onClick = { onSetDiscountType(DiscountType.PERCENT) },
                label = { Text(stringResource(R.string.discount_percent)) },
            )
            Spacer(Modifier.width(Dimens.sm))
            PlantoraTextField(
                value = state.discountInput,
                onValueChange = onSetDiscountInput,
                label = if (state.discountType == DiscountType.FLAT) stringResource(R.string.cart_amount) else stringResource(R.string.cart_percent),
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.width(130.dp),
            )
        }

        Spacer(Modifier.height(Dimens.lg))

        // ── Totals ──
        SummaryRow(stringResource(R.string.label_subtotal), totals.subtotal)
        if (totals.discountAmount.isPositive()) SummaryRow(stringResource(R.string.cart_discount), Money.ZERO - totals.discountAmount)
        Spacer(Modifier.height(Dimens.xs))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.label_total), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            MoneyText(totals.total, style = MaterialTheme.typography.titleLarge)
        }

        Spacer(Modifier.height(Dimens.lg))

        // ── Customer (entered fresh; phone required when there's a due) ──
        val hasDue = Money.parse(state.dueInput.ifBlank { "0" }).isPositive()
        SectionHeader(if (hasDue) stringResource(R.string.cart_customer_required) else stringResource(R.string.cart_customer_optional))
        PlantoraTextField(state.customerName, onSetCustomerName, label = stringResource(R.string.label_name))
        Spacer(Modifier.height(Dimens.sm))
        PlantoraTextField(
            state.customerPhone, onSetCustomerPhone,
            label = if (hasDue) stringResource(R.string.cart_phone_required) else stringResource(R.string.cart_phone_optional),
            keyboardType = KeyboardType.Phone,
        )

        Spacer(Modifier.height(Dimens.lg))

        // ── Payment ──
        SectionHeader(stringResource(R.string.cart_payment))
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
            PaymentChip(stringResource(R.string.pay_cash), state.paymentMode == PaymentMode.CASH) { onSetPaymentMode(PaymentMode.CASH) }
            PaymentChip(stringResource(R.string.pay_upi), state.paymentMode == PaymentMode.UPI) { onSetPaymentMode(PaymentMode.UPI) }
            PaymentChip(stringResource(R.string.pay_split), state.paymentMode == PaymentMode.SPLIT) { onSetPaymentMode(PaymentMode.SPLIT) }
        }
        if (state.paymentMode == PaymentMode.SPLIT) {
            Spacer(Modifier.height(Dimens.sm))
            PlantoraTextField(
                state.cashInput, onSetCashInput,
                label = stringResource(R.string.cart_cash_part), keyboardType = KeyboardType.Decimal,
            )
        }
        Spacer(Modifier.height(Dimens.sm))
        PlantoraTextField(
            state.dueInput, onSetDueInput,
            label = stringResource(R.string.cart_due), keyboardType = KeyboardType.Decimal,
        )
        Spacer(Modifier.height(Dimens.sm))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.lg)) {
            PayPill(stringResource(R.string.pay_cash), cash, Modifier.weight(1f))
            PayPill(stringResource(R.string.pay_upi), upi, Modifier.weight(1f))
        }

        // Scan-to-pay QR — appears whenever any amount is being collected via UPI.
        if (upi.isPositive()) {
            Spacer(Modifier.height(Dimens.lg))
            UpiQrSection(upiId = state.businessUpi, businessName = state.businessName, amount = upi)
        }

        state.checkoutError?.let {
            Spacer(Modifier.height(Dimens.md))
            Text(it.asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(Dimens.xl))
        // Park this bill to serve another customer first; it moves to "Held bills".
        com.plantora.billing.ui.components.SecondaryButton(
            text = stringResource(R.string.cart_hold),
            onClick = onHold,
            leadingIcon = Icons.Rounded.Pause,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Dimens.md))
        PrimaryButton(
            text = stringResource(R.string.cart_save, totals.total.format()),
            onClick = onCheckout,
            enabled = !state.isCartEmpty,
            loading = state.checkout == CheckoutPhase.SUBMITTING,
        )

        // Remarks last (rarely used).
        Spacer(Modifier.height(Dimens.md))
        PlantoraTextField(state.remarks, onSetRemarks, label = stringResource(R.string.cart_remarks), singleLine = false)
    }
}

@Composable
private fun UpiQrSection(upiId: String?, businessName: String, amount: Money) {
    androidx.compose.material3.Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(Dimens.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (upiId.isNullOrBlank()) {
                Text(
                    stringResource(R.string.upi_not_set),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(Dimens.xs))
                Text(
                    stringResource(R.string.upi_not_set_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            } else {
                Text(
                    stringResource(R.string.upi_scan),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(Dimens.md))
                androidx.compose.material3.Surface(
                    color = androidx.compose.ui.graphics.Color.White,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    com.plantora.billing.ui.components.UpiQrCode(
                        content = com.plantora.billing.ui.components.buildUpiUri(upiId, businessName, amount.toWire()),
                        modifier = Modifier.padding(Dimens.sm).size(220.dp),
                    )
                }
                Spacer(Modifier.height(Dimens.md))
                MoneyText(amount, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(Dimens.xs))
                Text(
                    upiId,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, money: Money) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoneyText(money, style = MaterialTheme.typography.bodyLarge, emphasize = false)
    }
}

@Composable
private fun PaymentChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun PayPill(label: String, money: Money, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoneyText(money, style = MaterialTheme.typography.titleMedium)
    }
}
