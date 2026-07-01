# Morimil App Architecture

## Phase 1

Morimil App starts as a native Android shell.

```text
UI: Jetpack Compose
Language: Kotlin
Repo: Morimil-app only
Genesis repo: not modified
```

## Initial Screens

```text
Chat
Projects
Living Memory
PC Handoff
```

## Future Slots

```text
Local memory:
  Room / SQLite

Voice:
  SpeechRecognizer
  TextToSpeech

Security:
  Android Keystore for future local secrets

Sync:
  Genesis reader
  controlled GitHub sync

Handoff:
  PC command preparation only after approval
```

## Boundary

Phase 1 creates the app foundation only. No real sync, no real memory engine, no voice engine, and no automation.
