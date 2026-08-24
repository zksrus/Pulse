package com.zksrus.pulse.ble

/**
 * Parses the Heart Rate Measurement characteristic value (UUID 0x2A37) as defined by
 * the Bluetooth SIG Heart Rate Service specification. All multi-byte fields are little-endian.
 *
 * Byte layout:
 *  [0] Flags
 *       bit 0: Heart Rate Value Format  (0 = uint8, 1 = uint16)
 *       bit 1-2: Sensor Contact Status
 *       bit 3: Energy Expended Status (0 = not present, 1 = present)
 *       bit 4: RR-Interval bit         (0 = not present, 1 = one or more present)
 *       bits 5-7: Reserved (ignored)
 *  [1..] Heart Rate Measurement Value (uint8 or uint16)
 *  [optional] Energy Expended (uint16, units of kilo Joules)
 *  [optional, repeating] RR-Interval (uint16, units of 1/1024 second)
 */
object HeartRateParser {

    data class Measurement(
        val heartRate: Int,
        val sensorContactSupported: Boolean,
        val sensorContactDetected: Boolean,
        val energyExpended: Int?,
        val rrIntervals: List<Int>,
    )

    fun parse(value: ByteArray): Measurement? {
        if (value.isEmpty()) return null

        val flags = value[0].toInt() and 0xFF
        val hrIs16Bit = (flags and 0x01) != 0
        val sensorContactSupported = (flags and 0x04) != 0
        val sensorContactDetected = (flags and 0x02) != 0
        val energyExpendedPresent = (flags and 0x08) != 0
        val rrPresent = (flags and 0x10) != 0

        var offset = 1
        if (offset >= value.size) return null

        val heartRate: Int = if (hrIs16Bit) {
            if (offset + 1 >= value.size) return null
            val hr = (value[offset].toInt() and 0xFF) or ((value[offset + 1].toInt() and 0xFF) shl 8)
            offset += 2
            hr
        } else {
            val hr = value[offset].toInt() and 0xFF
            offset += 1
            hr
        }

        var energyExpended: Int? = null
        if (energyExpendedPresent && offset + 1 < value.size) {
            energyExpended = (value[offset].toInt() and 0xFF) or ((value[offset + 1].toInt() and 0xFF) shl 8)
            offset += 2
        }

        val rrIntervals = mutableListOf<Int>()
        if (rrPresent) {
            while (offset + 1 < value.size) {
                val rr = (value[offset].toInt() and 0xFF) or ((value[offset + 1].toInt() and 0xFF) shl 8)
                rrIntervals.add(rr)
                offset += 2
            }
        }

        return Measurement(
            heartRate = heartRate,
            sensorContactSupported = sensorContactSupported,
            sensorContactDetected = sensorContactDetected,
            energyExpended = energyExpended,
            rrIntervals = rrIntervals,
        )
    }
}
