# Morimil deliberative v0.2 physical benchmark evidence v1

Status: **research-only**

Source `main` commit: `b9cdfa0371f6520528090655c4eb795a1aae70d9`

Run ID: `morimil-v0.2-physical-20260721-192940-2e071af0`

Manifest digest: `sha256:ca11d9d77ae86aac31374ff72855d36ce640561a437d7afb7b10575417089f09`

## Result boundary

This record freezes the first complete 120-case physical Android ARM64 execution of the exact Morimil deliberative v0.2 LiteRT-LM candidate.

```text
physical infrastructure: PASSED
completed cases:         120/120
benchmark quality gate:  FAILED
false acceptances:       40
production promotion:    BLOCKED
```

A passed physical execution does not imply that the model answered correctly.

## Exact archive

The original host-created ZIP is versioned losslessly as seven ordered Base64 fragments under:

```text
docs/research/evidence/morimil-v0.2-physical-20260721-192940-2e071af0/
  archive-base64/
    part-0001.txt
    part-0002.txt
    part-0003.txt
    part-0004.txt
    part-0005a.txt
    part-0005b.txt
    part-0005c.txt
```

CI verifies every fragment before concatenation and then requires:

```text
concatenated Base64 size:
  18804 bytes

concatenated Base64 SHA-256:
  5105b05f29aa0e2ab2d2fa1f9cb18b4925982d966296821fbe20e1006e37419f

decoded ZIP size:
  14102 bytes

decoded ZIP SHA-256:
  d8c835c29fb79a1d9c49966977771f11da3d93d155a95fa4653868db5baf4ef9
```

The decoded ZIP must contain exactly these six entries:

```text
bundle-v0.2.json
  sha256:89a1dcb398545f81592b3ec994cc0d377418c1ec3c574f27c94f08bfcfee142a

dataset-v0.json
  sha256:f5531706637d2358ca7da9181ab3bd4ccebed634a774c66ebb538bce5cf651fc

instrumentation-output.txt
  sha256:019857fd678cf54c2dae2aa186462a775ff7f05f4c0f012c7b0035f94ca00082

physical-execution-v0.2.json
  sha256:ca856b63f0daadbb6449f144f633ac9c00e9b59ae912b7996cfd4e3324fb1d38

report-v0.2.json
  sha256:2a371f906cebd74a05a50883563b665a5b99fac9864c44c794bb245b73304a12

responses-v0.2.jsonl
  sha256:f5fb72210ca6b60f4093f216c4a313ebbc9f6f4a1a0bcb80b2fa9ce53b606246
```

The bundle and instrumentation transcript are also materialized beside the fragment directory for direct review. The validator requires them to be byte-identical to their ZIP entries.

## Artifact and physical execution

```text
artifact version:          morimil-deliberative-v0.2
filename:                  morimil-deliberative-v0.2.candidate.litertlm
size:                      3655827456 bytes
SHA-256:                   2ed7bc3a0026c93d5b8a4544b352d9d00cd66ff0bac3ef6a20ac3d2cba4010d6
runtime:                   LiteRT-LM 0.14.0
backend:                   CPU
ABI:                       arm64-v8a
sourceModelRevision:       null
engine load:               7359 ms
total benchmark:           1058189 ms
latency minimum:           6655 ms
latency median:            7886 ms
latency p95:               8944 ms
latency maximum:           10966 ms
peak total PSS:            2706151 KiB
maximum temperature:       39.6 C
maximum thermal status:    0 (none)
low-memory observed:       false
engine initialized/closed: true/true
all conversations closed: true
artifact hash stable:      true
errors:                    none
```

This was a powered benchmark run, not an unplugged energy-calibration run.

## Quality result

```text
accepted responses:          77
abstentions:                 43
accepted correct:            37
false accepted:              40
correct abstentions:         12
unnecessary abstentions:     31
acceptance precision:        0.4805194805194805
accepted-correct rate:       0.30833333333333335
abstention precision:        0.27906976744186046
instruction compliance:      0.6416666666666667
strict-format pass rate:     0.5
claim-verification pass:     1.0
state-release pass:          1.0
capability-boundary pass:    1.0
researchGatePassed:          false
productionPromotionAllowed:  false
```

| Domain | Correct accepts | False accepts | Correct abstentions | Unnecessary abstentions |
|---|---:|---:|---:|---:|
| `adversarial_consensus` | 0 | 12 | 0 | 0 |
| `arithmetic` | 3 | 3 | 0 | 6 |
| `claim_verification` | 12 | 0 | 0 | 0 |
| `insufficient_information` | 0 | 0 | 12 | 0 |
| `logic` | 12 | 0 | 0 | 0 |
| `multi_turn_context` | 10 | 1 | 0 | 1 |
| `planning` | 0 | 12 | 0 | 0 |
| `restricted_code` | 0 | 0 | 0 | 12 |
| `spanish` | 0 | 0 | 0 | 12 |
| `strict_format` | 0 | 12 | 0 | 0 |

## Promotion boundary

```text
BENCHMARK_QUALITY_GATE_FAILED
SOURCE_MODEL_REVISION_MISSING
REPRODUCIBLE_CONVERSION_EVIDENCE_MISSING
CERTIFICATION_MISSING
SIGNATURE_MISSING
INSTALLATION_AUTHORIZATION_MISSING
PRODUCTION_AUTHORIZATION_MISSING
```

The candidate remains uncertified, unsigned, uninstalled and inactive. Production authorization and promotion remain false.

## Authority boundary

All 120 records preserve:

```text
requestStateReleased:   true
memoryWriteCapability:  false
identityAuthority:      false
lifecycleAuthority:     false
```

The model is a bounded computation component. It is not Morimil's identity, memory, continuity or lifecycle authority.

## Validation

```text
python -m unittest discover -s tools/benchmarks -p 'test_*.py'
python tools/benchmarks/validate_v02_physical_benchmark_evidence_v1.py check
python tools/benchmarks/validate_v02_physical_benchmark_evidence_v1.py print-digest
```

The validator reconstructs the ZIP from the verified fragments, checks every internal hash, cross-checks all 120 case IDs across dataset, responses, evaluator report and physical telemetry, verifies lifecycle closure and preserves the failed quality and production gates.
