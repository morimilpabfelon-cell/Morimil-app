# Morimil App

Morimil App is the native Android application for the Morimil living-memory companion system.

## Current Goal

Build the native Android body for Morimil: local identity, local memory,
memory organs, and a neutral reasoning motor layer.

```text
Included:
  Android native app
  Kotlin + Jetpack Compose UI
  local Room/SQLite memory
  hash-linked memory events
  memory organs and recall schedule runtime
  voice controls
  bundled Genesis seed reader/verifier
  neutral Motor/API screen with up to 10 reasoning API slots
  model discovery through compatible model catalogs

Not included yet:
  PC executor automation
  production release
```

## Repository Boundary

```text
Morimil-app:
  mobile application repo

Morimil:
  Genesis Block / audited baseline repo

Rule:
  Morimil-app does not mutate the Morimil Genesis repository.
  Reasoning APIs are transport only; memory and identity remain local.
```

## Android Studio Build Setup

This project is intended to run from Android Studio with:

```text
JDK: 17
Android Gradle Plugin: 8.6.1
Gradle wrapper: 8.7
compileSdk: 35
targetSdk: 35
```

Open this repository in Android Studio, trust the project, and run Gradle Sync.

If Android Studio cannot find the Android SDK, create a local `local.properties` file in the repository root. This file is ignored by Git and must not be committed.

Example for Windows:

```properties
sdk.dir=C\:\\Users\\YOUR_WINDOWS_USER\\AppData\\Local\\Android\\Sdk
```

Then run:

```powershell
.\gradlew.bat --version
.\gradlew.bat tasks
.\gradlew.bat :app:assembleDebug
```

After the build succeeds, select an emulator or a USB-connected Android phone in Android Studio and click Run.
