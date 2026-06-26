package com.plantora.billing.ui.billing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.plantora.billing.domain.Bill
import com.plantora.billing.ui.components.MoneyText
import com.plantora.billing.ui.components.PrimaryButton
import com.plantora.billing.ui.components.SecondaryButton
import com.plantora.billing.ui.theme.Dimens

@Composable
fun SuccessView(
    bill: Bill,
    printPhase: PrintPhase,
    printMessage: String?,
    onPrint: () -> Unit,
    onNewBill: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(Dimens.lg))
        Text(
            if (bill.idempotentReplay) "Bill already saved" else "Bill saved",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Dimens.sm))
        MoneyText(bill.total, style = MaterialTheme.typography.displaySmall)
        bill.customerName?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(Dimens.xxl))

        PrimaryButton(
            text = if (printPhase == PrintPhase.DONE) "Print again" else "Print receipt",
            onClick = onPrint,
            loading = printPhase == PrintPhase.PRINTING || printPhase == PrintPhase.CONNECTING,
            leadingIcon = Icons.Rounded.Print,
        )
        printMessage?.let {
            Spacer(Modifier.height(Dimens.sm))
            Text(
                it,
                color = if (printPhase == PrintPhase.FAILED) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(Dimens.md))
        SecondaryButton(text = "New bill", onClick = onNewBill, modifier = Modifier.fillMaxWidth())
    }
}
