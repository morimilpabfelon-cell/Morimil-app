# Android ARM64 tri-motor benchmark v0

Status: **research-only harness implemented and physically executed; research gate passed**.

The frozen execution and all raw text evidence are documented in:

```text
docs/research/MORIMIL_TRIMOTOR_V02_PHYSICAL_BENCHMARK_EVIDENCE_V1.md
```

This benchmark sends the frozen 120-case dataset through Morimil's isolated tri-motor
research runtime:

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

It does not activate Morimil's normal personal runtime, install a model for daily use,
write memory, alter identity or control lifecycle state.

## Frozen identities

```text
main base for harness:  a38d0652565cd786486ea5c97e573851e7249d84
merged harness commit:  79eb5e31fe11611901048e80803c5c284f58e5cc
benchmark version:      morimil.deliberative.loop-effort.benchmark.smoke.v0
dataset SHA-256:        sha256:f5531706637d2358ca7da9181ab3bd4ccebed634a774c66ebb538bce5cf651fc
case count:             120
artifact version:       morimil-deliberative-v0.2
artifact filename:      morimil-deliberative-v0.2.candidate.litertlm
artifact size:          3,655,827,456 bytes
artifact SHA-256:       sha256:2ed7bc3a0026c93d5b8a4544b352d9d00cd66ff0bac3ef6a20ac3d2cba4010d6
```

The original Deliberative-only benchmark remains the immutable comparison baseline.
The runner decodes its exact raw `report-v0.2.json` from the already-versioned v0.2
evidence archive, verifies SHA-256, and materializes it inside the new run directory.

## Routing plan

The routing plan is derived only from the closed synthetic request. It never reads
`acceptedAnswers` or `expectedDisposition` while producing an answer.

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

The 72 bounded cases use Intuitive plus Metacognitive. The other 48 invoke the
Deliberative candidate; when the bounded Metacognitive core cannot independently
verify them, authority abstains.

## Frozen physical result

```text
run id:                     morimil-trimotor-v0.2-physical-20260722-043653-908d91aa
completed cases:            120/120
research gate:              PASSED
false accepted:             0
accepted correct:           72
abstained:                  48
strict format:              24/24
request state released:     120/120
capability boundary:        120/120
```

Role coverage:

```text
INTUITIVE:                  72
DELIBERATIVE:               48
METACOGNITIVE:              72
opened conversations:       48
closed conversations:       48
```

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

The response state kind is `HYBRID_ROUTED`. `AUTHORITY_ABSTAINED` is a valid
fail-closed stop reason.

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

The final two fields are legacy fail-closed compatibility fields. Public production is
not Morimil's target.

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
6. checks instrumentation success before attempting output extraction;
7. captures exit information and recent logcat on instrumentation failure;
8. extracts response JSONL and the physical report;
9. validates all host contracts;
10. evaluates the frozen 120-case dataset;
11. materializes and verifies the raw v0.2 baseline from immutable evidence;
12. compares the candidate with that frozen baseline;
13. writes a bundle with hashes for every evidence file;
14. removes shell and app-private staging.

Default output root:

```text
build/morimil-arm64-trimotor-benchmark-reports/
```

Each completed run directory contains:

```text
dataset-v0.json
responses-trimotor-v0.2.jsonl
report-trimotor-v0.2.json
comparison-v0.2.json
physical-execution-trimotor-v0.2.json
instrumentation-output.txt
bundle-trimotor-v0.2.json
```

A failed instrumentation run retains the transcript and best-effort diagnostics in the
run directory before private staging is removed.

## Research gate

The fail-closed research gate requires:

```text
falseAcceptedCount == 0
strictFormatPassCount == strictFormatCaseCount
stateReleasePassCount == 120
capabilityBoundaryPassCount == 120
```

The frozen physical run passed all four conditions.

Passing this gate does not certify, sign, install or authorize activation of Morimil's
personal runtime.

## Comparison label

The frozen comparator returned:

```text
V0_3_SUPERIOR
```

This is a legacy outcome label from the comparative v0.3 research contract. It means
that the candidate satisfied the frozen superiority conditions. It does not claim that
a trained neural v0.3 model exists. The candidate is:

```text
Deliberative v0.2
+ bounded Intuitive core
+ bounded Metacognitive core
+ hybrid authority
```

## Explicitly not claimed

```text
trained neural v0.3 model:          false
normal Morimil activation:          false
personal runtime authorization:     false
artifact certification:             false
artifact signature:                 false
artifact installation:              false
```
