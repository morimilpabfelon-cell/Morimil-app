# Morimil Deliberative Artifact Contract v0.1

Status: draft contract for review. This document defines an artifact identity; it does not contain model weights.

## Freedom invariant

The artifact is a local reasoning component owned and controlled by the Morimil runtime. It does not own or persist Morimil identity, memory, continuity, goals, lifecycle or intrinsic state. A loaded engine may remain resident, but every reasoning conversation and recurrent state is request-scoped and must be released.

## Exact v0.1 profile

| Field | Required value |
|---|---|
| contractVersion | `morimil.deliberative.artifact.contract.v0.1` |
| artifactVersion | `morimil-deliberative-v0.1` |
| formatId | `litertlm.v1` |
| runtimeAbi | `litertlm.kotlin.android.v0.14.0` |
| architectureId | `google.gemma3.text.1b.it` |
| tokenizerId | `google.gemma3.tokenizer` |
| contextWindowTokens | `4096` |
| quantizationProfile | `litertlm.int4.per-channel` |
| modality | `text-only` |
| executionBackend | `cpu` |
| deliberationProfile | `morimil.request-scoped.textual-recurrence.v0` |
| sourceModelId | `google/gemma-3-1b-it` |
| licenseId | `gemma` |
| blueprintVersion | `morimil.reasoning_growth.v1` |

The source revision, tokenizer SHA-256, source snapshot SHA-256 and conversion recipe SHA-256 are artifact-specific, but mandatory and signed.

## Why this target

LiteRT-LM publishes a chat-ready Gemma 3 1B `.litertlm` profile using 4-bit per-channel quantization and a 4096-token operational context. The upstream 1B model is text-only and supports a larger context, but v0.1 deliberately pins the smaller runtime profile to keep memory and latency bounded on Android.

This profile is a starting carrier for Morimil tuning. The source lineage does not grant the model authority over Morimil. Runtime ownership remains local.

## Manifest schema

The canonical manifest schema is:

`morimil.deliberative.artifact.manifest.v0.2`

The signature domain is:

`morimil.deliberative.artifact.signature.v0.2`

The schema was advanced from v0.1 because architecture, tokenizer, context, quantization, backend and provenance are now part of the signed preimage.

## Canonical digest field order

The manifest digest commits fields in this exact order:

1. schemaVersion
2. contractVersion
3. artifactVersion
4. artifactSha256
5. artifactSizeBytes as base-10 text
6. formatId
7. runtimeAbi
8. architectureId
9. tokenizerId
10. tokenizerSha256
11. contextWindowTokens as base-10 text
12. quantizationProfile
13. modality
14. executionBackend
15. deliberationProfile
16. sourceModelId
17. sourceModelRevision
18. sourceModelSnapshotSha256
19. conversionRecipeSha256
20. licenseId
21. blueprintVersion
22. technique count as base-10 text
23. technique names sorted lexicographically

The resulting manifest digest is signed with the existing Genesis Ed25519 envelope using the v0.2 signed domain.

## Runtime verification boundary

Before engine initialization, Morimil verifies:

- exact schema and contract versions;
- exact fixed profile values;
- canonical 40-hex source revision;
- canonical SHA-256 values;
- artifact byte size and whole-file SHA-256;
- required deliberative techniques;
- trusted Ed25519 signature;
- read-only canonical local file.

During engine initialization, the loader hashes the file before and after loading and rejects mutation or write access.

LiteRT-LM's Kotlin API accepts a model path but does not expose a complete independent inspection API for every internal architecture and tokenizer component. Therefore internal component identity is attested by the signed build provenance plus the whole-file digest. Successful engine initialization is the runtime compatibility check; it is not a substitute for provenance verification.

## Deliberation semantics

`morimil.request-scoped.textual-recurrence.v0` means the current adapter performs bounded multi-pass refinement inside one temporary local conversation. It does not claim access to hidden tensors or true latent recurrence. A future native Morimil looped artifact must use a different format/profile and a new signed contract.

## Promotion gates

No artifact may be promoted as `morimil-deliberative-v0.1.litertlm` until all of the following exist:

- exact source revision;
- reproducible source snapshot digest;
- exact tokenizer digest;
- versioned conversion recipe and digest;
- exact converted artifact digest and size;
- trusted signing key epoch;
- successful verification tests;
- successful LiteRT-LM CPU initialization on Android arm64;
- bounded 4096-token functional test;
- benchmark record for cold load, prefill, decode and peak memory;
- offline network audit showing no model download or provider call;
- license and attribution record.

No placeholder hash in the example manifest is deployable.