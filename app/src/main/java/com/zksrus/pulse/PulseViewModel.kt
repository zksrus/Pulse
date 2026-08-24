package com.zksrus.pulse

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PulseViewModel(app: Application) : AndroidViewModel(app) {

    private val scanner = DeviceScanner(app)
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val pruneRunnable = object : Runnable {
        override fun run() {
            scanner.pruneOlderThan(System.currentTimeMillis() - STALE_AFTER_MS)
            handler.postDelayed(this, PRUNE_INTERVAL_MS)
        }
    }

    private val _devices = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val devices: StateFlow<List<DeviceInfo>> = _devices.asStateFlow()

    private val _bluetoothEnabled = MutableStateFlow(false)
    val bluetoothEnabled: StateFlow<Boolean> = _bluetoothEnabled.asStateFlow()

    init {
        scanner.listener = { list -> _devices.value = list }
    }

    fun refreshBluetoothState() {
        _bluetoothEnabled.value = scanner.isBluetoothEnabled()
    }

    fun startScanning() {
        refreshBluetoothState()
        if (scanner.isBluetoothEnabled()) {
            scanner.start()
            handler.removeCallbacks(pruneRunnable)
            handler.postDelayed(pruneRunnable, PRUNE_INTERVAL_MS)
        }
    }

    fun stopScanning() {
        handler.removeCallbacks(pruneRunnable)
        scanner.stop()
    }

    override fun onCleared() {
        stopScanning()
        super.onCleared()
    }

    companion object {
        private const val STALE_AFTER_MS = 12_000L
        private const val PRUNE_INTERVAL_MS = 3_000L
    }
}
