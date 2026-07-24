# Android ARM64 tri-motor benchmark v0

Status: **research-only harness physically executed for the historical 72/48 routing contract**.

The immutable execution and raw evidence are documented in:

```text
docs/research/MORIMIL_TRIMOTOR_V02_PHYSICAL_BENCHMARK_EVIDENCE_V1.md
```

The benchmark sends the frozen 120-case dataset through Morimil's isolated tri-motor research runtime. It does not activate Morimil's normal personal runtime, install a model for daily use, write memory, alter identity or control lifecycle state.

## Frozen identities

```text
main base for historical harness:  a38d0652565cd786486ea5c97e573851e7249d84
merged historical harness commit:  79eb5e31fe11611901048e80803c5c284f58e5cc
benchmark version:                  morimil.deliberative.loop-effort.benchmark.smoke.v0
dataset SHA-256:                    sha256:f5531706637d2358ca7da9181ab3bd4ccebed634a774c66ebb538bce5cf651fc
case count:                         120
artifact version:                   morimil-deliberative-v0.2
artifact filename:                  morimil-deliberative-v0.2.candidate.litertlm
artifact size:                      3,655,827,456 bytes
artifact SHA-256:                   sha256:2ed7bc3a0026c93d5b8a4544b352d9d00cd66ff0bac3ef6a20ac3d2cba4010d6
```

The frozen dataset and its digest remain unchanged. The original Deliberative-only benchmark also remains the immutable comparison baseline.

## Historical physical routing contract

The completed physical run used this routing plan:

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

The 72 bounded cases used local Intuitive computation and blind deterministic Metacognitive recomputation. The other 48 invoked the Deliberative candidate and abstained when independent verification was unavailable.

## Historical physical result

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

Historical role coverage:

```text
INTUITIVE:                  72
DELIBERATIVE:               48
METACOGNITIVE:              72
opened conversations:       48
closed conversations:       48
```

These figures describe the frozen physical execution only. They must not be silently rewritten after routing code changes.

## Current adapter contract

The current adapter adds deterministic authority for the twelve frozen `closed-order-v0` logic cases and routes the twelve existing `strict_format` cases through the exact-instruction authority. The dataset is not modified, and the total routing counts remain:

```text
84 bounded cases:
  arithmetic
  logic (closed arrival-order grammar only)
  restricted_code
  claim_verification
  strict_format (exact subtraction and FINAL:<resultado> grammar)
  adversarial_consensus
  multi_turn_context

36 generative or unsupported cases:
  spanish
  planning
  insufficient_information
```

Expected delta relative to the historical physical contract:

```text
bounded:    72 -> 84
generative: 48 -> 36
```

The exact-instruction change does not alter these counts. It removes the benchmark adapter's previous arithmetic rewriting for `strict_format` and instead verifies the original frozen instruction directly.

This remains a code and test contract, not new physical evidence. A new opt-in run on a physical ARM64 Android device is required before claiming 84 accepted correct, 36 abstained, updated role counts, latency, memory, battery or thermal results.

## Current authority flow

```text
bounded closed task
  -> local bounded Intuitive core
  -> optional blind deterministic Metacognitive recomputation in research runtime
  -> deterministic hybrid authority

open or unsupported task
  -> Deliberative research candidate when requested
  -> bounded verifier attempt
  -> structured abstention when deterministic verification is unavailable
```

For the closed-order logic domain, generated candidates are advisory. The final answer comes from a unique topological ordering of the parsed relation graph. Cycles, ties, malformed relations and unsupported wording abstain.

For `strict_format`, the original prompt must match the complete subtraction grammar and canonical `FINAL:<resultado>` placeholder. The local authority computes the subtraction and owns the final format. Altered placeholders, extra prose, unsupported operations and non-canonical integers abstain.

## Strict current evidence gate

The host-side current-contract validator independently checks every response. It does not trust aggregate counts alone.

For each frozen `caseId`, it verifies:

```text
expected domain
expected final disposition
exact authority route
exact output profile and reduction
requested, activated, failed and unavailable roles
exact final answer recalculated from the frozen generator
request-state release and forbidden-capability boundaries
```

A current run is accepted only with these exact totals:

```text
accepted correct:           84
false accepted:             0
abstained:                  36
correct abstentions:        12
unnecessary abstentions:    24
strict format:              24/24
claim verification:         12/12
state released:             120/120
capability boundary:        120/120

INTUITIVE activations:      84
DELIBERATIVE activations:   36
METACOGNITIVE activations:  84
opened conversations:       36
closed conversations:       36
```

The stale historical 72/48 routing contract is explicitly rejected by the current validator.

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

## Physical report requirements

A completed physical report requires:

```text
all 120 cases completed
all required roles activated according to the current routing plan
opened conversations == closed conversations == 36
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

The final two fields are legacy fail-closed compatibility fields. Public production is not Morimil's target.

## Host execution

From the repository root on Windows PowerShell, use only the strict current-contract wrapper:

```powershell
python .\tools\android-arm64\run_current_gemma3n_e2b_arm64_trimotor_benchmark_v0.py `
  "C:\Users\morim\MorimilModels\gemma3n-e2b-candidate-20260720-001337\morimil-deliberative-v0.2.candidate.litertlm"
```

Use `--serial <adb-serial>` only when more than one device is attached.

Before connecting the phone, the host-only gate can be checked with:

```powershell
python .\tools\android-arm64\run_current_gemma3n_e2b_arm64_trimotor_benchmark_v0.py --self-test
```

The strict wrapper:

1. runs the existing isolated physical harness;
2. verifies the exact local artifact twice;
3. verifies a physical ARM64 Android device with sufficient memory and storage;
4. builds and installs debug plus instrumentation APKs;
5. stages the artifact in app-private, read-only storage;
6. runs the opt-in instrumentation class;
7. checks instrumentation success before attempting output extraction;
8. captures exit information and recent logcat on instrumentation failure;
9. extracts response JSONL and the physical report;
10. validates the exact 84/36 structural contract;
11. recalculates the expected answer for every accepted frozen case;
12. evaluates the frozen 120-case dataset;
13. requires the exact current evaluation counts and zero false acceptances;
14. materializes and verifies the raw v0.2 baseline from immutable evidence;
15. compares the candidate with that frozen baseline;
16. verifies every bundle file hash and size;
17. removes shell and app-private staging.

A zero exit status from the strict wrapper means the complete current evidence directory passed all checks. The historical runner alone must not be used to claim current 84/36 evidence.

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

A failed instrumentation or evidence-validation run retains its transcript and best-effort diagnostics before private staging is removed.

## Research gate

The strict current fail-closed gate requires all of the following simultaneously:

```text
acceptedCorrectCount == 84
falseAcceptedCount == 0
abstainedCount == 36
strictFormatPassCount == strictFormatCaseCount == 24
claimVerificationPassCount == claimVerificationCaseCount == 12
stateReleasePassCount == 120
capabilityBoundaryPassCount == 120
```

The historical physical run passed its historical gate. The current adapter must pass this stricter gate in a separate physical execution before its 84/36 routing and authority ownership become physical evidence.

Passing the research gate does not certify, sign, install or authorize activation of Morimil's personal runtime.

## Explicitly not claimed

```text
new 84/36 physical result:          false
trained neural v0.3 model:          false
normal Morimil Deliberative:        false
normal Morimil Metacognitive:       false
personal runtime authorization:     false
artifact certification:             false
artifact signature:                 false
artifact installation:              false
```
