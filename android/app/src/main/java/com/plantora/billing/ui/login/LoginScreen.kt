package com.plantora.billing.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plantora.billing.R
import com.plantora.billing.data.local.SavedAccount
import com.plantora.billing.domain.Role
import com.plantora.billing.ui.components.PlantoraCard
import com.plantora.billing.ui.components.PlantoraTextField
import com.plantora.billing.ui.components.PrimaryButton
import com.plantora.billing.ui.components.SecondaryButton
import com.plantora.billing.ui.theme.BrandPrimary
import com.plantora.billing.ui.theme.Dimens

@Composable
fun LoginScreen(viewModel: LoginViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val loginCtx = androidx.compose.ui.platform.LocalContext.current

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = Dimens.xl),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Use the launcher FOREGROUND (a PNG). The adaptive icon mipmaps resolve to
            // an XML adaptive-icon on Android 8+, which Compose's painterResource cannot
            // render (it throws for non-raster assets), crashing on launch. The white
            // mark sits on the brand-coloured tile so it reads on the light background.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(BrandPrimary),
            ) {
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                    contentDescription = "PlantBill",
                    modifier = Modifier.size(132.dp),
                )
            }
            Spacer(Modifier.height(Dimens.lg))
            Text(
                "PlantBill",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                stringResource(R.string.login_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(Dimens.xxl))

            if (ui.formVisible) {
                LoginForm(ui = ui, viewModel = viewModel)
            } else {
                AccountPicker(ui = ui, viewModel = viewModel)
            }

            Spacer(Modifier.height(Dimens.xl))
            Text(
                stringResource(R.string.login_footer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            // "Interested in the product?" contact block — for prospective shops who land
            // on the login screen (there is no public sign-up). Phone dials; email composes.
            Spacer(Modifier.height(Dimens.xl))
            Text(
                stringResource(R.string.login_interested),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Dimens.sm))
            Text(
                "+91 7975402266",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.clickable {
                    runCatching {
                        loginCtx.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_DIAL,
                                android.net.Uri.parse("tel:+917975402266"),
                            ),
                        )
                    }
                },
            )
            Spacer(Modifier.height(Dimens.sm))
            Text(
                "support@dofida.in",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.clickable {
                    runCatching {
                        loginCtx.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_SENDTO,
                                android.net.Uri.parse("mailto:support@dofida.in"),
                            ),
                        )
                    }
                },
            )
            Spacer(Modifier.height(Dimens.xl))
        }
    }
}

@Composable
private fun AccountPicker(ui: LoginUiState, viewModel: LoginViewModel) {
    Text(
        stringResource(R.string.login_saved_title),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(Dimens.md))
    ui.savedAccounts.forEach { account ->
        AccountRow(
            account = account,
            loading = ui.switchingEmail == account.email,
            enabled = ui.switchingEmail == null,
            onClick = { viewModel.selectAccount(account.email) },
            onRemove = { viewModel.removeAccount(account.email) },
        )
        Spacer(Modifier.height(Dimens.sm))
    }
    Spacer(Modifier.height(Dimens.sm))
    SecondaryButton(
        text = stringResource(R.string.login_use_another),
        onClick = viewModel::showLoginForm,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AccountRow(
    account: SavedAccount,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    PlantoraCard(modifier = Modifier.clickable(enabled = enabled, onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            ) {
                Text(
                    initialsOf(account.displayName),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = Dimens.md)) {
                Text(
                    account.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    account.email + " • " + roleLabel(Role.from(account.role)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Box {
                    IconButton(onClick = { menuOpen = true }, enabled = enabled) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.login_account_options))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.login_remove_account), color = MaterialTheme.colorScheme.error) },
                            onClick = { menuOpen = false; onRemove() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginForm(ui: LoginUiState, viewModel: LoginViewModel) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    PlantoraTextField(
        value = ui.email,
        onValueChange = viewModel::onEmailChange,
        label = stringResource(R.string.login_email),
        placeholder = "you@example.com",
        keyboardType = KeyboardType.Email,
        enabled = !ui.submitting,
    )
    Spacer(Modifier.height(Dimens.md))
    PlantoraTextField(
        value = ui.password,
        onValueChange = viewModel::onPasswordChange,
        label = stringResource(R.string.login_password),
        isPassword = true,
        enabled = !ui.submitting,
        errorText = ui.error?.resolve(ctx),
    )

    Spacer(Modifier.height(Dimens.xl))

    PrimaryButton(
        text = stringResource(R.string.login_submit),
        onClick = viewModel::submit,
        enabled = ui.canSubmit,
        loading = ui.submitting,
        modifier = Modifier.fillMaxWidth(),
    )

    // Only offer "back to saved logins" when there are actually saved accounts.
    if (ui.savedAccounts.isNotEmpty()) {
        Spacer(Modifier.height(Dimens.md))
        SecondaryButton(
            text = stringResource(R.string.login_back_to_saved),
            onClick = viewModel::showSavedAccounts,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Up to two initials from the account's display name, for the avatar. */
private fun initialsOf(name: String): String {
    val parts = name.trim().split(" ", "\t").filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}

@Composable
private fun roleLabel(role: Role): String = when (role) {
    Role.OWNER -> stringResource(R.string.own_role_owner)
    Role.MANAGER -> stringResource(R.string.own_role_manager)
    Role.SALESPERSON -> stringResource(R.string.own_role_salesperson)
    Role.ADMIN -> stringResource(R.string.role_admin)
    Role.UNKNOWN -> ""
}
