package com.zksrus.pulse.viewmodel

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zksrus.pulse.ble.HeartRateManager
import com.zksrus.pulse.ble.HeartRateParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Exposes BLE scanning, connection state and the latest heart-rate reading to the UI.
 * Survives configuration changes because it is scoped to the Application.
 */
class PulseViewModel(app: Application) : AndroidViewModel(app) {

    sealed interface UiState {
        data object Idle : UiState
        data object Scanning : UiState
        data object Connecting : UiState
        data object Measuring : UiState
        data class Error(val message: String) : UiState
    }

    private val manager = HeartRateManager(getApplication())

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _devices = MutableStateFlow<List<HeartRateManager.HrDevice>>(emptyList())
    val devices: StateFlow<List<HeartRateManager.HrDevice>> = _devices.asStateFlow()

    private val _heartRate = MutableStateFlow<Int?>(null)
    val heartRate: StateFlow<Int?> = _heartRate.asStateFlow()

    private val _sensorContact = MutableStateFlow<Pair<Boolean, Boolean>?>(null)
    val sensorContact: StateFlow<Pair<Boolean, Boolean>?> = _sensorContact.asStateFlow()

    private val _bodyLocation = MutableStateFlow<String?>(null)
    val bodyLocation: StateFlow<String?> = _bodyLocation.asStateFlow()

    private val _bluetoothEnabled = MutableStateFlow(true)
    val bluetoothEnabled: StateFlow<Boolean> = _bluetoothEnabled.asStateFlow()

    private val _hasPermissions = MutableStateFlow(false)
    val hasPermissions: StateFlow<Boolean> = _hasPermissions.asStateFlow()

    init {
        manager.scanListener = object : HeartRateManager.ScanListener {
            override fun onDeviceFound(device: HeartRateManager.HrDevice) {
                val updated = _devices.value.toMutableList()
                val idx = updated.indexOfFirst { it.address == device.address }
                if (idx >= 0) updated[idx] = device else updated.add(device)
                _devices.value = updated.sortedByDescending { it.rssi }
            }

            override fun onScanFailed(errorCode: Int) {
                _uiState.value = UiState.Error("Scan failed (error $errorCode)")
            }
        }

        manager.connectionListener = object : HeartRateManager.ConnectionListener {
            override fun onConnectionStateChanged(state: HeartRateManager.ConnectionState) {
                _uiState.value = when (state) {
                    HeartRateManager.ConnectionState.CONNECTING -> UiState.Connecting
                    HeartRateManager.ConnectionState.CONNECTED -> UiState.Measuring
                    HeartRateManager.ConnectionState.DISCONNECTING -> UiState.Connecting
                    HeartRateManager.ConnectionState.DISCONNECTED -> {
                        _heartRate.value = null
                        _sensorContact.value = null
                        UiState.Idle
                    }
                }
            }

            override fun onHeartRate(heartRate: Int, measurement: HeartRateParser.Measurement?) {
                _heartRate.value = heartRate
            }

            override fun onSensorContact(supported: Boolean, detected: Boolean) {
                _sensorContact.value = supported to detected
            }

            override fun onBodySensorLocation(location: String) {
                _bodyLocation.value = location
            }

            override fun onError(message: String) {
                _uiState.value = UiState.Error(message)
            }
        }
    }

    fun refreshBluetoothState() {
        val bm = getApplication<Application>().getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        _bluetoothEnabled.value = bm?.adapter?.isEnabled == true
    }

    fun checkPermissions(): Boolean {
        val pm = getApplication<Application>().packageManager
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissionsSPlus().all {
                pm.checkPermission(it, getApplication<Application>().packageName) ==
                    PackageManager.PERMISSION_GRANTED
            }
        } else {
            requiredPermissionsPreS().all {
                pm.checkPermission(it, getApplication<Application>().packageName) ==
                    PackageManager.PERMISSION_GRANTED
            }
        }
        _hasPermissions.value = granted
        return granted
    }

    fun startScan() {
        if (!checkPermissions()) {
            _hasPermissions.value = false
            return
        }
        refreshBluetoothState()
        if (!_bluetoothEnabled.value) {
            _uiState.value = UiState.Error("Bluetooth is turned off")
            return
        }
        _devices.value = emptyList()
        _uiState.value = UiState.Scanning
        manager.startScan()
    }

    fun stopScan() {
        manager.stopScan()
        if (_uiState.value is UiState.Scanning) {
            _uiState.value = UiState.Idle
        }
    }

    fun connect(device: HeartRateManager.HrDevice) {
        manager.stopScan()
        _uiState.value = UiState.Connecting
        _heartRate.value = null
        _sensorContact.value = null
        _bodyLocation.value = null
        manager.connect(device.device)
    }

    fun disconnect() {
        manager.disconnect()
        _heartRate.value = null
        _uiState.value = UiState.Idle
    }

    override fun onCleared() {
        manager.stopScan()
        manager.disconnect()
    }
}

/** Permissions required on Android 12+ (API 31+). */
private fun requiredPermissionsSPlus(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    arrayOf(
        android.Manifest.permission.BLUETOOTH_SCAN,
        android.Manifest.permission.BLUETOOTH_CONNECT,
    )
} else emptyArray()

/** Permissions required on Android 11 and below. */
private fun requiredPermissionsPreS(): Array<String> = arrayOf(
    android.Manifest.permission.BLUETOOTH,
    android.Manifest.permission.BLUETOOTH_ADMIN,
    android.Manifest.permission.ACCESS_FINE_LOCATION,
)
