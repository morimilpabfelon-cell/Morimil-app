# Morimil App

Morimil App is the native Android application for the Morimil living-memory companion system.

## Phase 1 Goal

Build the native Android foundation only.

```text
Included:
  Android native skeleton
  Kotlin + Jetpack Compose UI shell
  mock screens for Chat, Projects, Living Memory, and PC Handoff
  placeholders for future memory, voice, Genesis sync, GitHub sync, and PC handoff

Not included yet:
  real local database
  voice capture
  text-to-speech
  GitHub write integration
  Genesis sync
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
  Phase 1 does not modify the Morimil Genesis repository.
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
