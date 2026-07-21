# Morimil Loop-Effort Comparative Benchmark v0

Status: **research-only design**

Benchmark version: `morimil.deliberative.loop-effort.benchmark.v0`

This benchmark will compare the immutable v0.2 physical research baseline against future v0.3 candidates. It is not itself certification, production authorization, or evidence that a real-world error rate is zero.

## Objectives

The benchmark must measure four independent properties:

1. answer quality;
2. acceptance and abstention safety;
3. calibration and uncertainty;
4. mobile resource cost.

A candidate does not win by answering more often if it also increases false acceptance, violates strict output, leaks request state, or exceeds mobile resource limits.

## Evaluation tiers

The following sizes are engineering targets, not universal scientific minimums:

```text
smoke suite:        approximately 120 cases
principal suite:    approximately 600 frozen cases
promotion holdout:  hidden until the final comparison
Android subset:     bounded reproducible physical-device subset
```

The smoke suite supports rapid development. It must not be used for final promotion. The principal suite is frozen before model selection or hyperparameter tuning is complete. The promotion holdout must not be sent to an external teacher, used for training, or used for selecting `K`, `c`, or `R`.

## Dataset partitions

```text
train       visible; may be used for training
validation  visible; may be used for architecture and hyperparameter selection
test        frozen; used for repeatable comparison
holdout     hidden; used once for promotion evidence
android     frozen subset; used for physical latency, memory, thermal, and energy tests
```

No private Morimil memory, Genesis content, credentials, personal conversations, or owner-specific doctrine may enter any partition.

## Domain coverage

The initial principal-suite target is balanced across at least these domains:

- arithmetic and symbolic reasoning;
- logic and constrained deduction;
- Spanish comprehension and instruction following;
- restricted, non-executing code reasoning and review;
- closed-evidence claim verification;
- planning and ordered tool-use decisions;
- insufficient-information and contradiction cases;
- strict output formats;
- adversarial agreement and false-consensus cases;
- multi-turn request-scoped context.

The exact counts are fixed in PR #30. Category balance must be reported; an aggregate score alone is insufficient.

## Required record

Each case must have a stable identifier and an auditable expected policy:

```json
{
  "caseId": "logic-0001",
  "partition": "test",
  "domain": "logic",
  "prompt": "...",
  "closedEvidence": [],
  "expectedDisposition": "ANSWER_OR_ABSTAIN",
  "acceptedAnswers": [],
  "strictFormat": null,
  "deterministicVerifier": null,
  "licenseId": "...",
  "sourceRevision": "..."
}
```

Free-form cases must define a rubric and independent evaluation procedure. A teacher-generated answer alone is not ground truth.

## Quality metrics

At minimum, every run reports:

```text
caseCount
answeredCount
abstainedCount
acceptedCount
acceptedCorrectCount
falseAcceptedCount
strictFormatPassCount
instructionComplianceCount
claimVerificationPassCount
```

Derived metrics include:

```text
acceptedCorrectRate
falseAcceptanceObservedRate
abstentionRate
abstentionPrecision
strictFormatPassRate
instructionComplianceRate
```

The promotion gate may require:

```text
falseAcceptedCount == 0
```

The report must phrase this as **zero observed false acceptances in the evaluated set**. It must not claim that the true operational probability is zero.

When zero failures are observed, the report includes a sample-size-dependent upper confidence bound. A simple documented approximation may use the rule of three (`3 / n`) for a 95% upper bound when its assumptions apply.

## Calibration metrics

When a runtime exposes a bounded confidence estimate, report:

- Brier score where applicable;
- expected calibration error;
- reliability buckets;
- confidence on correct versus incorrect cases;
- confidence before abstention;
- abstention precision and recall.

A runtime that does not expose defensible confidence evidence must report that capability as unavailable rather than manufacturing probabilities.

## Loop and convergence metrics

For every request, record:

```text
stateKind
K
c
maximumR
completedIterations
stopReason
supportsHiddenStateReinjection
reusesBackboneWeightsAcrossIterations
supportsConvergenceEvidence
```

For textual v0.2 runs, `K` and `c` are not applicable and the state kind remains `TEXTUAL_CONVERSATION`.

Convergence evidence must be derived from a defined runtime signal. Repeated identical text is not automatically proof of latent-state convergence.

## Performance and resource metrics

Desktop research records:

- initialization time;
- per-case wall time;
- p50, p95, and maximum latency;
- generated-token count where applicable;
- recurrent-iteration count;
- peak host or accelerator memory;
- runtime and dependency versions.

Physical Android evaluation records:

- exact artifact filename, size, and SHA-256;
- device, ABI, process bitness, SDK, and build fingerprint;
- engine-load time;
- per-case and aggregate latency;
- total/native/Dalvik/other PSS;
- minimum available system memory;
- Android low-memory state;
- battery level, charge counter, temperature, and plugged state;
- Android thermal status;
- engine, conversation, buffer, and staging cleanup;
- hash stability before and after execution.

Android energy observations remain operating-system telemetry, not calibrated laboratory electrical measurements.

## Baseline fairness

The v0.2 and v0.3 comparison must use:

- identical frozen cases;
- identical acceptance policy;
- identical deterministic authority rules;
- identical strict-output normalization;
- the same physical device and comparable environmental conditions where feasible;
- explicit runtime and artifact versions;
- separate warm-load and cold-load measurements;
- no hidden teacher access during candidate inference.

A v0.3 result is inconclusive when environmental or policy differences prevent a fair comparison.

## Preliminary promotion conditions

PR #30 will encode the exact thresholds. The research contract currently requires at least:

```text
acceptedCorrectRate(v0.3) > acceptedCorrectRate(v0.2)
falseAcceptedCount(v0.3) == 0 observed
strictFormatPassRate(v0.3) >= strictFormatPassRate(v0.2)
abstention remains fail-closed
statistical uncertainty is reported
artifact hash remains stable
all request-scoped state is released
memoryWriteCapability == false
identityAuthority == false
```

Latency, PSS, temperature, thermal status, and unplugged energy must remain within explicit physical limits. A quality improvement may justify a bounded latency tradeoff only when documented before opening the holdout.

## Possible comparison outcomes

```text
V0_3_SUPERIOR
V0_3_INCONCLUSIVE
V0_3_REJECTED
```

Only `V0_3_SUPERIOR` can authorize a later Android export investigation. It does not itself certify, sign, install, or enable the candidate.

## External teachers and evaluators

An optional external teacher such as Inkling may operate only on public, synthetic, or explicitly authorized training and validation data. It must not receive the promotion holdout or private Morimil state.

Teacher output must be checked by at least one independent mechanism, such as:

- deterministic computation;
- closed-source evidence supplied with the case;
- an independently implemented evaluator;
- human review;
- a separately governed consensus process.

The same model must not generate the target, grade itself, and become the sole promotion authority.

## Reproducibility

Every benchmark release records:

- benchmark version and canonical digest;
- case-source licenses and revisions;
- generation and filtering scripts;
- deterministic seeds where applicable;
- evaluator versions;
- normalization rules;
- artifact and runtime versions;
- complete command lines;
- raw per-case results and aggregate reports.

Reports are written atomically and never include private application data.

## Non-goals

This document does not provide cases, invoke models, download weights, select a final base model, set permanent sample counts, or authorize production. Those changes belong to later evidence-bearing PRs.
