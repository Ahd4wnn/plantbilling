package com.plantora.billing.ui.printer

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plantora.billing.print.PrinterDevice
import com.plantora.billing.ui.components.PlantoraCard
import com.plantora.billing.ui.components.PrimaryButton
import com.plantora.billing.ui.components.SecondaryButton
import com.plantora.billing.ui.components.SectionHeader
import com.plantora.billing.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterScreen(
    onBack: () -> Unit,
    viewModel: PrinterViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants -> viewModel.onPermissionResult(grants.values.all { it }) }

    fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN),
            )
        } else {
            viewModel.onPermissionResult(true)
        }
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Printer") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.lg),
        ) {
            val selected = ui.selectedDevice
            val hasSelection = ui.selectedMac != null
            PlantoraCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (hasSelection) Icons.Rounded.CheckCircle else Icons.Rounded.Bluetooth,
                        contentDescription = null,
                        tint = if (hasSelection) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.padding(Dimens.sm))
                    Text(
                        when {
                            selected != null -> "Default printer: ${selected.name}"
                            hasSelection -> "Printer set (not paired on this phone)"
                            else -> "No printer chosen"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Spacer(Modifier.height(Dimens.sm))
                Text(
                    "The printer connects only while a receipt prints, then frees up — so several salespeople can share one printer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (hasSelection) {
                    Spacer(Modifier.height(Dimens.md))
                    SecondaryButton(
                        if (ui.busy) "Printing…" else "Test print",
                        onClick = viewModel::testPrint,
                        leadingIcon = Icons.Rounded.Print,
                    )
                }
            }

            when {
                !ui.bluetoothSupported -> Text("This device has no Bluetooth.")
                ui.needsPermission -> PrimaryButton("Allow Bluetooth", onClick = { requestPermission() })
                !ui.bluetoothEnabled -> Text("Please turn on Bluetooth, then tap Refresh.")
            }

            SectionHeader("Paired printers")
            if (ui.devices.isEmpty()) {
                Text(
                    "No paired printers found. Pair your thermal printer in Android Bluetooth settings, then tap Refresh.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                ui.devices.forEach { device ->
                    DeviceRow(
                        device = device,
                        connecting = ui.selecting == device.mac,
                        connected = ui.selectedMac == device.mac,
                        onClick = { viewModel.select(device) },
                    )
                }
            }
            SecondaryButton("Refresh", onClick = viewModel::refresh)

            SectionHeader("Paper")
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                FilterChip(
                    selected = ui.paperWidthChars == 32,
                    onClick = { viewModel.setPaperWidth(32) },
                    label = { Text("58 mm") },
                )
                FilterChip(
                    selected = ui.paperWidthChars == 48,
                    onClick = { viewModel.setPaperWidth(48) },
                    label = { Text("80 mm") },
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Auto-cut paper", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = ui.autoCut, onCheckedChange = viewModel::setAutoCut)
            }

            ui.message?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: PrinterDevice,
    connecting: Boolean,
    connected: Boolean,
    onClick: () -> Unit,
) {
    PlantoraCard(modifier = Modifier.clickable(enabled = !connecting, onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(device.name, style = MaterialTheme.typography.titleMedium)
                Text(device.mac, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when {
                connecting -> CircularProgressIndicator(Modifier.height(24.dp))
                connected -> Icon(Icons.Rounded.CheckCircle, contentDescription = "Connected", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
