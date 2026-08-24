package com.zksrus.pulse

/**
 * Minimal snapshot of one nearby device as seen from its latest advertising packet.
 * Covers both BLE scan results and bonded/classic Bluetooth devices.
 */
data class DeviceInfo(
    val key: String,
    val address: String,
    val name: String?,
    val rssi: Int,
    val isClassic: Boolean,
    val isBonded: Boolean,
    val lastSeenMs: Long,
    var packetCount: Int = 1,
    var pinned: Boolean = false,
    var online: Boolean = true,
)
