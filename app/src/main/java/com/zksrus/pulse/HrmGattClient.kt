package com.zksrus.pulse

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.UUID

/**
 * Latest live readings pulled from a connected heart-rate monitor over GATT.
 * Fields default to "unknown" until the first notification/read lands.
 */
data class HrmData(
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val bpm: Int? = null,
    val sensorContact: SensorContact = SensorContact.NOT_SUPPORTED,
    val bodyLocation: String? = null,
    val energyExpended: Int? = null,
    val rrIntervals: List<Int> = emptyList(),
    val batteryPercent: Int? = null,
    val manufacturer: String? = null,
    val modelNumber: String? = null,
    val firmwareRevision: String? = null,
    val serialNumber: String? = null,
    val hardwareRevision: String? = null,
)

enum class SensorContact { NOT_SUPPORTED, NO_CONTACT, CONTACT }

/**
 * Connects to a BLE heart-rate monitor, enables notifications on the Heart Rate
 * Measurement characteristic and reads Battery + Device Information services,
 * streaming every value back through [onData].
 *
 * Heart Rate Measurement payload (GATT 0x2A37) layout:
 *   byte 0 = flags
 *     bit 0  HR uint8(0)/uint16(1)
 *     bits 1-2 sensor contact support 00=none 01=none 10=support 11=support+status
 *     bit 3  sensor contact detected (if supported)
 *     bit 4  energy expended present
 *     bit 5  RR-interval(s) present
 *   then HR value (1 or 2 bytes), optional Energy Expended (uint16), optional RR list.
 */
class HrmGattClient(
    private val device: BluetoothDevice,
    private val onData: (HrmData) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null

    @Volatile private var current = HrmData(connecting = true)

    /** Reads queued one at a time: Android allows only one outstanding read per gatt. */
    private val pendingReads = ArrayDeque<BluetoothGattCharacteristic>()

    fun connect() {
        current = current.copy(connecting = true, connected = false)
        emit()
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(null, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(null, false, gattCallback)
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        pendingReads.clear()
        gatt?.let {
            try {
                it.disconnect()
            } catch (_: Exception) {
            }
            try {
                it.close()
            } catch (_: Exception) {
            }
        }
        gatt = null
        current = HrmData()
        emit()
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    current = current.copy(connected = true, connecting = false)
                    emit()
                    g.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    pendingReads.clear()
                    current = current.copy(connected = false, connecting = false)
                    emit()
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            // Heart Rate Measurement: enable notifications.
            hrMeasurementChar(g)?.let { ch ->
                g.setCharacteristicNotification(ch, true)
                cccd(ch)?.let { d ->
                    d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    g.writeDescriptor(d)
                }
            }
            // Queue all read-only characteristics; processed one at a time.
            listOfNotNull(
                bodySensorLocationChar(g),
                char(g, DIS_SERVICE, MODEL_NUMBER_UUID),
                char(g, DIS_SERVICE, SERIAL_NUMBER_UUID),
                char(g, DIS_SERVICE, FIRMWARE_UUID),
                char(g, DIS_SERVICE, HARDWARE_UUID),
                char(g, DIS_SERVICE, MANUFACTURER_UUID),
                batteryLevelChar(g),
            ).forEach { pendingReads.add(it) }
            readNext(g)
        }

        /**
         * Characteristic read result. The status-bearing signature is the only one
         * implemented: on API 33+ the platform's new value-based callback forwards to
         * this with GATT_SUCCESS and populates characteristic.value, and on older
         * API levels it is called directly.
         */
        @Deprecated("Single read path for all API levels")
        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            @Suppress("DEPRECATION")
            handleRead(characteristic.uuid, characteristic.value ?: ByteArray(0))
            readNext(g)
        }

        /**
         * Heart Rate Measurement notification. Same forward-compat approach: the legacy
         * signature receives the value via characteristic.value on every API level.
         */
        @Deprecated("Single notify path for all API levels")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            @Suppress("DEPRECATION")
            val bytes = characteristic.value ?: return
            if (characteristic.uuid == HR_MEASUREMENT_UUID) parseHrMeasurement(bytes)
        }
    }

    @SuppressLint("MissingPermission")
    private fun readNext(g: BluetoothGatt) {
        val next = pendingReads.removeFirstOrNull() ?: return
        try {
            g.readCharacteristic(next)
        } catch (_: Exception) {
            readNext(g)
        }
    }

    private fun handleRead(uuid: UUID, value: ByteArray) {
        if (value.isEmpty()) return
        when (uuid) {
            BODY_SENSOR_LOCATION_UUID -> current = current.copy(
                bodyLocation = bodyLocationName(value[0].toInt() and 0xFF)
            )
            BATTERY_LEVEL_UUID -> current = current.copy(
                batteryPercent = value[0].toInt() and 0xFF
            )
            MODEL_NUMBER_UUID -> current = current.copy(modelNumber = String(value).trim().nullIfBlank())
            SERIAL_NUMBER_UUID -> current = current.copy(serialNumber = String(value).trim().nullIfBlank())
            FIRMWARE_UUID -> current = current.copy(firmwareRevision = String(value).trim().nullIfBlank())
            HARDWARE_UUID -> current = current.copy(hardwareRevision = String(value).trim().nullIfBlank())
            MANUFACTURER_UUID -> current = current.copy(manufacturer = String(value).trim().nullIfBlank())
        }
        emit()
    }

    private fun parseHrMeasurement(data: ByteArray) {
        if (data.isEmpty()) return
        val flags = data[0].toInt() and 0xFF
        val is16Bit = (flags and 0x01) != 0
        val sensorContactSupported = (flags and 0x04) != 0
        val sensorContactDetected = (flags and 0x08) != 0
        val energyPresent = (flags and 0x10) != 0
        val rrPresent = (flags and 0x20) != 0

        var index = 1
        val bpm: Int = if (is16Bit) {
            val v = ((data[index].toInt() and 0xFF) shl 8) or (data[index + 1].toInt() and 0xFF)
            index += 2
            v
        } else {
            val v = data[index].toInt() and 0xFF
            index += 1
            v
        }

        var energy: Int? = null
        if (energyPresent && index + 1 < data.size) {
            energy = ((data[index].toInt() and 0xFF) shl 8) or (data[index + 1].toInt() and 0xFF)
            index += 2
        }

        val rr = ArrayList<Int>()
        if (rrPresent) {
            while (index + 1 < data.size) {
                rr.add(((data[index].toInt() and 0xFF) shl 8) or (data[index + 1].toInt() and 0xFF))
                index += 2
            }
        }

        val contact = when {
            !sensorContactSupported -> SensorContact.NOT_SUPPORTED
            sensorContactDetected -> SensorContact.CONTACT
            else -> SensorContact.NO_CONTACT
        }

        current = current.copy(
            bpm = bpm,
            sensorContact = contact,
            energyExpended = energy,
            rrIntervals = rr,
        )
        emit()
    }

    private fun emit() {
        val snapshot = current
        handler.post { onData(snapshot) }
    }

    private fun bodyLocationName(v: Int): String = when (v) {
        0 -> "Other"
        1 -> "Chest"
        2 -> "Wrist"
        3 -> "Finger"
        4 -> "Hand"
        5 -> "Ear lobe"
        6 -> "Foot"
        else -> "Unknown"
    }

    private fun String.nullIfBlank(): String? = takeIf { isNotBlank() }

    private fun hrMeasurementChar(g: BluetoothGatt) =
        char(g, HR_SERVICE, HR_MEASUREMENT_UUID)

    private fun bodySensorLocationChar(g: BluetoothGatt) =
        char(g, HR_SERVICE, BODY_SENSOR_LOCATION_UUID)

    private fun batteryLevelChar(g: BluetoothGatt) =
        char(g, BATTERY_SERVICE, BATTERY_LEVEL_UUID)

    private fun char(g: BluetoothGatt, service: UUID, characteristic: UUID): BluetoothGattCharacteristic? =
        g.getService(service)?.getCharacteristic(characteristic)

    private fun cccd(ch: BluetoothGattCharacteristic): BluetoothGattDescriptor? =
        ch.getDescriptor(CCCD_UUID)

    companion object {
        private val HR_SERVICE: UUID = UUID.fromString("0000180D-0000-1000-8000-00805F9B34FB")
        private val BATTERY_SERVICE: UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")
        private val DIS_SERVICE: UUID = UUID.fromString("0000180A-0000-1000-8000-00805F9B34FB")

        private val HR_MEASUREMENT_UUID: UUID = UUID.fromString("00002A37-0000-1000-8000-00805F9B34FB")
        private val BODY_SENSOR_LOCATION_UUID: UUID = UUID.fromString("00002A38-0000-1000-8000-00805F9B34FB")
        private val BATTERY_LEVEL_UUID: UUID = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")
        private val MODEL_NUMBER_UUID: UUID = UUID.fromString("00002A24-0000-1000-8000-00805F9B34FB")
        private val SERIAL_NUMBER_UUID: UUID = UUID.fromString("00002A25-0000-1000-8000-00805F9B34FB")
        private val FIRMWARE_UUID: UUID = UUID.fromString("00002A26-0000-1000-8000-00805F9B34FB")
        private val HARDWARE_UUID: UUID = UUID.fromString("00002A27-0000-1000-8000-00805F9B34FB")
        private val MANUFACTURER_UUID: UUID = UUID.fromString("00002A29-0000-1000-8000-00805F9B34FB")
        private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
    }
}
