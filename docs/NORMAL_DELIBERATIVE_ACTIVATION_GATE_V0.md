# Normal deliberative activation gate v0

Status: **normal-runtime activation blocked**

## Purpose

This gate prevents a physically executable deliberative candidate from entering Morimil's normal runtime without sufficient independent quality, provenance and authorization evidence.

Physical execution and personal operational fitness are separate facts. Two evidence layers coexist:

1. the frozen raw-candidate benchmark recorded 40 false accepted responses and failed its quality gate;
2. the current hybrid-authority benchmark completed 120/120 cases with 84 deterministic acceptances, 36 abstentions and zero false acceptances.

The second result proves that Morimil's deterministic authority boundary can contain the candidate. It does **not** prove that the deliberative model is safe or authoritative when used alone. Therefore the normal deliberative gate continues to use the raw-candidate quality evidence.

## Current raw-candidate evidence

```text
completed cases:          120/120
false accepted responses: 40
quality gate:             failed
personal activation:      blocked
```

`DELIBERATIVE` remains absent from `MorimilNormalIntrinsicRuntimeV0`.

## Acquisition, not conversion

The current candidate is Google's prebuilt `.litertlm` binary:

```text
acquisition mode:                 DIRECT_UPSTREAM_BINARY_RENAME_ONLY
conversion performed by Morimil: false
rename only:                      true
```

Morimil must prove authenticated upstream binary acquisition and byte identity. It must not claim or require reproducible conversion evidence for a conversion it never performed.

The exact producer mapping from the binary to the deeper Transformers checkpoint remains undisclosed and is represented separately by `SOURCE_MODEL_REVISION_MISSING`.

## Runtime behavior

`MorimilNormalDeliberativeActivationGateV0` evaluates the exact candidate identity and reports structured blockers. The current candidate is blocked by:

```text
BENCHMARK_QUALITY_GATE_FAILED
FALSE_ACCEPTANCES_PRESENT
SOURCE_MODEL_REVISION_MISSING
REPRODUCIBLE_ACQUISITION_EVIDENCE_MISSING
CERTIFICATION_MISSING
SIGNATURE_MISSING
INSTALLATION_AUTHORIZATION_MISSING
PERSONAL_RUNTIME_AUTHORIZATION_MISSING
```

The normal runtime continues to register only:

```text
INTUITIVE
```

`METACOGNITIVE` also remains outside normal runtime and requires a separate review.

## Fail-closed transition

Passing the declarative gate in the future does not activate a motor automatically. `MorimilNormalIntrinsicRuntimeV0` deliberately fails construction if the current evidence becomes promotable while the registry has not been explicitly revised.

A later activation PR must separately provide:

1. independent deliberative-quality evidence with all required cases completed and zero false acceptances;
2. the exact producer checkpoint revision or independently verifiable producer mapping;
3. authenticated reproducible acquisition evidence for the exact upstream binary;
4. a verified and certified local artifact;
5. a valid artifact signature;
6. explicit installation authorization;
7. explicit personal runtime authorization;
8. a local engine wired through `VerifiedLocalDeliberativeCoreV01`;
9. normal-runtime tests on physical Android devices;
10. an explicit registry change reviewed as its own PR.

## Authority boundary

This gate and any future deliberative motor provide computation only. They grant no capability to write memory, alter identity or Genesis, control continuity, manage lifecycle, install artifacts, access providers or own final authority.

Morimil's deterministic kernel remains the final authority.
