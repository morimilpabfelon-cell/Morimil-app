# Android ARM64 deliberative benchmark v0

This harness executes the frozen 120-case Morimil deliberative smoke benchmark against the exact v0.2 LiteRT-LM research candidate on one physical Android ARM64 device.

It is an opt-in research workflow. It is not part of Morimil's normal runtime and does not certify, sign, install, activate or authorize the candidate.

## Exact inputs

### Artifact

```text
artifactVersion: morimil-deliberative-v0.2
filename:        morimil-deliberative-v0.2.candidate.litertlm
sizeBytes:       3655827456
sha256:          2ed7bc3a0026c93d5b8a4544b352d9d00cd66ff0bac3ef6a20ac3d2cba4010d6
runtime:         LiteRT-LM 0.14.0
backend:         CPU
ABI:             arm64-v8a
```

`sourceModelRevision` remains unknown. The artifact-repository revision is not substituted for the source-model revision.

### Benchmark

```text
benchmarkVersion:
morimil.deliberative.loop-effort.benchmark.smoke.v0

caseCount:
120

datasetSha256:
sha256:f5531706637d2358ca7da9181ab3bd4ccebed634a774c66ebb538bce5cf651fc
```

The instrumentation APK contains an independent Kotlin implementation of the same deterministic generator. Its always-on contract test serializes the generated dataset canonically and requires the exact digest above. The host evaluator independently regenerates the dataset from:

```text
tools/benchmarks/benchmark_common_v0.py
```

A changed case, order, field or encoding changes the digest and fails validation.

## Execution model

The physical runner:

1. verifies the local artifact twice;
2. requires one physical Android `arm64-v8a` device;
3. requires a 64-bit process, Android API 29 or newer and at least 6 GiB RAM;
4. stages the artifact temporarily;
5. copies it into private app storage as a read-only file;
6. verifies its size and SHA-256 again;
7. loads one CPU `Engine`;
8. creates one isolated `Conversation` for each case;
9. sends one bounded request per conversation;
10. closes every conversation before recording its response;
11. closes the engine;
12. verifies the artifact hash after execution;
13. extracts the response JSONL and physical execution report;
14. evaluates the responses on the host with the frozen benchmark evaluator;
15. writes a bundle containing SHA-256 values for every output;
16. removes both device-side artifact copies in `finally`.

No model weights are committed to Git.

## Prompt boundary

The test sends only:

- the case's closed evidence;
- the case's request-scoped transcript;
- the final user request;
- a fixed local benchmark instruction.

The expected answers are not included in the prompt.

The fixed abstention token is:

```text
MORIMIL_ABSTAIN
```

The runner maps only that exact string to:

```json
{
  "finalDisposition": "ABSTAINED",
  "finalAnswer": null
}
```

Any other non-empty response is recorded as accepted and is evaluated without manual correction.

## Response record

Each completed case produces one JSONL record compatible with:

```text
docs/model-artifacts/morimil-deliberative-benchmark-response-v0.schema.json
```

The physical v0.2 runner always records:

```text
stateKind:              TEXTUAL_CONVERSATION
completedIterations:    1
requestStateReleased:   true
memoryWriteCapability:  false
identityAuthority:      false
```

A conversation-close failure aborts the run. The runner does not emit a successful record with unreleased request state.

`strictFormatPassed`, `instructionCompliant` and `claimVerificationPassed` are derived deterministically by the instrumentation harness. They are not supplied by the model.

## Safety limits

The benchmark refuses to start unless:

```text
physical device:             required
ABI:                         arm64-v8a
process:                     64-bit
Android API:                 >= 29
device RAM:                  >= 6 GiB
initial battery:             >= 50 percent
initial battery temperature: < 42 C
thermal status:              below severe
low-memory state:            false
artifact identity:           exact
artifact permissions:        read-only
```

During execution it aborts when any of these becomes true:

```text
battery level:       < 20 percent
battery temperature: >= 43 C
thermal status:      severe or higher
low-memory state:    true
total duration:      > 60 minutes
single inference:    > 60 seconds
```

The phone should be cool, uncovered, unlocked and otherwise idle. The workflow records whether external power is connected, but this is not an energy-calibration profile. Use the separate unplugged profile for battery-counter observations.

## Host command

From the repository root:

```powershell
python .\tools\android-arm64\run_gemma3n_e2b_arm64_deliberative_benchmark_v0.py `
  "$env:USERPROFILE\MorimilModels\gemma3n-e2b-candidate-20260720-001337\morimil-deliberative-v0.2.candidate.litertlm"
```

For a specific ADB device:

```powershell
python .\tools\android-arm64\run_gemma3n_e2b_arm64_deliberative_benchmark_v0.py `
  "$env:USERPROFILE\MorimilModels\gemma3n-e2b-candidate-20260720-001337\morimil-deliberative-v0.2.candidate.litertlm" `
  --serial DEVICE_SERIAL
```

After the APKs have already been built:

```powershell
python .\tools\android-arm64\run_gemma3n_e2b_arm64_deliberative_benchmark_v0.py `
  "$env:USERPROFILE\MorimilModels\gemma3n-e2b-candidate-20260720-001337\morimil-deliberative-v0.2.candidate.litertlm" `
  --skip-build
```

The command does not run automatically in CI.

## Outputs

A successful infrastructure run creates:

```text
build/morimil-arm64-deliberative-benchmark-reports/<run-id>/
  dataset-v0.json
  responses-v0.2.jsonl
  report-v0.2.json
  physical-execution-v0.2.json
  instrumentation-output.txt
  bundle-v0.2.json
```

### `responses-v0.2.jsonl`

Contains exactly 120 unedited model outcomes. Wrong answers, abstentions and false acceptances remain visible.

### `report-v0.2.json`

Generated by the benchmark evaluator. It contains:

```text
acceptedCorrectRate
acceptancePrecision
falseAcceptedCount
abstentionRate
abstentionPrecision
strictFormatPassRate
instructionComplianceRate
claimVerificationPassRate
stateReleasePassRate
capabilityBoundaryPassRate
latency minimum/median/p95/maximum/average
researchGatePassed
```

A failed quality gate does not erase the result. The infrastructure run may complete while `researchGatePassed` is false.

### `physical-execution-v0.2.json`

Contains execution evidence only:

- artifact hashes before and after;
- engine lifecycle;
- conversation closure;
- per-case latency and process CPU time;
- PSS observations;
- battery and thermal observations;
- device identity fields;
- errors.

It does not claim benchmark correctness.

### `bundle-v0.2.json`

Pins SHA-256 values for the dataset, responses, benchmark report, physical report and instrumentation transcript.

## Result handling

Do not edit response records to improve the score.

A later baseline-freeze change may move the manifest from:

```text
runStatus: NOT_EXECUTED
```

to:

```text
runStatus: EXECUTED
```

only when the real response and report hashes are available.

The physical execution report and benchmark report serve different purposes and must not be substituted for each other.

## Authority boundary

The v0.2 model is an intrinsic computation component owned and bounded by Morimil's architecture. It is not a separate identity.

External models and services remain temporary tools and may not become Morimil's:

- identity;
- memory;
- continuity;
- lifecycle authority;
- production authority.

The runner fixes the following values:

```text
memoryWriteCapability:       false
identityAuthority:           false
lifecycleAuthority:          false
normalRuntimeActivated:      false
productionAuthorization:     false
productionPromotionAllowed:  false
```

The benchmark does not access Morimil private memory, Genesis content, credentials, personal conversations, external teachers or provider APIs.

## CI behavior

Normal CI performs only:

```text
host-runner self-test
host-runner unit tests
deterministic Python dataset check
independent Kotlin dataset digest test
Android compilation
normal unit tests
normal managed-device tests
```

The opt-in argument is absent in CI, so the 3.66 GB artifact is never required, downloaded or executed.
