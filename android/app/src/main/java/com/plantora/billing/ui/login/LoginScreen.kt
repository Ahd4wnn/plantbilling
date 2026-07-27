package com.plantora.billing.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plantora.billing.ui.components.PlantoraTextField
import com.plantora.billing.ui.components.PrimaryButton
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
            // Use the launcher FOREGROUND (a PNG). The adaptive icon mipmaps
            // (ic_launcher / ic_launcher_round) resolve to an XML adaptive-icon on
            // Android 8+, which Compose's painterResource cannot render — it throws
            // "Only VectorDrawables and rasterized asset types are supported",
            // crashing the app on launch. The foreground is PNG-only, so it's safe.
            //
            // The foreground is a white leaf/receipt on transparent, so on the light
            // login background it's invisible. Sit it on the brand-green tile (the
            // same #37974F as the launcher background) so the mark reads clearly.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xFF37974F)),
            ) {
                Image(
                    painter = painterResource(id = com.plantora.billing.R.mipmap.ic_launcher_foreground),
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
                stringResource(com.plantora.billing.R.string.login_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(Dimens.xxl))

            PlantoraTextField(
                value = ui.email,
                onValueChange = viewModel::onEmailChange,
                label = stringResource(com.plantora.billing.R.string.login_email),
                placeholder = "you@example.com",
                keyboardType = KeyboardType.Email,
                enabled = !ui.submitting,
            )
            Spacer(Modifier.height(Dimens.md))
            PlantoraTextField(
                value = ui.password,
                onValueChange = viewModel::onPasswordChange,
                label = stringResource(com.plantora.billing.R.string.login_password),
                isPassword = true,
                enabled = !ui.submitting,
                errorText = ui.error?.resolve(loginCtx),
            )

            Spacer(Modifier.height(Dimens.xl))

            PrimaryButton(
                text = stringResource(com.plantora.billing.R.string.login_submit),
                onClick = viewModel::submit,
                enabled = ui.canSubmit,
                loading = ui.submitting,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Dimens.xl))
            Text(
                stringResource(com.plantora.billing.R.string.login_footer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
