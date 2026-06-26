package com.plantora.billing

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlantoraTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PlantoraRoot()
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
            is AuthState.Authenticated -> MainShell(user = s.user, onLogout = viewModel::logout)
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
