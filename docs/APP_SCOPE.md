# Morimil App Scope

## Phase 5B Scope

```text
Add controlled read-only GitHub status preview.
Use stored Keystore-backed credential.
Perform only explicit user-triggered GET repo metadata.
```

## Explicit Non-Scope

```text
No repo creation.
No file upload.
No PR creation.
No merge.
No delete.
No workflow dispatch.
No background sync.
No autonomous mutation.
No PC command execution from the app.
No production release.
```

## Repository Boundary

```text
Morimil-app can preview its own GitHub repository metadata.
Morimil Genesis repository remains read-only and is not modified.
```
