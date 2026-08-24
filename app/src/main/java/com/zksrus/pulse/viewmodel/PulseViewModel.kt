package com.zksrus.pulse.viewmodel

import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zksrus.pulse.ble.HeartRateManager
import com.zksrus.pulse.ble.HeartRateParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    /**
     * On Android 11 and below the system Location service must be ON for BLE scans to return
     * results. The UI shows a banner when this is false.
     */
    private val _locationEnabled = MutableStateFlow(true)
    val locationEnabled: StateFlow<Boolean> = _locationEnabled.asStateFlow()

    private var scanTimeoutJob: Job? = null

    init {
        manager.scanListener = object : HeartRateManager.ScanListener {
            override fun onDeviceFound(device: HeartRateManager.HrDevice) {
                val updated = _devices.value.toMutableList()
                val idx = updated.indexOfFirst { it.address == device.address }
                if (idx >= 0) updated[idx] = device else updated.add(device)
                _devices.value = updated.sortedByDescending { it.rssi }
            }

            override fun onScanFailed(errorCode: Int) {
                _uiState.value = UiState.Error(manager.describeScanError(errorCode))
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
        refreshLocationState()
    }

    fun refreshLocationState() {
        // Only relevant on Android 11 and below, but cheap to compute everywhere.
        val lm = getApplication<Application>().getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        _locationEnabled.value = lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
            lm?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
    }

    fun checkPermissions(): Boolean {
        val pm = getApplication<Application>().packageManager
        val pkg = getApplication<Application>().packageName
        val granted = requiredPermissions().all {
            pm.checkPermission(it, pkg) == PackageManager.PERMISSION_GRANTED
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
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R && !_locationEnabled.value) {
            // On Android 11 and below the Location service must be enabled or the scan returns
            // no results (and on some OEMs it fails outright).
            _uiState.value = UiState.Error("Turn on Location (GPS) to scan for BLE devices")
            return
        }
        _devices.value = emptyList()
        _uiState.value = UiState.Scanning
        val started = manager.startScan()
        if (!started) {
            _uiState.value = UiState.Error("Could not start scanning. Check Bluetooth and permissions.")
            return
        }
        // Stop scanning after a while so we don't drain the battery; the user can refresh again.
        scanTimeoutJob?.cancel()
        scanTimeoutJob = viewModelScope.launch {
            delay(SCAN_TIMEOUT_MS)
            stopScan()
        }
    }

    fun stopScan() {
        scanTimeoutJob?.cancel()
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
        scanTimeoutJob?.cancel()
        manager.stopScan()
        manager.disconnect()
    }

    private companion object {
        /** How long a single scan runs before auto-stopping (ms). */
        const val SCAN_TIMEOUT_MS = 15_000L
    }
}

/**
 * All runtime permissions the app needs. On Android 12+ that's the new Bluetooth permissions
 * plus location (kept because some OEM stacks still require it for BLE). On older versions the
 * legacy Bluetooth permissions plus fine location are mandatory for scanning.
 */
private fun requiredPermissions(): Array<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(android.Manifest.permission.BLUETOOTH_SCAN)
        add(android.Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        add(android.Manifest.permission.BLUETOOTH)
        add(android.Manifest.permission.BLUETOOTH_ADMIN)
    }
    add(android.Manifest.permission.ACCESS_FINE_LOCATION)
}.toTypedArray()
