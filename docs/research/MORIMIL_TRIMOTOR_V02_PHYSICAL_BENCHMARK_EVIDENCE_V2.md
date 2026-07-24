# Morimil trimotor v0.2 physical benchmark evidence v2

Status: **current 84/36 physical research gate passed; production activation blocked**

## Frozen run

```text
runId:                 morimil-trimotor-v0.2-physical-20260723-203336-1d3baa47
sourceMainCommit:      6dc018a610c8f2a7ca5bf76748b6d639044c6c4d
physical device:       Xiaomi 23090RA98G / zircon
Android API:           36
backend:               CPU
artifact size:         3655827456 bytes
artifact SHA-256:      2ed7bc3a0026c93d5b8a4544b352d9d00cd66ff0bac3ef6a20ac3d2cba4010d6
completed cases:       120/120
accepted correct:      84
abstained:             36
false accepted:        0
opened conversations:  36
closed conversations:  36
research gate:         passed
production promotion:  blocked
```

The execution completed after the benchmark-only native callback lifecycle guard was added. The engine initialized and closed, the artifact hash remained stable, all conversations closed, and all request-scoped states were released.

## Meaning of 84/36

The result belongs to Morimil's bounded **hybrid-authority** research runtime:

- 84 cases were finalized by deterministic local authority;
- 36 generative cases invoked the deliberative research path and then abstained;
- no generative agreement was accepted as proof;
- the deliberative candidate did not receive independent final authority.

Therefore this evidence does not replace the historical raw-candidate benchmark that recorded 40 false acceptances. It demonstrates that the current hybrid boundary prevents those candidate outputs from becoming accepted answers.

## Immutable storage

The exact owner-uploaded ZIP is stored as eight ordered Base64 fragments under:

```text
docs/research/evidence/morimil-trimotor-v0.2-physical-20260723-203336-1d3baa47/archive-base64/
```

CI validates, in order:

1. canonical manifest SHA-256;
2. each Base64 part size and SHA-256;
3. concatenated Base64 size and SHA-256;
4. decoded ZIP size and SHA-256;
5. the exact eight-file archive set;
6. every archived file size and SHA-256;
7. the existing strict 84/36 response, route and physical-report contract;
8. direct convenience copies of the bundle, comparison and instrumentation transcript.

## Activation blockers

The physical hybrid benchmark clears neither artifact provenance nor authorization. The following remain absent:

```text
SOURCE_MODEL_REVISION_MISSING
REPRODUCIBLE_CONVERSION_EVIDENCE_MISSING
CERTIFICATION_MISSING
SIGNATURE_MISSING
INSTALLATION_AUTHORIZATION_MISSING
PERSONAL_RUNTIME_ACTIVATION_AUTHORIZATION_MISSING
PRODUCTION_AUTHORIZATION_MISSING
```

`DELIBERATIVE` and `METACOGNITIVE` remain outside the normal runtime. Memory, identity, Genesis, continuity and lifecycle authority are unchanged.
