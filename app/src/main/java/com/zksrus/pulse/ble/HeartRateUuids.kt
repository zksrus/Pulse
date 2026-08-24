package com.zksrus.pulse.ble

/** Standard Bluetooth SIG UUIDs for the Heart Rate Service. */
object HeartRateUuids {
    /** Heart Rate Service. */
    val SERVICE: java.util.UUID = java.util.UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")

    /** Heart Rate Measurement characteristic (notifiable). */
    val MEASUREMENT: java.util.UUID = java.util.UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")

    /** Body Sensor Location characteristic (readable). */
    val BODY_SENSOR_LOCATION: java.util.UUID = java.util.UUID.fromString("00002a38-0000-1000-8000-00805f9b34fb")

    /** Heart Rate Control Point characteristic (writable). */
    val CONTROL_POINT: java.util.UUID = java.util.UUID.fromString("00002a39-0000-1000-8000-00805f9b34fb")

    /** Client Characteristic Configuration Descriptor — write 0x0001 to enable notifications. */
    val CLIENT_CONFIG: java.util.UUID = java.util.UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}
