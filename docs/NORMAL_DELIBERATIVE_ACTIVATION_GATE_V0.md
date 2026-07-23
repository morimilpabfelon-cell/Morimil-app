# Normal deliberative activation gate v0

Status: **normal-runtime activation blocked**

## Purpose

This gate prevents a physically executable deliberative candidate from entering Morimil's normal runtime without sufficient quality, provenance and authorization evidence.

Physical execution and production fitness are separate facts. The current Gemma 3n E2B LiteRT-LM candidate completed its 120-case Android ARM64 run, but the frozen benchmark recorded:

```text
completed cases:          120/120
false accepted responses: 40
quality gate:             failed
production promotion:     blocked
```

Therefore `DELIBERATIVE` remains absent from `MorimilNormalIntrinsicRuntimeV0`.

## Runtime behavior

`MorimilNormalDeliberativeActivationGateV0` evaluates the exact candidate identity and reports structured blockers. The current candidate is blocked by:

```text
BENCHMARK_QUALITY_GATE_FAILED
FALSE_ACCEPTANCES_PRESENT
SOURCE_MODEL_REVISION_MISSING
REPRODUCIBLE_CONVERSION_EVIDENCE_MISSING
CERTIFICATION_MISSING
SIGNATURE_MISSING
INSTALLATION_AUTHORIZATION_MISSING
PRODUCTION_AUTHORIZATION_MISSING
```

The normal runtime continues to register only:

```text
INTUITIVE
```

`METACOGNITIVE` also remains outside normal runtime and requires a separate review.

## Fail-closed transition

Passing the declarative gate in the future does not activate a motor automatically. `MorimilNormalIntrinsicRuntimeV0` deliberately fails construction if the current evidence becomes promotable while the registry has not been explicitly revised.

A later activation PR must separately provide:

1. a benchmark with all required cases completed and zero false acceptances;
2. an exact source-model revision;
3. reproducible conversion evidence;
4. a verified and certified local artifact;
5. a valid artifact signature;
6. explicit installation authorization;
7. explicit production authorization;
8. a local engine wired through `VerifiedLocalDeliberativeCoreV01`;
9. normal-runtime tests on physical Android devices;
10. an explicit registry change reviewed as its own PR.

## Authority boundary

This gate and any future deliberative motor provide computation only. They grant no capability to write memory, alter identity or Genesis, control continuity, manage lifecycle, install artifacts, access providers or own final authority.

Morimil's deterministic kernel remains the final authority.
