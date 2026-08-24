# Pulse

A tiny Android app that lists every nearby Bluetooth device — both BLE advertising
peripherals and bonded classic Bluetooth devices — in real time.

## What it does

- Continuously scans BLE advertisements with the low-latency scan mode.
- Shows device name, MAC address, RSSI and packet count for each device.
- Seeds the list with already-bonded classic Bluetooth devices so it is never empty.
- Prunes devices that have not been seen for ~12 seconds.
- Handles runtime permissions (BLUETOOTH_SCAN / BLUETOOTH_CONNECT on API 31+, location
  on older releases) and prompts to enable Bluetooth when it is off.

The scanner is modeled on the "Поиск весов" screen of the
[Scales](https://github.com/zksrus/Scales) app, trimmed down to just listing devices.

## Tech

Kotlin, Jetpack Compose, Material 3, minSdk 26, targetSdk 34.

## Build

```sh
./gradlew assembleDebug
```
