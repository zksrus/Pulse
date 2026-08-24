package com.zksrus.pulse

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    private val _hideOffline = MutableStateFlow(false)
    val hideOffline: StateFlow<Boolean> = _hideOffline.asStateFlow()

    /**
     * Pinned device keys in the order they were pinned (most recently pinned first).
     * A pinned device stays at the top of the list regardless of signal or staleness.
     */
    private val pinnedKeys = LinkedHashSet<String>()

    init {
        scanner.keepPredicate = { synchronized(pinnedKeys) { it in pinnedKeys } }
        scanner.listener = { list -> _devices.value = applyView(list) }
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

    /** Toggle the pinned state of a device; pinned items stick to the top of the list. */
    fun togglePin(key: String) {
        synchronized(pinnedKeys) {
            if (!pinnedKeys.remove(key)) {
                // Add at the front: reinsert so the most recent pin sits highest.
                val copy = ArrayList(pinnedKeys)
                copy.add(0, key)
                pinnedKeys.clear()
                pinnedKeys.addAll(copy)
            }
        }
        // Re-emit the current list with updated ordering/pin flags.
        _devices.value = applyView(_devices.value)
    }

    /** Toggle hiding of offline devices; pinned devices stay visible either way. */
    fun toggleHideOffline() {
        _hideOffline.value = !_hideOffline.value
        _devices.value = applyView(_devices.value)
    }

    override fun onCleared() {
        stopScanning()
        super.onCleared()
    }

    /**
     * Applies [hideOffline] (pinned devices stay visible regardless), sorts pinned
     * items to the top (most recently pinned first) and the rest by descending RSSI.
     */
    private fun applyView(list: List<DeviceInfo>): List<DeviceInfo> {
        val order: List<String>
        synchronized(pinnedKeys) { order = pinnedKeys.toList() }
        val hide = _hideOffline.value
        return list
            .map { it.copy(pinned = it.key in order) }
            // Pinned devices stay visible regardless of online state.
            .filter { it.pinned || !hide || it.online }
            .sortedWith(
                compareByDescending<DeviceInfo> { it.pinned }
                    // Pinned: lower pin index (more recent) first; unpinned get MAX so they trail.
                    .thenBy { d -> if (d.pinned) order.indexOf(d.key) else Int.MAX_VALUE }
                    .thenByDescending { it.online }
                    .thenByDescending { it.rssi }
                    .thenBy { it.name ?: it.address }
            )
    }

    companion object {
        private const val STALE_AFTER_MS = 12_000L
        private const val PRUNE_INTERVAL_MS = 3_000L
    }
}
