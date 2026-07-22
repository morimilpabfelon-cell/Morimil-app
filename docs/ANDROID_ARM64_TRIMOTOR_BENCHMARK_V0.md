# Android ARM64 tri-motor benchmark v0

Status: **research-only harness implemented; physical execution not yet performed**.

This benchmark sends the frozen 120-case dataset through Morimil's isolated tri-motor research runtime:

```text
Bounded closed tasks
  -> Intuitive bounded local core
  -> Metacognitive blind deterministic recomputation
  -> Hybrid authority

Open or unsupported tasks
  -> Deliberative v0.2 LiteRT-LM candidate
  -> Metacognitive bounded verifier attempt
  -> Hybrid authority
  -> structured abstention when independent verification is unavailable
```

It does not activate the normal Morimil runtime, install a model for daily use, authorize production, write memory, alter identity or control lifecycle state.

## Frozen identities

```text
main base for harness:  a38d0652565cd786486ea5c97e573851e7249d84
benchmark version:      morimil.deliberative.loop-effort.benchmark.smoke.v0
dataset SHA-256:        sha256:f5531706637d2358ca7da9181ab3bd4ccebed634a774c66ebb538bce5cf651fc
case count:             120
artifact version:       morimil-deliberative-v0.2
artifact filename:      morimil-deliberative-v0.2.candidate.litertlm
artifact size:          3,655,827,456 bytes
artifact SHA-256:       sha256:2ed7bc3a0026c93d5b8a4544b352d9d00cd66ff0bac3ef6a20ac3d2cba4010d6
```

The original deliberative-only benchmark and its evidence remain unchanged.

## Routing plan

The routing plan is derived only from the closed synthetic request. It never reads `acceptedAnswers` or `expectedDisposition` while producing an answer.

Expected route coverage by dataset construction:

```text
72 bounded cases:
  arithmetic
  restricted_code
  claim_verification
  strict_format
  adversarial_consensus
  multi_turn_context

48 generative or unsupported cases:
  logic
  spanish
  planning
  insufficient_information
```

The 72 bounded cases use Intuitive plus Metacognitive. The other 48 invoke the Deliberative candidate; when the bounded Metacognitive core cannot independently verify them, authority must abstain.

These are routing expectations, not benchmark results.

## Output contract

Each response record includes:

```text
final answer or abstention
primary candidate
verifier candidate
requested, activated, failed and unavailable roles
activated capability versions
authority route and status
finalization status
request-state release
capability boundaries
latency and deliberative iteration count
```

The response state kind is `HYBRID_ROUTED`. `AUTHORITY_ABSTAINED` is a valid fail-closed stop reason.

The physical report requires:

```text
all 120 cases completed
all three roles activated at least once
opened conversations == closed conversations
request state released for every case
artifact hash stable before and after execution
engine initialized and closed
memoryWriteCapability == false
identityAuthority == false
lifecycleAuthority == false
normalRuntimeActivated == false
productionAuthorization == false
promotionAllowed == false
```

## Host execution

From the repository root on Windows PowerShell:

```powershell
python .\tools\android-arm64\run_gemma3n_e2b_arm64_trimotor_benchmark_v0.py `
  "C:\Users\morim\MorimilModels\gemma3n-e2b-candidate-20260720-001337\morimil-deliberative-v0.2.candidate.litertlm"
```

Use `--serial <adb-serial>` only when more than one device is attached.

The runner:

1. verifies the exact local artifact twice;
2. verifies a physical ARM64 Android device with sufficient memory and storage;
3. builds and installs debug plus instrumentation APKs;
4. stages the artifact in app-private, read-only storage;
5. runs the opt-in instrumentation class;
6. extracts response JSONL and the physical report;
7. validates all host contracts;
8. evaluates the frozen 120-case dataset;
9. compares the result with the frozen physical v0.2 baseline;
10. writes a bundle with hashes for every evidence file;
11. removes shell and app-private staging.

Default output root:

```text
build/morimil-arm64-trimotor-benchmark-reports/
```

Each run directory contains:

```text
dataset-v0.json
responses-trimotor-v0.2.jsonl
report-trimotor-v0.2.json
comparison-v0.2.json
physical-execution-trimotor-v0.2.json
instrumentation-output.txt
bundle-trimotor-v0.2.json
```

## Research gate

The existing evaluator remains fail-closed. The research gate requires:

```text
falseAcceptedCount == 0
strictFormatPassCount == strictFormatCaseCount
stateReleasePassCount == 120
capabilityBoundaryPassCount == 120
```

Passing this gate does not certify, sign, install, activate or promote the motor. A physical result must be frozen separately as immutable evidence before any later decision.

## Explicitly not claimed

```text
physical tri-motor result:       not yet executed
research gate result:            unknown until execution
comparison outcome:              unknown until execution
normal Morimil activation:       false
artifact certification:          false
artifact signature:              false
artifact installation:           false
production promotion:            false
```
