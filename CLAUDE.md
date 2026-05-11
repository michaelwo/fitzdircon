# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

`fitzdircon` is an Android app that runs on iFit GlassOS fitness consoles (treadmills, bikes). It bridges the iFit equipment to Zwift by implementing Zwift's "Direct Connect" protocol — advertising the machine over mDNS, accepting a TCP connection from Zwift, streaming live workout telemetry to it, and forwarding resistance/incline/speed commands back to the equipment via gRPC.

## Build commands

```bash
# Debug APK
./gradlew assembleDebug

# Release APK (requires env vars: KEYSTORE_PATH, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD)
./gradlew assembleRelease

# All unit tests
./gradlew test

# Single module tests
./gradlew :lib:core:test
./gradlew :lib:dircon:test
./gradlew :lib:ifit2:test

# Single test class
./gradlew :lib:dircon:test --tests "org.fitzdircon.dircon.DirectConnectPacketTest"

# Lint
./gradlew lint
```

CI builds pass `-PversionCode=<n>` to Gradle; this sets `BuildConfig.IS_CI_BUILD = true` and populates `VERSION_CODE`. Version name comes from `version.properties`.

## Module layout

| Module | Type | Purpose |
|---|---|---|
| `:app` | Android app | Startup, platform detection, `MainActivity` status UI, service lifecycle |
| `:lib:core` | Android library (pure Java) | Domain model: `Command`, `Device`, `DeviceController`, `Telemetry` types, `TelemetryHub` |
| `:lib:dircon` | Android library | Zwift Direct Connect server: mDNS + TCP, FTMS/GATT-over-TCP protocol |
| `:lib:ifit2` | Android library | iFit GlassOS gRPC integration: telemetry streaming and command transport |

Dependency graph: `app` → `dircon` + `ifit2`; both `dircon` and `ifit2` → `core`.

## Architecture

### Startup sequence (`MainActivity.onCreate`)

1. `IFitPlatform.detect()` — tries to open a gRPC channel to `localhost:54321` (the GlassOS service). If `GrpcCredentials.load()` succeeds and `ConsoleService.getKnownConsoleInfo` responds, the platform is marked available and the machine class (BIKE vs TREADMILL) is determined.
2. If available: `GrpcTelemetryReader` is created and registered in `TelemetryHub.shared()`; a `GrpcBikeDevice` or `GrpcTreadmillDevice` is created and wrapped in `DeviceController`; `DirectConnectCommandBridge.setSink(controller::enqueueCommand)` wires commands from the Direct Connect service back to the device.
3. `ZwiftDirectConnectService` is started (unless disabled in prefs).

### Telemetry flow

`GrpcTelemetryReader` subscribes to GlassOS gRPC streaming services (incline, resistance, speed, RPM, watts, heart rate) only while a workout is active (`WorkoutState.WORKOUT_STATE_RUNNING`). Each metric update is pushed to `TelemetryHub.shared()`, which fans it out to all subscribers. `ZwiftDirectConnectService` is one subscriber — it updates `DirectConnectTrainerState` and writes FTMS Indoor Bike Data notifications to the connected Zwift client.

### Command flow

Zwift sends FTMS control-point writes over the TCP connection → `DirectConnectProfile.process()` decodes them into `Command` objects (e.g. `ResistanceCommand`, `InclineCommand`) → `DirectConnectCommandBridge.submit()` → `DeviceController.enqueueCommand()` → `GrpcCommandTransport.apply()` → GlassOS gRPC (`setIncline`, `setResistance`, `setSpeed`).

### Direct Connect protocol (`lib/dircon`)

`ZwiftDirectConnectService` is an Android `Service` that registers an mDNS service via `NsdManager` and accepts exactly one TCP client at a time. The wire format is defined in `DirectConnectPacket`. `DirectConnectProfile` emulates GATT services/characteristics (FTMS `0x1826`, Cycling Power `0x1818`, CSC `0x1816`) over that TCP stream. A 1-second heartbeat publishes periodic measurements (power, CSC) alongside real-time telemetry notifications.

### GlassOS credential discovery (`GrpcCredentials`)

GlassOS mTLS credentials are not shipped with this app. They are discovered at runtime by scanning the `resources.arsc` of the `com.ifit.rivendell` or `com.ifit.glassos_service` APK for `img_icon_*` raw resources. These resources are PEM-encoded certificates and keys stored with JPEG markers (FFD8/FFD9) to disguise them. `GrpcCredentials` identifies the CA cert (self-signed), client cert (signed by CA), and the RSA private key (matched by modulus), then builds an `SSLContext` for mTLS. Results are cached in `SharedPreferences` keyed by package version.

### Log tags

All `android.util.Log` calls use `FZ:`-prefixed tags: `FZ:Main`, `FZ:DirCon`, `FZ:Platform`, `FZ:Dispatch`.
