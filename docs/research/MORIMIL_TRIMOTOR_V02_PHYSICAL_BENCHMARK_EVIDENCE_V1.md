# Morimil trimotor v0.2 physical benchmark evidence v1

Status: **physical ARM64 execution completed; research gate passed; personal runtime activation remains blocked**.

This record freezes the exact physical run:

```text
run id:                 morimil-trimotor-v0.2-physical-20260722-043653-908d91aa
source main commit:     79eb5e31fe11611901048e80803c5c284f58e5cc
benchmark version:      morimil.deliberative.loop-effort.benchmark.smoke.v0
dataset SHA-256:        sha256:f5531706637d2358ca7da9181ab3bd4ccebed634a774c66ebb538bce5cf651fc
candidate motor:        morimil-trimotor-v0.2-bounded-local-v0
artifact SHA-256:       sha256:2ed7bc3a0026c93d5b8a4544b352d9d00cd66ff0bac3ef6a20ac3d2cba4010d6
```

The source archive received from the owner is identified by:

```text
filename:  morimil-trimotor-v0.2-physical-20260722-043653-908d91aa-freeze.zip
size:      19,421 bytes
SHA-256:   sha256:799d29f130dda25981941c28a702f46c5be9a998fdb36cbc9c427532a9b3b8f1
```

The exact binary archive is versioned as seven ordered Base64 fragments under
`docs/research/evidence/`. CI concatenates them, verifies the Base64 and decoded ZIP
SHA-256 values, then validates all seven archive entries. The transcript, comparison,
bundle and restored raw baseline report are also kept as direct convenience files.

## Physical result

```text
instrumentation tests:        OK (2 tests)
completed cases:              120/120
research gate:                PASSED
accepted correct:             72
false accepted:               0
abstained:                    48
correct abstentions:          12
unnecessary abstentions:      36
strict format:                24/24
request state released:       120/120
capability boundary:          120/120
```

Role and authority coverage:

```text
INTUITIVE activations:        72
DELIBERATIVE activations:     48
METACOGNITIVE activations:    72

ACCEPTED_DETERMINISTIC:       72
ABSTAINED:                    48

conversations opened:         48
conversations closed:         48
```

The bounded route accepted arithmetic, restricted code, closed claim verification,
strict format, adversarial-consensus reductions and bounded multi-turn context.
Open logic, Spanish generation, planning and insufficient-information cases were sent
through the Deliberative route and stopped fail-closed when independent bounded
verification was unavailable.

## Physical performance

```text
model load:                   7,914 ms
total benchmark:              883,211 ms
latency median:               2 ms
latency p95:                  18,049 ms
latency maximum:              19,184 ms
peak total PSS:               2,705,581 KiB
peak native PSS:              973,384 KiB
minimum system available:     5,676,789,760 bytes
maximum battery temperature:  38.4 C
maximum thermal status:       0 (none)
low memory observed:          false
physical errors:              []
```

The low median is expected: 72 bounded cases were resolved deterministically without
LiteRT-LM inference. The 48 Deliberative cases dominate average and p95 latency.

## Baseline comparison

The exact raw baseline report already exists inside Morimil's immutable v0.2 evidence
archive. The corrected host runner decodes `report-v0.2.json`, writes it into the
current run directory and verifies:

```text
sha256:2a371f906cebd74a05a50883563b665a5b99fac9864c44c794bb245b73304a12
```

This restores the raw comparison input without adding a second divergent baseline
copy to the repository.

The frozen comparator returned:

```text
outcome:  V0_3_SUPERIOR
reasons:  []
```

`V0_3_SUPERIOR` is a legacy outcome name from the frozen comparative research
contract. It means that this candidate satisfied the comparator's superiority
conditions. It does **not** mean that a trained neural Morimil v0.3 model exists.
The candidate is the v0.2 Deliberative artifact routed with bounded Intuitive and
Metacognitive cores under hybrid authority.

## Improvement and limitation

Compared with the Deliberative-only v0.2 baseline:

```text
false accepted:               40 -> 0
accepted correct:             37 -> 72
acceptance precision:         0.480519 -> 1.0
strict format pass rate:      0.5 -> 1.0
accepted-correct rate:        0.308333 -> 0.6
```

The remaining limitation is excessive conservatism:

```text
answer-required abstentions:  36
instruction compliance:       84/120
```

The next motor research problem is to verify more open reasoning without
reintroducing false acceptance.

## Authority and activation boundary

This evidence records:

```text
memory write capability:              false
identity authority:                   false
lifecycle authority:                  false
normal runtime activated:             false
personal runtime activation allowed:  false
certified:                             false
signed:                                false
installed:                             false
```

Morimil is a private personal instance. The legacy fields
`productionAuthorization` and `productionPromotionAllowed` remain `false` only as
fail-closed compatibility fields in the existing benchmark contracts; public
production is not the project target.

Remaining blockers:

```text
SOURCE_MODEL_REVISION_MISSING
REPRODUCIBLE_CONVERSION_EVIDENCE_MISSING
CERTIFICATION_MISSING
SIGNATURE_MISSING
INSTALLATION_AUTHORIZATION_MISSING
PERSONAL_RUNTIME_ACTIVATION_AUTHORIZATION_MISSING
```

## Verification

From the repository root:

```powershell
python .\tools\benchmarks\validate_trimotor_v02_physical_benchmark_evidence_v1.py check
```

Expected result:

```text
Morimil trimotor v0.2 physical benchmark evidence v1: VALID
Physical infrastructure: PASSED
Cases completed: 120/120
Research gate: PASSED (0 false acceptances)
Comparison label: V0_3_SUPERIOR (legacy label, no trained v0.3 claim)
Personal runtime activation: BLOCKED
```
