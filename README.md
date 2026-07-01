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

## First Local Build

Open this repository in Android Studio and sync Gradle.

Then run:

```powershell
.\gradlew.bat :app:assembleDebug
```

If the Gradle wrapper is not generated yet, open the folder in Android Studio and let the IDE sync/create the local build environment.
