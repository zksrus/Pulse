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
import java.util.UUID

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
    fun getRemoteDevice(address: String): BluetoothDevice? =
        try {
            adapter?.getRemoteDevice(address)
        } catch (_: Exception) {
            null
        }

    @SuppressLint("MissingPermission")
    fun bondedDevices(): List<DeviceInfo> {
        val a = adapter ?: return emptyList()
        if (!a.isEnabled) return emptyList()
        return a.bondedDevices.orEmpty().map { d ->
            DeviceInfo(
                key = d.address ?: "bonded:${d.name ?: System.identityHashCode(d)}",
                address = d.address ?: "—",
                name = safeName(d),
                rssi = Int.MIN_VALUE, // unknown; sorts below any real signal
                isClassic = true,
                isBonded = true,
                lastSeenMs = 0L, // offline until it actually advertises
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
            // A heart-rate sensor stays one for the session: once detected (by UUID or
            // by name), keep the flag even on later packets that happen to omit the
            // service UUID list.
            val isHr = prev?.isHeartRate == true || isHeartRateDevice(result)
            val info = DeviceInfo(
                key = key,
                address = safeAddress(d) ?: "—",
                name = safeName(d),
                rssi = result.rssi,
                isClassic = prev?.isClassic ?: false,
                isBonded = prev?.isBonded ?: false,
                lastSeenMs = now,
                packetCount = (prev?.packetCount ?: 0) + 1,
                isHeartRate = isHr,
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

    /**
     * Detects a chest-strap / HRM beacon. Primary signal is the standard Heart Rate
     * Service UUID (0x180D) in the advertising payload; falls back to a name allowlist
     * for devices that omit the service list from their advertisement.
     */
    private fun isHeartRateDevice(result: ScanResult): Boolean {
        val record = result.scanRecord ?: return false
        if (record.serviceUuids?.any { it.uuid == HEART_RATE_SERVICE_UUID } == true) return true
        val name = record.deviceName?.lowercase() ?: return false
        return HR_NAME_HINTS.any { name.contains(it) }
    }

    @SuppressLint("MissingPermission")
    private fun mergeBonded() {
        // Seed the list with bonded classic devices, but never overwrite a row that is
        // already being fed live packets by the BLE scanner (a bonded phone that also
        // advertises would otherwise be demoted to "offline, no signal").
        bondedDevices().forEach {
            devices.putIfAbsent(it.key, it)
        }
        dispatch()
    }

    /** Drop devices not seen recently so the list reflects what is actually around now. */
    fun pruneOlderThan(cutoffMs: Long) {
        var changed = false
        val it = devices.entries.iterator()
        while (it.hasNext()) {
            val (key, info) = it.next()
            if (keepPredicate(key)) continue // pinned devices stay even when stale
            if (info.isBonded && info.lastSeenMs == 0L) continue // never-seen bonded: keep for context
            if (info.lastSeenMs < cutoffMs) {
                it.remove()
                changed = true
            }
        }
        if (changed) dispatch()
    }

    private fun dispatch() {
        val now = System.currentTimeMillis()
        // Online = advertised within the stale window. Bonded devices that never
        // advertised (lastSeenMs == 0) are offline — but kept for context.
        val sorted = devices.values.map { d ->
            d.copy(online = d.lastSeenMs != 0L && d.lastSeenMs >= now - STALE_AFTER_MS)
        }.sortedWith(
            compareByDescending<DeviceInfo> { it.online }
                .thenByDescending { it.rssi }
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

    companion object {
        /** Standard GATT Heart Rate Service. */
        private val HEART_RATE_SERVICE_UUID: UUID =
            UUID.fromString("0000180D-0000-1000-8000-00805F9B34FB")

        /** Name fragments typical of chest-strap / HRM beacons (fallback detection). */
        private val HR_NAME_HINTS = listOf(
            "hrm", "tickr", "polar", "wahoo", "garmin", "zephyr", "suunto",
            "sigma", "magene", "hr-", "heart rate", "chest",
        )

        /** A device is considered "offline" once it has not advertised for this long. */
        private const val STALE_AFTER_MS = 12_000L
    }
}
