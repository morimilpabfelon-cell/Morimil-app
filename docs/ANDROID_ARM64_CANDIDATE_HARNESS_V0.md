# Android ARM64 candidate harness v0

## Purpose

This harness tests the exact research-only Gemma 3n E2B LiteRT-LM candidate on a physical Android `arm64-v8a` device.

It verifies:

- exact filename, byte size and SHA-256;
- a physical arm64 device and a 64-bit app process;
- LiteRT-LM `0.14.0` initialization with `Backend.CPU()`;
- one bounded local inference;
- exact smoke output `FINAL:AZUL`;
- model hash stability before and after runtime use;
- explicit closure of the conversation and engine;
- deletion of all candidate bytes staged on the device.

It does **not** certify, sign, install, register or promote the candidate. It does not modify the normal runtime flag and does not connect the model to Morimil's identity, memory or lifecycle.

## Exact candidate identity

```text
Local filename:      morimil-deliberative-v0.2.candidate.litertlm
Artifact size:       3655827456 bytes
Artifact SHA-256:    2ed7bc3a0026c93d5b8a4544b352d9d00cd66ff0bac3ef6a20ac3d2cba4010d6
Upstream repository: google/gemma-3n-E2B-it-litert-lm
Repository revision: c03b6f60b8da6c5400b6838a2cf26420f80c0a01
Source revision:     unknown / null
Runtime:             LiteRT-LM 0.14.0
Backend:             CPU
```

The repository revision identifies the producer repository snapshot. It is not treated as the exact base-model source revision.

## Device requirements

Use a dedicated physical Android test device. The host runner rejects emulators and requires:

- `arm64-v8a` in `ro.product.cpu.abilist`;
- at least 6 GiB reported RAM;
- enough free `/data` storage for shell staging, private staging and runtime overhead;
- USB debugging and `adb` authorization;
- no production Morimil installation that must be preserved under a different signing key.

The script installs the repository's debug and instrumentation APKs. It does not clear application data, but a dedicated test device is still required.

## Run from Windows PowerShell

From the repository root:

```powershell
Set-ExecutionPolicy -Scope Process Bypass

& ".\tools\android-arm64\run-gemma3n-e2b-arm64-harness-v0.ps1" `
  -ArtifactPath "C:\Users\morim\MorimilModels\gemma3n-e2b-candidate-20260720-001337\morimil-deliberative-v0.2.candidate.litertlm"
```

When more than one adb device is attached, pass the serial explicitly:

```powershell
& ".\tools\android-arm64\run-gemma3n-e2b-arm64-harness-v0.ps1" `
  -ArtifactPath "C:\Users\morim\MorimilModels\gemma3n-e2b-candidate-20260720-001337\morimil-deliberative-v0.2.candidate.litertlm" `
  -Serial "DEVICE_SERIAL"
```

Use `-SkipBuild` only when both APKs already exist at the standard Gradle output paths.

## Staging and cleanup

The runner performs these steps:

1. hashes the local candidate twice;
2. builds and installs debug test APKs;
3. pushes the candidate to a unique `/data/local/tmp` path;
4. verifies remote size and SHA-256;
5. streams the bytes into the debug app's private test directory with `run-as`;
6. changes the private file to mode `0400`;
7. verifies private size and SHA-256 again;
8. runs only `Gemma3nE2bArm64CandidateHarnessV0Test` with an explicit opt-in argument;
9. extracts the JSON report to the host;
10. deletes the shell staging and the app-private harness directory in `finally`.

The normal CI managed-device runs do not provide the opt-in argument. The heavy test is therefore skipped in CI while its code is still compiled. CI does not download or contain model weights.

## Output

Host reports are written under:

```text
build/morimil-arm64-harness-reports/<timestamp>/
```

Files:

```text
morimil-arm64-candidate-runtime-v0.json
instrumentation-output.txt
```

A passing report must retain:

```text
certified:              false
signed:                 false
installed:              false
promotionAllowed:       false
productionAuthorization:false
```

## Interpretation

A passing run establishes only that the exact candidate can be loaded and used for one bounded CPU inference in the tested Android arm64 environment. It does not establish:

- reproducible model conversion;
- exact source-model revision;
- Android fleet compatibility;
- acceptable sustained latency;
- acceptable RAM, thermal or battery behavior;
- long-context stability;
- safety or general reasoning quality;
- production authorization.

Those remain separate gates.
