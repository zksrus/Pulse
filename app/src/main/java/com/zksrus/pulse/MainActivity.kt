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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.zksrus.pulse.ui.theme.PulseTheme

class MainActivity : ComponentActivity() {

    private val vm by viewModels<PulseViewModel>()

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
                val devices by vm.devices.collectAsState()
                val btOn by vm.bluetoothEnabled.collectAsState()
                val hideOffline by vm.hideOffline.collectAsState()
                PulseScreen(
                    devices = devices,
                    bluetoothEnabled = btOn,
                    hideOffline = hideOffline,
                    onRefresh = { startIfReady() },
                    onTogglePin = { vm.togglePin(it) },
                    onToggleHideOffline = { vm.toggleHideOffline() },
                )
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
