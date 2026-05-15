# AGENTS.md

Guidance for Codex and other coding agents working in this repository.

## Project

`fitzdircon` is an Android app for iFit GlassOS fitness consoles. It bridges telemetry and control commands between the console and Zwift using the Zwift/Wahoo Direct Connect protocol.

## Environment

This is an Android/Gradle project. Full builds require:

- JDK 17
- Android SDK with API 34
- `ANDROID_HOME` set, or `sdk.dir` configured in `local.properties`

If those prerequisites are missing, do not run Gradle just to make documentation-only or metadata-only changes.

## Validation

Use the narrowest check that matches the files changed:

- Markdown/docs-only changes: no build is required.
- Java or Android resource changes: run the most specific relevant Gradle task.
- App-wide behavior changes: run `./gradlew assembleDebug` and relevant tests when the Android SDK is available.

Common commands:

```bash
./gradlew assembleDebug
./gradlew test
./gradlew :lib:core:test
./gradlew :lib:dircon:test
./gradlew :lib:ifit2:test
./gradlew lint
```

In Codex Cloud, if `./gradlew` cannot download the Gradle distribution because the
environment proxy blocks it, use the preinstalled `gradle` command instead:

```bash
gradle assembleDebug
gradle test
```

Release builds require signing environment variables and should not be used as the default validation path.
