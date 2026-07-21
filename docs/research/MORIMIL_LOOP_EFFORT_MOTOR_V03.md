# Morimil Loop-Effort Motor Research Contract v0.3

Status: **research-only**

Contract version: `morimil.deliberative.loop-effort.research.v0.3`

This document defines a separate experimental line for strengthening Morimil's local deliberative motor. It does not replace, certify, sign, install, promote, or activate any model artifact.

## Decision

Morimil keeps four responsibilities separate:

- Morimil owns identity, local memory, continuity, goals, lifecycle, routing, and final authority.
- The current Gemma 3n E2B candidate remains an immutable physical research baseline.
- LOTUS is an architecture reference for genuine recurrent latent computation.
- Inkling is an optional external teacher or evaluator for public, synthetic, or explicitly authorized data only.

Neither Inkling, LOTUS, Gemma, LiteRT-LM, nor another neural model becomes Morimil's identity or gains authority to write memory, Genesis, lifecycle, or installation state.

## Immutable v0.2 research baseline

The current baseline is not a stable production motor. It is the exact artifact that completed bounded physical Android ARM64 research profiles:

```text
artifactVersion:          morimil-deliberative-v0.2
filename:                 morimil-deliberative-v0.2.candidate.litertlm
sizeBytes:                3655827456
sha256:                   2ed7bc3a0026c93d5b8a4544b352d9d00cd66ff0bac3ef6a20ac3d2cba4010d6
artifactRepository:       google/gemma-3n-E2B-it-litert-lm
artifactRepositoryCommit: c03b6f60b8da6c5400b6838a2cf26420f80c0a01
sourceModelRevision:      null
runtime:                  LiteRT-LM 0.14.0
backend:                  CPU
certified:                false
signed:                   false
installed:                false
productionAuthorized:     false
```

The artifact-repository commit is not evidence of the exact source-model revision that produced the published artifact. `sourceModelRevision` remains `null` until supported by verifiable provenance.

## Technique boundaries

### Inkling

Research may study or reproduce these ideas:

- controllable reasoning effort;
- explicit compute-cost tradeoffs;
- calibration using proper scoring rules;
- abstention-aware rewards;
- separate rubric and factual-claim evaluators;
- synthetic data generation and critique.

Inkling is not a mobile runtime candidate. Any optional teacher use must exclude autobiographical memory, Genesis, identity state, private doctrine, credentials, and private conversations. Teacher output is evidence to validate, not authority.

### LOTUS

Research may study or adapt these mechanisms:

- multiple latent workspace blocks;
- recurrent reuse of the transformer backbone;
- hidden-state reinjection between iterations;
- parallel latent positions;
- intermediate supervision and final-answer supervision;
- final decoding after recurrent refinement;
- independently tunable latent width and recurrent depth.

The public LOTUS implementation and checkpoints are reference material, not Android-ready Morimil artifacts. Loading checkpoint weights without the recurrent wrapper does not establish LOTUS behavior.

### Licenses

The LOTUS source-code license does not automatically govern its base models, derived checkpoints, tokenizers, or datasets. Every input must record its own license, usage conditions, exact revision, and hashes. The same rule applies to Inkling, Gemma, and any teacher-generated dataset.

## Runtime capability vocabulary

A runtime must identify its state mechanism as exactly one of:

```text
TEXTUAL_CONVERSATION
LATENT_RECURRENT
```

A runtime may claim `LATENT_RECURRENT` only when it demonstrates both:

1. hidden-state reinjection between recurrent iterations; and
2. reuse of the same backbone weights across those iterations.

Repeated calls to a text conversation, self-revision prompts, or repeated decoding remain `TEXTUAL_CONVERSATION`, even when they improve an answer.

The Kotlin interface currently used by Morimil for LiteRT-LM does not expose hidden states or a hidden-state reinjection operation. This is a limitation of the current adapter boundary, not a claim that every future LiteRT-LM, C++, NDK, unrolled-graph, or custom runtime is permanently incapable of latent recurrence.

## Loop-effort dimensions

Research describes a candidate loop using:

```text
K = number of latent workspace blocks
c = latent positions per block
R = recurrent backbone iterations
```

Published LOTUS values are experimental results for particular models and datasets. They are not defaults for Morimil. Morimil must determine `K`, `c`, and `R` using its own quality, memory, latency, thermal, and energy measurements.

The research contract permits at most eight recurrent iterations. This preserves the existing bounded-effort ceiling while allowing future evidence to justify a lower limit.

## Stop reasons

Every experimental execution must finish with one explicit stop reason:

```text
CONVERGED
BUDGET_EXHAUSTED
MEMORY_LIMIT
THERMAL_LIMIT
ENERGY_LIMIT
INVALID_STATE
ENGINE_FAILURE
```

Only `CONVERGED` and `BUDGET_EXHAUSTED` may produce a candidate for later authority evaluation. Resource, invalid-state, and engine failures must fail closed.

## Candidate-base decision

Gemma 3n is the first candidate to investigate because the v0.2 artifact already runs on the target ARM64 device. It is not fixed as the final v0.3 base.

The feasibility study must be able to conclude any of:

```text
ADAPTABLE
ADAPTABLE_BUT_NOT_EXPORTABLE
MEMORY_LIMIT_EXCEEDED
QUALITY_REGRESSION
ARCHITECTURALLY_INCOMPATIBLE
```

If Gemma 3n cannot support the loop without unacceptable quality, export, memory, energy, or thermal regressions, research must evaluate another small mobile-compatible base or reject the latent-loop direction.

## Mandatory invariants

Every implementation governed by this contract must satisfy all of the following:

- request-scoped working state only;
- no persisted hidden state, KV cache, draft, or workspace;
- no autobiographical-memory writer;
- no identity or Genesis writer;
- no lifecycle or installation authority;
- no provider credential or endpoint authority;
- no direct acceptance of neural output;
- deterministic authority or an independent verifier remains above the motor;
- bounded iterations and bounded resource use;
- all sessions, engines, native buffers, and temporary files close or clean up;
- artifact and result hashes are recorded where applicable;
- normal runtime remains disabled;
- any unsupported or invalid condition fails closed.

## Benchmark and promotion boundary

The benchmark contract is defined in `MORIMIL_LOOP_EFFORT_BENCHMARK_V0.md`.

A v0.3 candidate may replace the v0.2 baseline only after a frozen holdout and physical ARM64 evaluation demonstrate, at minimum:

- higher observed accepted-correct performance than v0.2;
- zero observed false acceptances in the evaluated promotion set;
- statistical uncertainty reported rather than claiming zero real-world risk;
- fail-closed abstention;
- no regression in strict-format behavior;
- bounded p95 latency, PSS, temperature, thermal state, and unplugged energy use;
- stable artifact hash;
- complete release of request-scoped state;
- no memory writer or identity authority.

A failed or inconclusive experiment leaves v0.2 unchanged and inactive for normal production use.

## Fixed near-term sequence

Only these steps are fixed:

1. PR #29: define this research contract and executable capability invariants.
2. PR #30: add Morimil's comparative benchmark.
3. PR #31: freeze and record the exact v0.2 benchmark baseline.

Later PR numbers, model choices, training stages, export routes, and Android runtime designs remain provisional until those three steps produce evidence.

## Non-goals of this contract

This contract does not:

- include or download weights;
- invoke Inkling or another remote teacher;
- reproduce LOTUS;
- train or convert a model;
- change the current LiteRT-LM adapter;
- install or register an artifact;
- certify or sign an artifact;
- enable hybrid authority at runtime;
- modify Morimil identity, memory, Genesis, continuity, or lifecycle.

## Primary references

- Inkling announcement: https://thinkingmachines.ai/news/introducing-inkling/
- Inkling model card: https://thinkingmachines.ai/model-card/inkling/
- LOTUS paper: https://arxiv.org/abs/2606.31779
- LOTUS project: https://yingfan-bot.github.io/lotus/
- LOTUS source: https://github.com/yingfan-bot/lotus
- Gemma 3n overview: https://ai.google.dev/gemma/docs/gemma-3n
- LiteRT-LM source: https://github.com/google-ai-edge/LiteRT-LM
