# fitzdircon

Bridges iFit2 GlassOS Nordictrack fitness consoles (treadmills, exercise bikes) to Zwift via the Zwift/Wahoo Direct Connect protocol.

## What it does

fitzdircon runs on the iFit2 GlassOS Android device embedded in your Nordictrack fitness console. It advertises the machine over mDNS so Zwift can discover it on your local network (appearing as **iFit via fitzdircon** in Zwift's pairing screen), then accepts a TCP connection and streams live workout telemetry (speed, cadence, power, heart rate, resistance, incline) to Zwift using the FTMS Indoor Bike Data format. Resistance and incline commands sent by Zwift are forwarded back to the console hardware via the GlassOS gRPC interface.

## Requirements

- An iFit fitness console running ifit2 GlassOS (treadmill or exercise bike)
- Zwift running on the same local network
- Android 5.0 (API 21) or higher on the console

## Installation

> **New to this?** See the [Getting Started guide](docs/getting-started.md) for a plain-language, step-by-step walkthrough — no technical experience needed.

Download the latest signed APK from [GitHub Releases](../../releases) and sideload it onto the console:

```bash
adb install fitzdircon-<version>-release.apk
```

Or transfer the APK to the device and install via **Settings → Apps → Install unknown apps**.

The app starts automatically at boot. The main screen shows connection status, local IP address, and a live telemetry readout once a Zwift client connects.

## Building from source

**Prerequisites:** JDK 17, Android SDK (API 34)

```bash
# Debug APK
./gradlew assembleDebug

# Release APK — requires a PKCS#12 keystore
export KEYSTORE_PATH=/path/to/release.p12
export KEYSTORE_PASSWORD=...
export KEY_ALIAS=fitzdircon
export KEY_PASSWORD=...
./gradlew assembleRelease

# Unit tests
./gradlew test

# Lint
./gradlew lint
```

Output APKs land in `app/build/outputs/apk/`.

CI increments the version code automatically via `-PversionCode=<run_number>`. Version name is set in `version.properties`.

## Architecture

Four Gradle modules with a strict dependency order:

| Module | Purpose |
|---|---|
| `:lib:core` | Domain model: `Command`, `Device`, `Telemetry`, `TelemetryHub` |
| `:lib:dircon` | Zwift/Wahoo Direct Connect server — mDNS registration, TCP listener, FTMS/GATT-over-TCP protocol |
| `:lib:ifit2` | iFit GlassOS integration — gRPC telemetry streaming, command transport, mTLS credential extraction |
| `:app` | Startup, platform detection, `MainActivity` status UI, service lifecycle |

**Telemetry flow:** `GrpcTelemetryReader` polls GlassOS gRPC streams while a workout is active and pushes each metric update into `TelemetryHub`, which fans it out to all subscribers. `ZwiftDirectConnectService` is one subscriber — it encodes the data into FTMS Indoor Bike Data packets and writes them to the connected Zwift client.

**Command flow:** Zwift sends FTMS control-point writes over the TCP connection → `DirectConnectProfile` decodes them into `Command` objects → `DirectConnectCommandBridge` → `DeviceController` → `GrpcCommandTransport` → GlassOS gRPC (`setIncline`, `setResistance`, `setSpeed`).

## License

AGPL-3.0. Derived from [QZ / qdomyos-zwift](https://github.com/cagnulein/qdomyos-zwift) and the QZ Companion NordicTrack Treadmill project. See [NOTICE](NOTICE) for attribution.
