package com.zksrus.pulse

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * Continuous BLE advertisement scanner that surfaces every advertising device around.
 * Modeled on the Scales "Поиск весов" scanner, but trimmed down: no protocol decoding,
 * just name / address / RSSI. Also exposes the classic bonded devices so the list is
 * non-empty even when nothing is advertising.
 */
class DeviceScanner(context: Context) {

    private val adapter: BluetoothAdapter? =
        (context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
            ?.adapter

    private val handler = Handler(Looper.getMainLooper())

    var listener: ((List<DeviceInfo>) -> Unit)? = null

    /**
     * Predicate telling [pruneOlderThan] to keep a device regardless of staleness.
     * The ViewModel sets this to its pinned-key set so pinned items never drop out.
     */
    var keepPredicate: (String) -> Boolean = { false }

    private val devices = LinkedHashMap<String, DeviceInfo>()
    private var scanning = false

    fun isBluetoothEnabled(): Boolean = adapter != null && adapter!!.isEnabled

    @SuppressLint("MissingPermission")
    fun bondedDevices(): List<DeviceInfo> {
        val a = adapter ?: return emptyList()
        if (!a.isEnabled) return emptyList()
        return a.bondedDevices.orEmpty().map { d ->
            DeviceInfo(
                key = d.address ?: "bonded:${d.name ?: System.identityHashCode(d)}",
                address = d.address ?: "—",
                name = safeName(d),
                rssi = 0,
                isClassic = true,
                isBonded = true,
                lastSeenMs = System.currentTimeMillis(),
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (adapter == null || !adapter!!.isEnabled || scanning) return
        scanning = true
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        adapter!!.bluetoothLeScanner.startScan(null, settings, scanCallback)
        // Seed the list with already-bonded classic devices.
        mergeBonded()
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        if (!scanning) return
        scanning = false
        if (adapter != null && adapter!!.isEnabled) {
            try {
                adapter!!.bluetoothLeScanner.stopScan(scanCallback)
            } catch (_: Exception) {
            }
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val d = result.device ?: return
            val key = safeAddress(d) ?: "anon:${d.name ?: result.rssi}"
            val now = System.currentTimeMillis()
            val prev = devices[key]
            val info = DeviceInfo(
                key = key,
                address = safeAddress(d) ?: "—",
                name = safeName(d),
                rssi = result.rssi,
                isClassic = false,
                isBonded = false,
                lastSeenMs = now,
                packetCount = (prev?.packetCount ?: 0) + 1,
            )
            devices[key] = info
            dispatch()
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { onScanResult(0, it) }
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun mergeBonded() {
        bondedDevices().forEach { if (!devices.containsKey(it.key)) devices[it.key] = it }
        dispatch()
    }

    /** Drop devices not seen recently so the list reflects what is actually around now. */
    fun pruneOlderThan(cutoffMs: Long) {
        var changed = false
        val it = devices.entries.iterator()
        while (it.hasNext()) {
            val (key, info) = it.next()
            if (info.isBonded) continue // keep bonded classic devices for context
            if (keepPredicate(key)) continue // pinned devices stay even when stale
            if (info.lastSeenMs < cutoffMs) {
                it.remove()
                changed = true
            }
        }
        if (changed) dispatch()
    }

    private fun dispatch() {
        // Strongest signal first; bonded classic devices keep their place at the tail.
        val sorted = devices.values.sortedWith(
            compareByDescending<DeviceInfo> { it.rssi }
                .thenBy { it.isBonded }
                .thenBy { it.name ?: it.address }
        )
        handler.post { listener?.invoke(sorted.toList()) }
    }

    @SuppressLint("MissingPermission")
    private fun safeAddress(d: BluetoothDevice): String? =
        try {
            d.address
        } catch (_: SecurityException) {
            null
        }

    @SuppressLint("MissingPermission")
    private fun safeName(d: BluetoothDevice): String? =
        try {
            d.name
        } catch (_: SecurityException) {
            null
        }
}
