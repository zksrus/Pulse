# Pulse

A small Android app that finds a Bluetooth heart-rate monitor and shows the live heart rate on screen.

Built to work with **any** standard BLE heart-rate monitor (chest straps, armbands, ear clips)
that implements the Bluetooth SIG **Heart Rate Service** — including the HryFine **HR40** used for testing.
It does not hard-code any one device: it scans for heart-rate monitors by service UUID and falls back to
name-based detection for devices that don't advertise the service.

## How it works

1. **Scan** — looks for BLE devices advertising the Heart Rate Service (`0x180D`), with a name-based
   fallback so cheaper monitors (e.g. "HR40") are still discovered.
2. **Connect** — opens a GATT connection and discovers services.
3. **Subscribe** — enables notifications on the Heart Rate Measurement characteristic (`0x2A37`).
4. **Parse** — decodes each notification according to the Heart Rate Service spec:
   the flag byte chooses between `uint8`/`uint16` heart rate, and optional energy-expended and
   RR-interval fields are read when present.
5. **Display** — shows the BPM with a heart icon that beats at the current cadence.

## Screens

- **Device scan screen** — lists discovered heart-rate monitors with name, address and signal strength.
  Tap a device to connect.
- **Heart-rate screen** — large BPM read-out, a pulsing heart, sensor-contact status, and the body
  sensor location (chest / wrist / ...).

## Requirements

- Android 8.0 (API 26) or newer
- A phone with Bluetooth Low Energy
- A BLE heart-rate monitor (HR40, Polar H10, etc.)

## Permissions

| Permission | Purpose |
|---|---|
| `BLUETOOTH_SCAN` | Scan for BLE devices (Android 12+) |
| `BLUETOOTH_CONNECT` | Connect to a device (Android 12+) |
| `BLUETOOTH` / `BLUETOOTH_ADMIN` | Scan/connect on Android 11 and below |
| `ACCESS_FINE_LOCATION` | Required to scan for BLE devices (mandatory on Android 11 and below; kept on 12+ because some OEM stacks still require it) |

**Note:** on Android 11 and below the system **Location service must be switched on** for BLE
scans to return any results — even though the app doesn't use your location for anything else.
The app detects this and shows a "Turn on Location" prompt.

## Build

Open the project in Android Studio (Hedgehog or newer) or build from the command line:

```bash
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Install on a connected device:

```bash
./gradlew installDebug
```

## Project layout

```
app/
  src/main/
    AndroidManifest.xml
    java/com/zksrus/pulse/
      MainActivity.kt                 # entry point, permissions, screen routing
      ble/
        HeartRateUuids.kt             # Heart Rate Service UUIDs
        HeartRateParser.kt            # 0x2A37 byte-format parser
        HeartRateManager.kt           # BLE scan + GATT connection + notifications
      viewmodel/
        PulseViewModel.kt             # UI state (scan results, BPM, connection state)
      ui/
        DeviceScanScreen.kt           # device list
        HeartRateScreen.kt            # BPM display with pulsing heart
  src/test/java/com/zksrus/pulse/ble/
    HeartRateParserTest.kt            # unit tests for the parser
```

## Testing the parser

```bash
./gradlew testDebugUnitTest
```

## License

Provided as-is for personal use.
