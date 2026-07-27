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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plantora.billing.R
import com.plantora.billing.i18n.asString
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
                title = { Text(stringResource(R.string.more_printer)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
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
                            selected != null -> stringResource(R.string.prn_default, selected.name)
                            hasSelection -> stringResource(R.string.prn_set_not_paired)
                            else -> stringResource(R.string.prn_none_chosen)
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Spacer(Modifier.height(Dimens.sm))
                Text(
                    stringResource(R.string.prn_shared_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (hasSelection) {
                    Spacer(Modifier.height(Dimens.md))
                    SecondaryButton(
                        if (ui.busy) stringResource(R.string.prn_printing) else stringResource(R.string.prn_test_print),
                        onClick = viewModel::testPrint,
                        leadingIcon = Icons.Rounded.Print,
                    )
                }
            }

            when {
                !ui.bluetoothSupported -> Text(stringResource(R.string.prn_no_bluetooth))
                ui.needsPermission -> PrimaryButton(stringResource(R.string.prn_allow_bluetooth), onClick = { requestPermission() })
                !ui.bluetoothEnabled -> Text(stringResource(R.string.prn_turn_on_bluetooth))
            }

            SectionHeader(stringResource(R.string.prn_paired))
            if (ui.devices.isEmpty()) {
                Text(
                    stringResource(R.string.prn_no_paired),
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
            SecondaryButton(stringResource(R.string.prn_refresh), onClick = viewModel::refresh)

            SectionHeader(stringResource(R.string.prn_paper))
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                FilterChip(
                    selected = ui.paperWidthChars == 32,
                    onClick = { viewModel.setPaperWidth(32) },
                    label = { Text(stringResource(R.string.prn_58mm)) },
                )
                FilterChip(
                    selected = ui.paperWidthChars == 48,
                    onClick = { viewModel.setPaperWidth(48) },
                    label = { Text(stringResource(R.string.prn_80mm)) },
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.prn_auto_cut), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = ui.autoCut, onCheckedChange = viewModel::setAutoCut)
            }

            ui.message?.let {
                Text(it.asString(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
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
                connected -> Icon(Icons.Rounded.CheckCircle, contentDescription = stringResource(R.string.cd_connected), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
