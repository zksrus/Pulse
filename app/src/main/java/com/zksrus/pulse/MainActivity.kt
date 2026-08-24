package com.zksrus.pulse

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zksrus.pulse.ui.DeviceScanScreen
import com.zksrus.pulse.ui.HeartRateScreen
import com.zksrus.pulse.viewmodel.PulseViewModel

class MainActivity : ComponentActivity() {

    private val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.all { it }) {
                viewModel.startScan()
            }
        }

    private lateinit var viewModel: PulseViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = androidx.lifecycle.ViewModelProvider(this)[PulseViewModel::class.java]
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppContent()
                }
            }
        }
    }

    @Composable
    private fun AppContent() {
        var hasPermissions by remember { mutableStateOf(checkPermissions()) }
        val bluetoothEnabled by viewModel.bluetoothEnabled.collectAsStateWithLifecycle()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        when {
            !hasPermissions -> PermissionScreen(
                onRequest = {
                    permissionLauncher.launch(requiredPermissions)
                    hasPermissions = checkPermissions()
                },
            )

            !bluetoothEnabled -> BluetoothDisabledScreen(
                onEnable = { promptEnableBluetooth() },
            )

            uiState == PulseViewModel.UiState.Measuring ||
                uiState == PulseViewModel.UiState.Connecting -> HeartRateScreen(viewModel)

            else -> DeviceScanScreen(viewModel)
        }
    }

    @Composable
    private fun PermissionScreen(onRequest: () -> Unit) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Pulse needs Bluetooth permissions to find heart-rate monitors.", fontSize = 16.sp)
                Spacer(modifier = Modifier.padding(20.dp))
                Button(onClick = onRequest) { Text("Grant permissions") }
            }
        }
    }

    @Composable
    private fun BluetoothDisabledScreen(onEnable: () -> Unit) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Bluetooth is turned off.", fontSize = 18.sp)
                Spacer(modifier = Modifier.padding(20.dp))
                Button(onClick = onEnable) { Text("Turn on Bluetooth") }
            }
        }
    }

    private fun checkPermissions(): Boolean = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun promptEnableBluetooth() {
        try {
            startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshBluetoothState()
        if (checkPermissions() &&
            isBluetoothEnabled() &&
            viewModel.uiState.value == PulseViewModel.UiState.Idle
        ) {
            viewModel.startScan()
        }
    }

    private fun isBluetoothEnabled(): Boolean =
        (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.isEnabled == true
}

