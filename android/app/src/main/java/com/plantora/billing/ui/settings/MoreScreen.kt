package com.plantora.billing.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.RequestQuote
import androidx.compose.material.icons.rounded.Store
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.plantora.billing.BuildConfig
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plantora.billing.R
import com.plantora.billing.domain.Role
import com.plantora.billing.domain.User
import com.plantora.billing.i18n.LocaleManager
import com.plantora.billing.i18n.findActivity
import com.plantora.billing.ui.components.PlantoraCard
import com.plantora.billing.ui.components.PlantoraTextField
import com.plantora.billing.ui.components.SecondaryButton
import com.plantora.billing.ui.theme.Dimens

/** A selectable app language: BCP-47 tag + its name in its own script. */
private data class LangOption(val tag: String, val nameRes: Int)

private val LANGUAGES = listOf(
    LangOption("en", R.string.lang_en),
    LangOption("ml", R.string.lang_ml),
    LangOption("hi", R.string.lang_hi),
    LangOption("ta", R.string.lang_ta),
    LangOption("kn", R.string.lang_kn),
)

@Composable
fun MoreScreen(
    user: User,
    onOpenShop: () -> Unit,
    onOpenPrinter: () -> Unit,
    onOpenStaff: () -> Unit,
    onOpenLabour: () -> Unit,
    onOpenBorrowings: () -> Unit,
    onLogout: () -> Unit,
    viewModel: MoreViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    // Shown to the user; the wa.me link needs the country code with no symbols.
    val supportNumber = "+91 7975402266"
    val supportWhatsApp = "917975402266"
    val cumulative by viewModel.cumulative.collectAsStateWithLifecycle()
    val cashSet by viewModel.cashSet.collectAsStateWithLifecycle()

    var showLanguage by remember { mutableStateOf(false) }
    val currentTag = LocaleManager.getLanguageTag(context).ifEmpty { "en" }
    val currentLangRes = LANGUAGES.firstOrNull { it.tag == currentTag }?.nameRes ?: R.string.lang_en

    // Confirmation toast after the manager sets the running cash in hand.
    LaunchedEffect(cashSet.message) {
        cashSet.message?.let {
            android.widget.Toast.makeText(context, it.resolve(context), android.widget.Toast.LENGTH_LONG).show()
            viewModel.dismissMessage()
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.md),
    ) {
        PlantoraCard {
            Text(user.displayShop, style = MaterialTheme.typography.titleLarge)
            Text(user.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(R.string.more_role, user.role.name.lowercase().replace('_', ' ')),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        MenuRow(
            Icons.Rounded.Language,
            stringResource(R.string.more_language),
            stringResource(currentLangRes),
            onClick = { showLanguage = true },
        )

        MenuRow(Icons.Rounded.Store, stringResource(R.string.more_shop_details), stringResource(R.string.more_shop_details_sub), onOpenShop)
        if (user.role == Role.MANAGER) {
            MenuRow(Icons.Rounded.Groups, stringResource(R.string.more_salespeople), stringResource(R.string.more_salespeople_sub), onOpenStaff)
        }
        MenuRow(
            Icons.Rounded.Engineering,
            stringResource(R.string.more_labour),
            if (user.role == Role.MANAGER) stringResource(R.string.more_labour_sub_manager) else stringResource(R.string.more_labour_sub_staff),
            onOpenLabour,
        )
        MenuRow(
            Icons.Rounded.RequestQuote,
            stringResource(R.string.more_borrowings),
            stringResource(R.string.more_borrowings_sub),
            onOpenBorrowings,
        )

        // Cash-in-hand controls. The switch is a per-device display choice; the
        // "Set" row (manager only) changes the shared running total for the shop.
        SwitchRow(
            icon = Icons.Rounded.AccountBalanceWallet,
            title = stringResource(R.string.more_running_cash),
            subtitle = stringResource(R.string.more_running_cash_sub),
            checked = cumulative,
            onCheckedChange = viewModel::setCumulative,
        )
        if (user.role == Role.MANAGER) {
            MenuRow(
                Icons.Rounded.AccountBalanceWallet,
                stringResource(R.string.more_set_cash),
                stringResource(R.string.more_set_cash_sub),
                onClick = viewModel::openCashSet,
            )
        }
        // The printer lives on the billing device, so only the salesperson sets it up.
        if (user.role == com.plantora.billing.domain.Role.SALESPERSON) {
            MenuRow(Icons.Rounded.Print, stringResource(R.string.more_printer), stringResource(R.string.more_printer_sub), onOpenPrinter)
        }

        MenuRow(
            Icons.Rounded.SupportAgent,
            stringResource(R.string.more_support),
            stringResource(R.string.more_support_sub, supportNumber),
            onClick = {
                // Open a WhatsApp chat with support. wa.me opens the WhatsApp app
                // when installed, and falls back to the browser otherwise.
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://wa.me/$supportWhatsApp"),
                )
                try {
                    context.startActivity(intent)
                } catch (e: android.content.ActivityNotFoundException) {
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.more_support_error, supportNumber),
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                }
            },
        )

        SecondaryButton(
            text = stringResource(R.string.action_logout),
            onClick = onLogout,
            leadingIcon = Icons.AutoMirrored.Rounded.Logout,
        )

        // App version — helps support identify a shop's exact build.
        Text(
            stringResource(R.string.more_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = Dimens.sm),
        )
    }

    if (showLanguage) {
        AlertDialog(
            onDismissRequest = { showLanguage = false },
            title = { Text(stringResource(R.string.language_dialog_title)) },
            text = {
                Column {
                    LANGUAGES.forEach { lang ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showLanguage = false
                                    if (lang.tag != currentTag) {
                                        LocaleManager.setLanguageTag(context, lang.tag)
                                        // Recreate so the whole UI redraws in the new language.
                                        context.findActivity()?.recreate()
                                    }
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = lang.tag == currentTag, onClick = null)
                            Text(
                                stringResource(lang.nameRes),
                                modifier = Modifier.padding(start = Dimens.md),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguage = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (cashSet.open) {
        AlertDialog(
            onDismissRequest = viewModel::closeCashSet,
            title = { Text(stringResource(R.string.more_set_cash)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.more_set_cash_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.size(Dimens.md))
                    PlantoraTextField(
                        value = cashSet.amount,
                        onValueChange = viewModel::setCashAmount,
                        label = stringResource(R.string.more_cash_label),
                        keyboardType = KeyboardType.Decimal,
                        errorText = cashSet.error?.resolve(context),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmCashSet, enabled = cashSet.canSave) {
                    Text(if (cashSet.saving) stringResource(R.string.action_saving) else stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::closeCashSet) { Text(stringResource(R.string.action_cancel)) }
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
