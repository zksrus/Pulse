package com.zksrus.pulse.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zksrus.pulse.ble.HeartRateManager
import com.zksrus.pulse.viewmodel.PulseViewModel

/**
 * Shows the list of discovered heart-rate monitors so the user can pick one to connect to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScanScreen(viewModel: PulseViewModel) {
    val devices by viewModel.devices.collectAsStateLifecycle()
    val uiState by viewModel.uiState.collectAsStateLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pulse — find a heart-rate monitor") },
                actions = {
                    val isScanning = uiState is PulseViewModel.UiState.Scanning
                    IconButton(onClick = {
                        if (isScanning) viewModel.stopScan() else viewModel.startScan()
                    }) {
                        Icon(
                            imageVector = if (isScanning) Icons.Filled.Stop else Icons.Filled.Refresh,
                            contentDescription = if (isScanning) "Stop scan" else "Start scan",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when (uiState) {
                        is PulseViewModel.UiState.Scanning -> "Scanning…"
                        is PulseViewModel.UiState.Connecting -> "Connecting…"
                        is PulseViewModel.UiState.Error -> (uiState as PulseViewModel.UiState.Error).message
                        else -> "Tap the refresh button in the top-right to scan"
                    },
                    fontSize = 14.sp,
                    color = if (uiState is PulseViewModel.UiState.Error)
                        MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                )
                if (uiState is PulseViewModel.UiState.Scanning) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (devices.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    val text = when {
                        uiState is PulseViewModel.UiState.Scanning ->
                            "Looking for heart-rate monitors…\nMake sure your monitor is switched on and nearby."
                        uiState is PulseViewModel.UiState.Error ->
                            "Could not find any devices.\n" +
                                "Check that Bluetooth and Location are enabled, then try again."
                        else -> "No devices found.\nTap the refresh button to scan."
                    }
                    Text(
                        text = text,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(devices, key = { it.address }) { device ->
                        DeviceRow(device) { viewModel.connect(device) }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(device: HeartRateManager.HrDevice, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Bluetooth,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.padding(start = 12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = device.name, fontSize = 18.sp)
            Text(
                text = device.address,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(text = "${device.rssi} dBm", fontSize = 14.sp)
    }
}
