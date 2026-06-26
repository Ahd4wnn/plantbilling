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
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Store
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.plantora.billing.domain.User
import com.plantora.billing.ui.components.PlantoraCard
import com.plantora.billing.ui.components.SecondaryButton
import com.plantora.billing.ui.theme.Dimens

@Composable
fun MoreScreen(
    user: User,
    onOpenShop: () -> Unit,
    onOpenPrinter: () -> Unit,
    onOpenStaff: () -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val supportNumber = "7306803557"
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
        if (user.role == com.plantora.billing.domain.Role.MANAGER) {
            MenuRow(Icons.Rounded.Groups, "Salespeople", "Add and manage counter staff", onOpenStaff)
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
