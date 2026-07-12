package com.plantora.billing.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Engineering
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Store
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plantora.billing.domain.Role
import com.plantora.billing.domain.User
import com.plantora.billing.ui.components.PlantoraCard
import com.plantora.billing.ui.components.PlantoraTextField
import com.plantora.billing.ui.components.SecondaryButton
import com.plantora.billing.ui.theme.Dimens

@Composable
fun MoreScreen(
    user: User,
    onOpenShop: () -> Unit,
    onOpenPrinter: () -> Unit,
    onOpenStaff: () -> Unit,
    onOpenLabour: () -> Unit,
    onLogout: () -> Unit,
    viewModel: MoreViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val supportNumber = "7306803557"
    val cumulative by viewModel.cumulative.collectAsStateWithLifecycle()
    val cashSet by viewModel.cashSet.collectAsStateWithLifecycle()

    // Confirmation toast after the manager sets the running cash in hand.
    LaunchedEffect(cashSet.message) {
        cashSet.message?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.dismissMessage()
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.md),
    ) {
        PlantoraCard {
            Text(user.displayShop, style = MaterialTheme.typography.titleLarge)
            Text(user.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Role: ${user.role.name.lowercase().replace('_', ' ')}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        MenuRow(Icons.Rounded.Store, "Shop details", "Name, address, UPI — shown on receipts", onOpenShop)
        if (user.role == Role.MANAGER) {
            MenuRow(Icons.Rounded.Groups, "Salespeople", "Add and manage counter staff", onOpenStaff)
        }
        MenuRow(
            Icons.Rounded.Engineering,
            "Labour",
            if (user.role == Role.MANAGER) "Manage workers and record payments" else "Record payments to workers",
            onOpenLabour,
        )

        // Cash-in-hand controls. The switch is a per-device display choice; the
        // "Set" row (manager only) changes the shared running total for the shop.
        SwitchRow(
            icon = Icons.Rounded.AccountBalanceWallet,
            title = "Running cash in hand",
            subtitle = "Carry the drawer total over to the next day",
            checked = cumulative,
            onCheckedChange = viewModel::setCumulative,
        )
        if (user.role == Role.MANAGER) {
            MenuRow(
                Icons.Rounded.AccountBalanceWallet,
                "Set cash in hand",
                "Correct the running total to the cash you counted",
                onClick = viewModel::openCashSet,
            )
        }
        // The printer lives on the billing device, so only the salesperson sets it up.
        if (user.role == com.plantora.billing.domain.Role.SALESPERSON) {
            MenuRow(Icons.Rounded.Print, "Printer", "Connect a Bluetooth thermal printer", onOpenPrinter)
        }

        MenuRow(
            Icons.Rounded.SupportAgent,
            "Contact support",
            "Call us at $supportNumber",
            onClick = {
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_DIAL,
                    android.net.Uri.parse("tel:$supportNumber"),
                )
                context.startActivity(intent)
            },
        )

        SecondaryButton(
            text = "Log out",
            onClick = onLogout,
            leadingIcon = Icons.AutoMirrored.Rounded.Logout,
        )
    }

    if (cashSet.open) {
        AlertDialog(
            onDismissRequest = viewModel::closeCashSet,
            title = { Text("Set cash in hand") },
            text = {
                Column {
                    Text(
                        "Enter the cash you actually have on hand right now. The running total will start from this and keep adding each day.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.size(Dimens.md))
                    PlantoraTextField(
                        value = cashSet.amount,
                        onValueChange = viewModel::setCashAmount,
                        label = "Cash in hand (₹)",
                        keyboardType = KeyboardType.Decimal,
                        errorText = cashSet.error,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmCashSet, enabled = cashSet.canSave) {
                    Text(if (cashSet.saving) "Saving…" else "Save")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::closeCashSet) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    PlantoraCard(modifier = Modifier.clickable { onCheckedChange(!checked) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f).padding(horizontal = Dimens.md)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    PlantoraCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f).padding(horizontal = Dimens.md)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}
