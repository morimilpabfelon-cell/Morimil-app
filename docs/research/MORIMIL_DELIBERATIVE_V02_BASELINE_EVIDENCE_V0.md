# Morimil deliberative v0.2 baseline evidence v0

Status: **research-only**

Snapshot commit: `5071f399703273a91ad280ebc43fbce8eb2c1ff7`

Manifest digest: `sha256:a80e074e0a4fbf5e63d2c9e5d96d575c432a12788e559717154a53e724ecb404`

## Purpose

This record freezes only what the repository can currently support for the exact Gemma 3n E2B LiteRT-LM v0.2 candidate. It does not reconstruct missing raw files, run the model, infer measurements, or convert a repository revision into a source-model revision.

The snapshot deliberately separates three states:

```text
artifact identity:             frozen
existing physical summary:     accepted, raw reports not versioned
120-case smoke benchmark:      NOT_EXECUTED
production promotion:          blocked
```

## Exact artifact

```text
profile:
  morimil.deliberative.artifact.contract.v0.2-candidate

artifact:
  morimil-deliberative-v0.2

filename:
  morimil-deliberative-v0.2.candidate.litertlm

size:
  3655827456 bytes

sha256:
  2ed7bc3a0026c93d5b8a4544b352d9d00cd66ff0bac3ef6a20ac3d2cba4010d6

upstream artifact repository:
  google/gemma-3n-E2B-it-litert-lm

upstream artifact repository revision:
  c03b6f60b8da6c5400b6838a2cf26420f80c0a01

source model:
  google/gemma-3n-E2B-it

sourceModelRevision:
  null
```

The upstream artifact-repository revision is not the exact source-model revision and must never be substituted for it.

## Physical summary frozen from the existing repository contract

```text
physical ARM64 harness:            passed
sustained passes:                  6
unplugged passes:                  6
strict inferences:                 36
strict outputs:                    all passed
artifact hash stable:              true
conversations closed:              all
engines closed:                    all
samples unplugged:                 all
observed charge decrease:          29000 microamp-hours
p95 inference latency:             8953 ms
peak total PSS:                    2649395 KiB
maximum battery temperature:       35.0 C
maximum thermal status:            0
low-memory observed:               false
errors:                            none
research evidence accepted:        true
```

The charge decrease is Android operating-system telemetry, not calibrated laboratory energy measurement.

## Evidence limitation

The raw sustained and unplugged JSON reports are not versioned in the repository snapshot used by this PR. The manifest therefore uses:

```text
summaryStatus:
  ACCEPTED_SUMMARY_RAW_REPORT_NOT_VERSIONED

rawReportVersioned:
  false

rawReportSha256:
  null
```

This is a repository summary, not a replacement for the missing raw reports. A later change may add independently verified raw evidence, but it must use a new version and real file hashes.

## Benchmark state

The frozen benchmark identity is:

```text
benchmark:
  morimil.deliberative.loop-effort.benchmark.smoke.v0

cases:
  120

dataset sha256:
  f5531706637d2358ca7da9181ab3bd4ccebed634a774c66ebb538bce5cf651fc
```

No complete v0.2 response file or evaluator report exists in the repository. Consequently this snapshot requires:

```text
runStatus:           NOT_EXECUTED
responseFileSha256:  null
reportFileSha256:    null
report:              null
```

The physical six-by-six profile is not equivalent to the 120-case smoke benchmark. Its results must not be copied into benchmark metrics.

## Promotion boundary

The snapshot keeps all six blockers:

```text
SOURCE_MODEL_REVISION_MISSING
REPRODUCIBLE_CONVERSION_EVIDENCE_MISSING
CERTIFICATION_MISSING
SIGNATURE_MISSING
INSTALLATION_AUTHORIZATION_MISSING
PRODUCTION_AUTHORIZATION_MISSING
```

It also requires:

```text
certified:                   false
signed:                      false
installed:                   false
normalRuntimeActivated:      false
productionAuthorization:     false
productionPromotionAllowed:  false
```

## Morimil authority boundary

The intrinsic deliberative motor is a Morimil-owned computation component. It is not a separate identity and does not own Morimil memory or lifecycle.

External models and services remain temporary, replaceable tools. They cannot become Morimil identity, continuity, memory authority or lifecycle authority.

The snapshot therefore requires:

```text
intrinsicMotorOwnedByMorimil:    true
externalMotorMayBecomeIdentity:  false
memoryWriteCapability:           false
identityAuthority:               false
lifecycleAuthority:              false
requestScopedStateRequired:      true
```

## Validation

From the repository root:

```text
python -m unittest discover -s tools/benchmarks -p 'test_*.py'
python tools/benchmarks/validate_v02_baseline_evidence_v0.py check
python tools/benchmarks/validate_v02_baseline_evidence_v0.py print-digest
```

The validator is standard-library only. It rejects altered physical numbers, fabricated benchmark execution, forged source-model provenance, production authorization and violations of Morimil identity or memory boundaries.

## Non-goals

This snapshot does not:

- execute the 120 benchmark cases;
- include model weights;
- reconstruct missing raw reports;
- certify or sign the candidate;
- install or activate the candidate;
- enable the hybrid runtime;
- write Morimil identity or memory;
- authorize production.
