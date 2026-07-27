package com.plantora.billing.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.plantora.billing.R
import com.plantora.billing.domain.PaymentMethod

/**
 * Localized label for a payment method. The [PaymentMethod.label] property is a
 * fixed English string used off the UI thread; on screen, prefer this so the
 * label follows the chosen app language.
 */
@Composable
fun paymentLabel(method: PaymentMethod): String = when (method) {
    PaymentMethod.CASH -> stringResource(R.string.pay_cash)
    PaymentMethod.UPI -> stringResource(R.string.pay_upi)
    PaymentMethod.SPLIT -> stringResource(R.string.pay_split)
    PaymentMethod.DUE -> stringResource(R.string.pay_due)
    PaymentMethod.NONE -> stringResource(R.string.pay_none)
}
