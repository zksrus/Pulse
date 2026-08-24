package com.zksrus.pulse.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import java.util.UUID

/**
 * Central BLE manager: scans for heart-rate monitors and connects to a chosen device,
 * subscribing to the standard Heart Rate Measurement characteristic (0x2A37).
 *
 * Works with ANY heart-rate monitor that conforms to the Bluetooth SIG Heart Rate Service
 * (Polar, Garmin, HryFine HR40, generic chest straps, armbands, etc.).
 */
class HeartRateManager(private val context: Context) {

    companion object {
        private const val TAG = "HeartRateManager"
    }

    /** A discovered BLE heart-rate monitor. */
    data class HrDevice(
        val device: BluetoothDevice,
        val name: String,
        val address: String,
        val rssi: Int,
    )

    enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING }

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? get() = bluetoothManager.adapter

    private var gatt: BluetoothGatt? = null

    // ─────────────────────────────────────────────────────────────────────
    // Scanning
    // ─────────────────────────────────────────────────────────────────────

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach(::handleScanResult)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed, errorCode=$errorCode")
            scanListener?.onScanFailed(errorCode)
        }
    }

    private val foundDevices = mutableMapOf<String, HrDevice>()
    var scanListener: ScanListener? = null

    interface ScanListener {
        fun onDeviceFound(device: HrDevice)
        fun onScanFailed(errorCode: Int)
    }

    private fun handleScanResult(result: ScanResult) {
        val device = result.device
        val name = device.name ?: result.scanRecord?.deviceName
        ?: guessNameFromScanRecord(result.scanRecord)
        val address = device.address

        // Accept devices that either advertise the Heart Rate Service UUID or expose a
        // plausible heart-rate-related name. This keeps the list focused on HR monitors.
        if (!looksLikeHeartRateMonitor(result, name)) return

        val hrDevice = HrDevice(
            device = device,
            name = (name?.ifBlank { null } ?: getStringResource(context, "unknown_device")),
            address = address,
            rssi = result.rssi,
        )
        if (foundDevices.put(address, hrDevice) == null) {
            scanListener?.onDeviceFound(hrDevice)
        } else {
            scanListener?.onDeviceFound(hrDevice) // notify again with updated RSSI
        }
    }

    /**
     * Decides whether a scan result represents a heart-rate monitor. A device qualifies if it
     * advertises the Heart Rate Service (0x180D) in its service list, or its name contains
     * common heart-rate keywords (since some cheap monitors don't advertise the service UUID).
     */
    private fun looksLikeHeartRateMonitor(result: ScanResult, name: String?): Boolean {
        val record = result.scanRecord
        val services = record?.serviceUuids
        val advertisesHrService = services?.any { it.uuid == HeartRateUuids.SERVICE } == true
        if (advertisesHrService) return true

        // Fallback: match by name. Covers devices like "HR40" that may not advertise the service UUID.
        val n = name?.lowercase().orEmpty()
        val keywords = listOf("heart", "hrm", "puls", "hr-", " hr", "hr40", "hr40", "sensor")
        return keywords.any { n.contains(it) } || n.matches(Regex(".*hr\\d{2,3}.*"))
    }

    private fun guessNameFromScanRecord(record: android.bluetooth.le.ScanRecord?): String? {
        record ?: return null
        // Some devices put their name into the manufacturer data or complete local name only.
        return record.deviceName
    }

    /** Starts a scan for heart-rate monitors. Returns false if Bluetooth is unavailable. */
    fun startScan(): Boolean {
        if (adapter == null || adapter?.isEnabled != true) {
            Log.w(TAG, "Bluetooth adapter unavailable or disabled")
            return false
        }
        foundDevices.clear()

        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            Log.w(TAG, "BluetoothLeScanner unavailable")
            return false
        }

        // Prefer filtering by the Heart Rate Service UUID, but also allow unfiltered scan so we
        // can fall back to name-based detection for non-conformant devices.
        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(HeartRateUuids.SERVICE))
                .build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        return try {
            scanner.startScan(filters, settings, scanCallback)
            // Additionally run a short unfiltered pass to catch name-only devices.
            scanner.startScan(null, settings, scanCallback)
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing permission to scan", e)
            false
        }
    }

    fun stopScan() {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner != null) {
            try {
                scanner.stopScan(scanCallback)
            } catch (_: SecurityException) {
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // GATT connection
    // ─────────────────────────────────────────────────────────────────────

    var connectionListener: ConnectionListener? = null

    interface ConnectionListener {
        fun onConnectionStateChanged(state: ConnectionState)
        fun onHeartRate(heartRate: Int, measurement: HeartRateParser.Measurement?)
        fun onSensorContact(supported: Boolean, detected: Boolean)
        fun onBodySensorLocation(location: String)
        fun onError(message: String)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectionListener?.onConnectionStateChanged(ConnectionState.CONNECTED)
                    try {
                        gatt.discoverServices()
                    } catch (_: SecurityException) {
                        connectionListener?.onError("No permission to discover services")
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectionListener?.onConnectionStateChanged(ConnectionState.DISCONNECTED)
                    try {
                        gatt.close()
                    } catch (_: SecurityException) {
                    }
                    this@HeartRateManager.gatt = null
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = gatt.getService(HeartRateUuids.SERVICE)
            if (service == null) {
                connectionListener?.onError("Heart Rate Service not found on this device")
                disconnect()
                return
            }
            val measurement = service.getCharacteristic(HeartRateUuids.MEASUREMENT)
            if (measurement == null) {
                connectionListener?.onError("Heart Rate Measurement characteristic not found")
                disconnect()
                return
            }

            try {
                gatt.setCharacteristicNotification(measurement, true)
                val descriptor = measurement.getDescriptor(HeartRateUuids.CLIENT_CONFIG)
                if (descriptor != null) {
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                }
            } catch (e: SecurityException) {
                connectionListener?.onError("No permission to enable notifications")
            }

            // Optionally read Body Sensor Location for an extra detail line.
            val bodyLocation = service.getCharacteristic(HeartRateUuids.BODY_SENSOR_LOCATION)
            if (bodyLocation != null) {
                try {
                    gatt.readCharacteristic(bodyLocation)
                } catch (_: SecurityException) {
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            handleCharacteristicNotification(characteristic.value)
        }

        // New callback signature on Android 13+ (T). Keep the legacy one for older versions.
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleCharacteristicNotification(value)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (characteristic.uuid == HeartRateUuids.BODY_SENSOR_LOCATION) {
                val location = describeBodyLocation(characteristic.value?.firstOrNull()?.toInt() ?: -1)
                connectionListener?.onBodySensorLocation(location)
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            if (characteristic.uuid == HeartRateUuids.BODY_SENSOR_LOCATION) {
                val location = describeBodyLocation(value.firstOrNull()?.toInt() ?: -1)
                connectionListener?.onBodySensorLocation(location)
            }
        }
    }

    private fun handleCharacteristicNotification(value: ByteArray) {
        val measurement = HeartRateParser.parse(value) ?: return
        connectionListener?.onHeartRate(measurement.heartRate, measurement)
        connectionListener?.onSensorContact(
            measurement.sensorContactSupported,
            measurement.sensorContactDetected,
        )
    }

    private fun describeBodyLocation(code: Int): String = when (code) {
        0 -> "Other"
        1 -> "Chest"
        2 -> "Wrist"
        3 -> "Finger"
        4 -> "Hand"
        5 -> "Ear lobe"
        6 -> "Foot"
        else -> getStringResource(context, "location_unknown")
    }

    /** Connects to the given [device] and subscribes to heart-rate notifications. */
    fun connect(device: BluetoothDevice) {
        if (gatt != null) {
            disconnect()
        }
        connectionListener?.onConnectionStateChanged(ConnectionState.CONNECTING)
        gatt = try {
            // TRANSPORT_LE = 2; request the LE transport explicitly for a reliable GATT link.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, gattCallback, 2)
            } else {
                device.connectGatt(context, false, gattCallback)
            }
        } catch (e: SecurityException) {
            connectionListener?.onError("No permission to connect")
            null
        }
    }

    fun disconnect() {
        connectionListener?.onConnectionStateChanged(ConnectionState.DISCONNECTING)
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: SecurityException) {
        }
        gatt = null
    }

    fun isConnected(): Boolean = gatt != null
}

private fun getStringResource(context: Context, key: String): String {
    val id = context.resources.getIdentifier(key, "string", context.packageName)
    return if (id != 0) context.getString(id) else key
}
