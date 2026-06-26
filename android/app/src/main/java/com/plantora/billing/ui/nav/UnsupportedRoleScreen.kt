package com.plantora.billing.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.plantora.billing.ui.components.EmptyState
import com.plantora.billing.ui.components.SecondaryButton
import com.plantora.billing.ui.theme.Dimens

/** Shown when an admin signs in: this app is for shop owners/salespeople. */
@Composable
fun UnsupportedRoleScreen(onLogout: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(Dimens.lg), contentAlignment = Alignment.Center) {
        EmptyState(
            icon = Icons.Rounded.Computer,
            title = "Use the web app",
            message = "This app is for shop owners and salespeople. " +
                "Admin tools are available on the PlantBill web dashboard.",
            action = { SecondaryButton(text = "Log out", onClick = onLogout) },
        )
    }
}
