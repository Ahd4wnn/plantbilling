package com.plantora.billing

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.plantora.billing.i18n.LocaleManager
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plantora.billing.data.AuthState
import com.plantora.billing.ui.RootViewModel
import com.plantora.billing.ui.components.LoadingState
import com.plantora.billing.ui.login.LoginScreen
import com.plantora.billing.ui.nav.MainShell
import com.plantora.billing.ui.nav.UnsupportedRoleScreen
import com.plantora.billing.ui.theme.PlantoraTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // Apply the in-app language choice and pin the display metrics (font scale,
    // display size, bold text) before the UI is built. Picking a new language in
    // More recreates the activity, which re-runs this.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Second line of defence behind LocaleManager.wrap, which already pins the
            // font scale on the Configuration. Cheap, and covers any Context that
            // reaches composition without going through attachBaseContext.
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = 1f),
            ) {
                PlantoraTheme {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        PlantoraRoot()
                    }
                }
            }
        }
    }
}

@Composable
private fun PlantoraRoot(viewModel: RootViewModel = hiltViewModel()) {
    val state by viewModel.authState.collectAsStateWithLifecycle()
    val connected by viewModel.isConnected.collectAsStateWithLifecycle()

    Crossfade(targetState = state, animationSpec = tween(220), label = "auth") { s ->
        when (s) {
            is AuthState.Loading -> LoadingState()
            is AuthState.Unauthenticated -> LoginScreen()
            is AuthState.Authenticated ->
                when {
                    s.user.isAdmin ->
                        com.plantora.billing.ui.admin.AdminShell(user = s.user, onLogout = viewModel::logout)
                    s.user.isOwner ->
                        com.plantora.billing.ui.owner.OwnerShell(user = s.user, onLogout = viewModel::logout)
                    else ->
                        MainShell(user = s.user, onLogout = viewModel::logout)
                }
            is AuthState.UnsupportedRole -> UnsupportedRoleScreen(onLogout = viewModel::logout)
        }
    }

    if (!connected) {
        NoInternetDialog(onRetry = viewModel::retryConnection)
    }
}

@Composable
private fun NoInternetDialog(onRetry: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { /* blocking — the connection must be resolved */ },
        icon = {
            androidx.compose.material3.Icon(
                Icons.Rounded.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { androidx.compose.material3.Text("No internet connection") },
        text = {
            androidx.compose.material3.Text(
                "We can't reach PlantBill. Please check your internet connection and try again.",
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onRetry) {
                androidx.compose.material3.Text("Try again")
            }
        },
    )
}
