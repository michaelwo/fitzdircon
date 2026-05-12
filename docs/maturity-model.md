# fitzdircon App Maturity Model

Eight dimensions, each scored 0–3. The model is specific to this project's constraints:
an Android protocol bridge that connects iFit GlassOS fitness consoles to Zwift via mTLS
gRPC on one side and the Zwift Direct Connect TCP protocol on the other.

---

## Scoring Scale

| Score | Meaning |
|-------|---------|
| **0** | Missing or broken |
| **1** | Ad-hoc / works but fragile |
| **2** | Defined, documented, repeatable |
| **3** | Measured, automated, self-correcting |

---

## Dimensions

### 1. Test Coverage

Does the code have tests that catch regressions before hardware access is needed?

| Score | Criteria |
|-------|----------|
| 0 | No tests |
| 1 | Some unit tests for isolated helpers |
| 2 | Core protocol pipeline covered: packet encoding/decoding, GATT service emulation, telemetry state accumulation, command delegation; edge cases (frame boundaries, unknown characteristic UUIDs, zero-value telemetry) covered |
| 3 | Full round-trip tests: iFit telemetry values → FTMS notification bytes (all metrics); FTMS control-point write → gRPC command call (all command types); GrpcCredentials tested against a fixture APK resource; TelemetryHub concurrency exercised |

### 2. Observability

Can you tell what the bridge is doing and why, during and after a ride?

| Score | Criteria |
|-------|----------|
| 0 | No logging |
| 1 | Ad-hoc `Log.i` calls, unstructured |
| 2 | Structured log tags (`FZ:DirCon`, `FZ:Dispatch`, `FZ:Platform`); connection state changes (mDNS registration, TCP accept/disconnect, gRPC stream start/stop) logged with reasons; telemetry values logged at source and at FTMS encoding |
| 3 | Post-ride log analysis script: extracts ride duration, telemetry event count, command count, connection events, and gRPC credential discovery path; anomalies (missing telemetry, repeated reconnects) flagged automatically |

### 3. Protocol Compliance

Does the Direct Connect implementation correctly handle what Zwift expects?

| Score | Criteria |
|-------|----------|
| 0 | No compliance validation |
| 1 | Manual Zwift pairing test on real hardware |
| 2 | Packet encode/decode roundtrip tests for all frame types; FTMS and CSC service/characteristic table verified in tests; GATT control-point response codes validated; Python smoke test exercises the full handshake |
| 3 | Automated compliance test suite (extending `scripts/`) acts as a Zwift client: pairs, subscribes to all characteristics, issues all FTMS control-point commands, and verifies each response; tested against every code change |

### 4. Credential Security

Can the app reliably discover and validate the GlassOS mTLS credentials?

| Score | Criteria |
|-------|----------|
| 0 | Credentials hardcoded or discovery not implemented |
| 1 | Discovery implemented but untested; failure silently breaks the gRPC connection with no actionable error |
| 2 | `GrpcCredentials.load()` tested against a fixture `resources.arsc` containing known PEM blobs disguised as JPEG; CA/client cert/key identification logic verified; version-based cache invalidation tested; failure path logs at error level with cert details |
| 3 | Automated test exercises full discovery cycle including multiple candidate packages; cert chain validation verified; rotation scenario (APK version bump) tested; credential expiry detected at startup |

### 5. Build Reproducibility

Does every commit produce the same artifact, automatically?

| Score | Criteria |
|-------|----------|
| 0 | Manual builds, version bumped by hand in multiple files |
| 1 | Gradle builds locally; version managed but inconsistently |
| 2 | CI builds on every push; version sourced from single `version.properties`; `versionCode` from run number; signed release APK published automatically on tag |
| 3 | All GitHub Actions refs pinned to SHA digests (no mutable tags); build inputs fully declared; release notes auto-generated from commits since last tag |

### 6. Failure Resilience

Does the bridge recover gracefully when gRPC, mDNS, or the TCP connection fails?

| Score | Criteria |
|-------|----------|
| 0 | Any failure = silent hang or crash |
| 1 | Error logged but no recovery; restart required |
| 2 | TCP client disconnect → service loops back to accept without restart; gRPC stream errors logged and re-subscribed; mDNS re-registration attempted on conflict; all failure paths log at error level |
| 3 | Per-subsystem graceful degradation: Zwift side fails → gRPC streams continue running, ready for next pairing; gRPC side fails → Zwift connection held with stale state and reconnect attempted; no uncaught exceptions reach the user |

### 7. Telemetry Fidelity

Are the values Zwift receives accurate representations of what iFit is reporting?

| Score | Criteria |
|-------|----------|
| 0 | No translation validation; unknown if values are meaningful |
| 1 | Manual observation (watch the Zwift display during a ride) |
| 2 | Unit tests verify FTMS field encoding from known iFit values for every metric (speed, cadence, power, incline, heart rate, resistance); physical unit conversions documented with formula (e.g. km/h × 100 → FTMS speed field, grade % × 100 → FTMS inclination field) |
| 3 | Logged comparison of raw iFit gRPC values vs. FTMS-encoded values from a real workout; known-good reference values captured; any encoding deviation > 0.5 LSB flagged |

### 8. Documentation

Can a new contributor understand and extend the bridge without reading every file?

| Score | Criteria |
|-------|----------|
| 0 | No docs |
| 1 | README with basic setup; CLAUDE.md with architecture notes |
| 2 | Architecture doc (data flow diagram, class roles for each module); FTMS/GATT service reference table (UUIDs, field formats); deployment runbook end-to-end verified (all commands confirmed to work) |
| 3 | Docs kept adjacent to the code they describe; FTMS field reference verified or generated against `DirectConnectProfile` source; protocol compliance table cross-linked to corresponding test |

---

## Current Scores (as of 2026-05-12)

| Dimension | Score | Notes |
|-----------|-------|-------|
| Test Coverage | 2 | DirectConnectPacketTest (22), DirectConnectProfileTest (24), DirectConnectTrainerStateTest (7), DirectConnectServiceInfoTest (1), DeviceControllerTest (2), TelemetryHubTest (1), GrpcDeviceCommandTest (5) — 62 tests total; core protocol and command delegation covered; missing: credential discovery, service lifecycle, TelemetryHub concurrency, IFitPlatform detection |
| Observability | 2 | Structured FZ:* tags; gRPC stream start/stop (FZ:Dispatch), workout state transitions, telemetry values at source (FZ:Dispatch `telemetry metric=value`) and at FTMS encoding point (FZ:DirCon `ftms Type=value`), stream errors all logged; `scripts/analyze-ride.sh` extracts ride duration, telemetry/command counts, connection timeline, and flags anomalies (missing telemetry, repeated reconnects, stream errors, credential failures) |
| Protocol Compliance | 2 | Packet and profile unit tests cover encode/decode for all known frame types and FTMS characteristics; Python smoke test in `scripts/` exercises the full handshake; no automated compliance suite that acts as a Zwift client |
| Credential Security | 3 | `GrpcCredentialsTest` (8 tests): full discovery cycle via `discoverFromCandidateContent` (multiple candidate packages — first fails, second succeeds), cert chain validation (unrelated cert rejected), rotation scenario via `fromCache` (version match → cache hit, version bump → miss), expiry detection via `checkCertificateValidity` called from `buildSslContext`; `parsePrivateKey` migrated from `android.util.Base64` to `java.util.Base64` for pure-Java testability |
| Build Reproducibility | 3 | CI on every push; version from `version.properties`; versionCode from `github.run_number`; signed release APK on tag; release notes auto-generated; all GitHub Actions refs pinned to SHA digests; runner pinned to `ubuntu-24.04` |
| Failure Resilience | 2 | TCP accept loop already re-accepts after client disconnect without restart; gRPC metric stream observers re-subscribe on error if workout is still active (`subscribeIncline` etc.); gRPC workout state stream re-subscribes on error; mDNS registration retried once on `onRegistrationFailed` via `mDnsRetried` guard; all failure paths log at error level |
| Telemetry Fidelity | 2 | `DirectConnectProfileTest` verifies FTMS encoding from known telemetry values; unit conversion formulas not independently documented; no logged commanded-vs-received comparison |
| Documentation | 2 | README.md, CLAUDE.md (architecture + startup sequence), deploy-s22i-adb.md (8-step runbook); no standalone architecture diagram doc; runbooks not end-to-end verified |

**Overall: 18 / 24**

---

## Next Step Per Dimension

| Dimension | Next action to reach score+1 |
|-----------|------------------------------|
| Test Coverage | Add `GrpcCredentialsTest` with a fixture `resources.arsc` containing known PEM blobs; add round-trip test: construct `Telemetry` values, push through `TelemetryHub`, verify FTMS bytes out |
| Observability | ✓ Done — gRPC stream start/stop, workout state, telemetry at source and FTMS point all logged; `scripts/analyze-ride.sh` reports event timeline and anomalies |
| Protocol Compliance | Extend `scripts/` smoke test to act as a full Zwift client: subscribe to all FTMS/CSC characteristics and issue each control-point command type, asserting correct response codes |
| Credential Security | ✓ Done — `GrpcCredentialsTest` (8 tests): full discovery cycle, cert chain validation, rotation via `fromCache`, expiry detection via `checkCertificateValidity` at `buildSslContext` |
| Build Reproducibility | ✓ Done — all Actions refs pinned to SHA digests; runner pinned to `ubuntu-24.04`; release notes auto-generated via `generate_release_notes: true` |
| Failure Resilience | ✓ Done — TCP accept loop already reconnects; gRPC metric streams re-subscribe per-metric on error; workout state stream re-subscribes; mDNS retried on failure; to reach 3: add per-subsystem graceful degradation (hold Zwift connection with stale state while gRPC reconnects, add exponential backoff) |
| Telemetry Fidelity | Document each FTMS field encoding as a table in `DirectConnectProfile` (source unit → wire format → scale factor); add `@see` links from field assignments to the corresponding `DirectConnectProfileTest` assertion |
| Documentation | Create `docs/architecture.md` with a data-flow diagram and class-role table for each module; verify `deploy-s22i-adb.md` runbook commands execute correctly end-to-end on the S22i |
