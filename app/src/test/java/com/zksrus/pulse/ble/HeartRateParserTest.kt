package com.zksrus.pulse.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeartRateParserTest {

    @Test
    fun parsesUint8HeartRateWithNoFlags() {
        // flags=0x00 (uint8 HR, sensor contact not supported, no EE, no RR), HR=72
        val data = byteArrayOf(0x00, 72)
        val m = HeartRateParser.parse(data)
        assertEquals(72, m?.heartRate)
        assertEquals(false, m?.sensorContactSupported)
        assertEquals(false, m?.sensorContactDetected)
        assertEquals(null, m?.energyExpended)
        assertEquals(emptyList<Int>(), m?.rrIntervals)
    }

    @Test
    fun parsesUint16HeartRate() {
        // flags=0x01 (uint16 HR), HR=250 (0xFA 0x00 little-endian)
        val data = byteArrayOf(0x01, 0xFA.toByte(), 0x00)
        val m = HeartRateParser.parse(data)
        assertEquals(250, m?.heartRate)
    }

    @Test
    fun parsesUint16HeartRateAbove255() {
        // HR can exceed 255 for some sensors; uint16 value 300 = 0x2C 0x01
        val data = byteArrayOf(0x01, 0x2C, 0x01)
        val m = HeartRateParser.parse(data)
        assertEquals(300, m?.heartRate)
    }

    @Test
    fun parsesSensorContactFlags() {
        // bit1(sensor contact detected) + bit2(sensor contact supported) = 0x06
        // HR=80 (uint8)
        val data = byteArrayOf(0x06, 80)
        val m = HeartRateParser.parse(data)
        assertEquals(80, m?.heartRate)
        assertEquals(true, m?.sensorContactSupported)
        assertEquals(true, m?.sensorContactDetected)
    }

    @Test
    fun parsesSensorContactSupportedNotDetected() {
        // bit2 set (supported), bit1 clear (not detected) = 0x04
        val data = byteArrayOf(0x04, 80)
        val m = HeartRateParser.parse(data)
        assertEquals(true, m?.sensorContactSupported)
        assertEquals(false, m?.sensorContactDetected)
    }

    @Test
    fun parsesEnergyExpended() {
        // flags: uint8 HR + EE present (bit3=0x08) => 0x08, HR=90, EE=100 kJ (0x64 0x00)
        val data = byteArrayOf(0x08, 90, 0x64, 0x00)
        val m = HeartRateParser.parse(data)
        assertEquals(90, m?.heartRate)
        assertEquals(100, m?.energyExpended)
    }

    @Test
    fun parsesRrIntervals() {
        // flags: uint8 HR + RR present (bit4=0x10) => 0x10
        // HR=75, RR=1024 (1s) = 0x00 0x04, RR=900 = 0x84 0x03
        val data = byteArrayOf(0x10, 75, 0x00, 0x04, 0x84.toByte(), 0x03)
        val m = HeartRateParser.parse(data)
        assertEquals(75, m?.heartRate)
        assertEquals(listOf(1024, 900), m?.rrIntervals)
    }

    @Test
    fun parsesFullPacket() {
        // flags: uint16 HR + sensor contact supported+detected + EE + RR
        // bits: 0 (uint16) | 0x02 | 0x04 | 0x08 | 0x10 = 0x1F
        // HR=140 (0x8C 0x00), EE=50 (0x32 0x00), RR=800 (0x20 0x03)
        val data = byteArrayOf(
            0x1F,
            0x8C.toByte(), 0x00, // HR
            0x32, 0x00,          // EE
            0x20, 0x03,          // RR
        )
        val m = HeartRateParser.parse(data)
        assertEquals(140, m?.heartRate)
        assertEquals(true, m?.sensorContactSupported)
        assertEquals(true, m?.sensorContactDetected)
        assertEquals(50, m?.energyExpended)
        assertEquals(listOf(800), m?.rrIntervals)
    }

    @Test
    fun returnsNullForEmptyPacket() {
        assertNull(HeartRateParser.parse(byteArrayOf()))
    }

    @Test
    fun returnsNullForTruncatedPacket() {
        // flags only, no HR byte
        assertNull(HeartRateParser.parse(byteArrayOf(0x00)))
        // flags uint16, only one HR byte
        assertNull(HeartRateParser.parse(byteArrayOf(0x01, 0x01)))
    }
}
