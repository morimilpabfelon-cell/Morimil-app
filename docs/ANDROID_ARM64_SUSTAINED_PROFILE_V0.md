# Android ARM64 sustained profile v0

This harness measures the exact Gemma 3n E2B LiteRT-LM research candidate on a physical Android ARM64 device. It is an opt-in instrumentation workflow and is not part of Morimil's normal runtime.

## Candidate identity

```text
filename: morimil-deliberative-v0.2.candidate.litertlm
size:     3655827456 bytes
sha256:   2ed7bc3a0026c93d5b8a4544b352d9d00cd66ff0bac3ef6a20ac3d2cba4010d6
runtime:  LiteRT-LM 0.14.0
backend:  CPU
```

The repository revision for the published LiteRT-LM artifact remains separate from the missing exact source-model revision. This harness does not resolve that provenance blocker.

## Measurements

The profile loads one engine and performs six isolated conversations. It records:

- engine load latency;
- per-round wall latency and process CPU time;
- minimum, median, p95, maximum and average inference latency;
- process total/native/Dalvik/other PSS;
- Java heap use and system available memory;
- battery level, temperature, current and charge counter when Android exposes them;
- Android thermal status before and after each round;
- strict output compliance for all six rounds;
- resource closure and artifact integrity before and after execution.

## Safety limits

The heavy test is fail-closed. It will not start unless:

- the device is physical and declares `arm64-v8a`;
- the process is 64-bit;
- Android API level is at least 29;
- the device reports at least 6 GiB of RAM;
- battery level is at least 30 percent;
- battery temperature is below 42 C;
- thermal status is below `severe`;
- the exact read-only artifact has the expected size and SHA-256.

During execution it aborts if battery temperature reaches 45 C or Android reports thermal status `severe` or higher.

Use a cool, uncovered device with the screen unlocked. Stop the run manually if the phone becomes unusually hot, disconnects repeatedly or Android displays a thermal warning.

## Research gate

The v0 gate requires:

```text
completed rounds:                    6/6
strict outputs:                      6/6
conversations closed:                all
maximum inference latency:           <= 30000 ms
p95 inference latency:               <= 20000 ms
peak total process PSS:              <= 8 GiB
maximum battery temperature:         < 45 C
battery-temperature increase:        <= 8 C
maximum Android thermal status:      below severe
Android low-memory state:            never observed
artifact hash:                       unchanged
engine:                              initialized and closed
```

Passing this small physical gate is research evidence only. It is not a production performance specification and does not replace broader device coverage, long-context testing, battery-energy measurement, provenance, certification or signing.

## CI behavior

Normal CI compiles the instrumentation code but does not supply `morimilArm64SustainedProfileEnabled=true`. The 3.66 GB test is skipped and no workflow downloads or embeds model weights.

## Windows execution

Use the PR branch on a physical device with USB debugging authorized:

```powershell
Set-ExecutionPolicy -Scope Process Bypass

& ".\tools\android-arm64\run-gemma3n-e2b-arm64-sustained-profile-v0.ps1" `
  -ArtifactPath "$env:USERPROFILE\MorimilModels\gemma3n-e2b-candidate-20260720-001337\morimil-deliberative-v0.2.candidate.litertlm"
```

After a successful build, later retries may use `-SkipBuild`.

The runner verifies the artifact in host storage, shell staging and private app storage; runs the instrumentation profile; extracts an atomic JSON report; and removes both device-side copies in `finally`.

Default local outputs:

```text
build/morimil-arm64-sustained-profile-reports/<timestamp>/morimil-arm64-sustained-profile-v0.json
build/morimil-arm64-sustained-profile-reports/<timestamp>/instrumentation-output.txt
```

## Boundary

The report must retain:

```text
sourceModelRevision:      null
certified:                false
signed:                   false
installed:                false
promotionAllowed:         false
productionAuthorization:  false
normalRuntimeActivated:   false
```

The harness does not include model weights, persist the candidate, activate the hybrid router, write Morimil identity or memory, access a provider, or authorize production use.
