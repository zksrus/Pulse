package com.zksrus.pulse

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.zksrus.pulse.ui.theme.PulseTheme

class MainActivity : ComponentActivity() {

    private val vm by viewModels<PulseViewModel>()
    private val stepVm by viewModels<StepViewModel>()

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        startIfReady()
    }

    private val enableBtLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        vm.refreshBluetoothState()
        startIfReady()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PulseTheme {
                var selectedTab by remember { mutableIntStateOf(0) }

                val devices by vm.devices.collectAsState()
                val btOn by vm.bluetoothEnabled.collectAsState()
                val hideOffline by vm.hideOffline.collectAsState()
                val hrmData by vm.hrmData.collectAsState()

                val stepUiState by stepVm.uiState.collectAsState()

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Bluetooth, contentDescription = null) },
                                label = { Text("Устройства") },
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.DirectionsWalk, contentDescription = null) },
                                label = { Text("Шаги") },
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 }
                            )
                        }
                    }
                ) { padding ->
                    when (selectedTab) {
                        0 -> PulseScreen(
                            devices = devices,
                            bluetoothEnabled = btOn,
                            hideOffline = hideOffline,
                            hrmData = hrmData,
                            onRefresh = { startIfReady() },
                            onTogglePin = { vm.togglePin(it) },
                            onToggleHideOffline = { vm.toggleHideOffline() },
                            onToggleHrm = { address ->
                                if (address.isEmpty()) vm.disconnectHrm() else vm.connectHrm(address)
                            },
                            modifier = Modifier.padding(padding)
                        )
                        1 -> StepCounterScreen(
                            uiState = stepUiState,
                            onRefresh = { stepVm.refresh() },
                            onGoalChanged = { /* Goal update */ },
                            modifier = Modifier.padding(padding)
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshBluetoothState()
        startIfReady()
    }

    override fun onPause() {
        super.onPause()
        vm.disconnectHrm()
        vm.stopScanning()
    }

    private fun startIfReady() {
        if (!hasRuntimePermissions()) {
            permissionsLauncher.launch(requiredPermissions())
            return
        }
        vm.refreshBluetoothState()
        if (!vm.bluetoothEnabled.value) {
            enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }
        vm.startScanning()
    }

    private fun hasRuntimePermissions(): Boolean =
        requiredPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun requiredPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
        else -> arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    }
}
