# Android Standards Baseline

This document defines the baseline Morimil-app should satisfy before feature expansion.

## Build Toolchain

Required baseline:

```text
Android Studio: stable channel compatible with AGP 8.6.1
Android Gradle Plugin: 8.6.1
Gradle wrapper: 8.7
JDK: 17
compileSdk: 35
targetSdk: 35
minSdk: 26
```

Current status:

```text
PASS: Gradle wrapper is pinned to 8.7.
PASS: AGP is pinned to 8.6.1.
PASS: Kotlin is pinned to 2.0.21.
PASS: Java toolchain resolver is configured.
PASS: Gradle Java auto-detect and auto-download are enabled.
PASS: app module compileOptions target Java 17.
LOCAL ONLY: local.properties must define sdk.dir on each developer machine.
```

## Android Studio Local Setup

Do not commit `local.properties`.

Each Windows development machine should create a local file like:

```properties
sdk.dir=C\:\\Users\\YOUR_WINDOWS_USER\\AppData\\Local\\Android\\Sdk
```

Validation commands:

```powershell
.\gradlew.bat --version
.\gradlew.bat tasks
.\gradlew.bat :app:assembleDebug
```

## Architecture Baseline

Target architecture:

```text
UI layer: Compose screens, state rendering, user events only.
State holder: ViewModel exposes UI state and receives UI events.
Data layer: repositories expose application data and business operations.
Domain layer: optional; add use cases only when logic is reused or complex.
Data flow: unidirectional data flow.
```

Current status:

```text
PASS: Kotlin + Compose app shell exists.
PASS: ViewModel exposes StateFlow-backed state.
PASS: Repository layer exists for local memory.
NEEDS HARDENING: MorimilApp.kt is large and mixes many screen concerns in one file.
NEEDS HARDENING: ViewModel manually creates infrastructure dependencies.
NEEDS HARDENING: no automated build/test gate is active yet.
NEEDS HARDENING: no unit or UI test baseline is present yet.
```

## Quality Baseline

Before production release, the app should satisfy:

```text
Back navigation works consistently.
State survives rotation/background/foreground transitions where required.
Light and dark theme readability is verified.
Runtime permissions are explained and requested only when needed.
Sensitive keys are never displayed as plain normal text after entry.
No local secrets are committed.
Debug build compiles from a clean checkout.
```

## Next Gate

The next safe gate is local validation on the developer machine:

```text
1. Pull latest main.
2. Confirm settings.gradle.kts contains the Foojay resolver plugin.
3. Confirm gradle-wrapper.properties uses Gradle 8.7.
4. Confirm local.properties points to the Android SDK.
5. Run Gradle Sync.
6. Run :app:assembleDebug.
```

Optional but recommended after explicit approval:

```text
Add a GitHub Actions Android build workflow for push and pull_request.
```
