package com.zksrus.pulse

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.ExperimentalFoundationApi

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PulseScreen(
    devices: List<DeviceInfo>,
    bluetoothEnabled: Boolean,
    hideOffline: Boolean,
    hrmData: HrmData?,
    onRefresh: () -> Unit,
    onTogglePin: (String) -> Unit,
    onToggleHideOffline: () -> Unit,
    onToggleHrm: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pulse — nearby Bluetooth devices") },
                actions = {
                    FilterChip(
                        selected = hideOffline,
                        onClick = onToggleHideOffline,
                        label = { Text("Online only") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.WifiOff,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (!bluetoothEnabled) {
                BluetoothOffHint()
            } else if (devices.isEmpty()) {
                EmptyHint()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    hrmData?.let { item { HrmDataPanel(it, onClose = { onToggleHrm("") }) } }
                    items(devices, key = { it.key }) { d ->
                        DeviceCard(
                            device = d,
                            onClick = { onTogglePin(d.key) },
                            onLongClick = if (d.isHeartRate) {
                                { onToggleHrm(d.address.takeIf { it.isNotBlank() && it != "—" } ?: d.key) }
                            } else null,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeviceCard(
    device: DeviceInfo,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val typeTag = when {
        device.isHeartRate -> "HRM"
        device.isBonded -> "Classic · bonded"
        device.isClassic -> "Classic"
        else -> "BLE"
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        colors = when {
            device.pinned -> CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            device.isHeartRate -> CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            else -> CardDefaults.cardColors()
        },
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when {
                        device.pinned -> Icons.Default.PushPin
                        device.isHeartRate -> Icons.Default.Favorite
                        else -> Icons.Default.Bluetooth
                    },
                    contentDescription = null,
                    tint = when {
                        device.isHeartRate -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    },
                )
                Spacer(Modifier.padding(end = 8.dp))
                Text(
                    text = device.name?.takeIf { it.isNotBlank() } ?: "Unknown device",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (device.online) "${device.rssi} dBm" else "—",
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = device.address,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tag(typeTag)
                Tag("packets: ${device.packetCount}")
                if (device.pinned) Tag("pinned")
                if (!device.online) Tag("offline")
            }
            if (device.isHeartRate) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Long-press to connect live readings",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Tag(text: String) {
    Box(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small,
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun BluetoothOffHint() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.BluetoothDisabled,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Bluetooth is off. Turn it on and tap refresh.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyHint() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.Bluetooth,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Searching for nearby devices…",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HrmDataPanel(data: HrmData, onClose: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                )
                Spacer(Modifier.padding(end = 8.dp))
                Text(
                    text = when {
                        data.connected -> "Heart Rate Monitor"
                        data.connecting -> "Connecting…"
                        else -> "Disconnected"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Disconnect")
                }
            }

            Spacer(Modifier.height(8.dp))

            // Primary reading: heart rate, big and central.
            val bpmText = data.bpm?.let { "$it" } ?: "—"
            Text(
                text = bpmText,
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = "bpm",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tag(contactLabel(data.sensorContact))
                data.bodyLocation?.let { Tag("loc: $it") }
                data.batteryPercent?.let { Tag("battery: $it%") }
            }

            // Secondary metrics with explanatory units.
            val metrics = buildList {
                data.energyExpended?.let { add("Energy expended: $it kJ  (cumulative since reset)") }
                if (data.rrIntervals.isNotEmpty()) {
                    add("RR intervals: ${data.rrIntervals.joinToString(", ")} ms  (beat-to-beat)")
                }
            }
            if (metrics.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                metrics.forEach {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }

            val infoLines = buildList {
                data.manufacturer?.let { add("Manufacturer: $it") }
                data.modelNumber?.let { add("Model: $it") }
                data.firmwareRevision?.let { add("Firmware: $it") }
                data.hardwareRevision?.let { add("Hardware: $it") }
                data.serialNumber?.let { add("Serial: $it") }
            }
            if (infoLines.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                infoLines.forEach {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }
    }
}

private fun contactLabel(c: SensorContact): String = when (c) {
    SensorContact.NOT_SUPPORTED -> "contact: n/a"
    SensorContact.CONTACT -> "contact: on skin"
    SensorContact.NO_CONTACT -> "contact: off"
}
