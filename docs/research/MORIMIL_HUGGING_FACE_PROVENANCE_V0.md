# Morimil Hugging Face provenance v0.1

Status: **authenticated capture pending; normal-runtime activation blocked**

## Purpose

This contract establishes the supply-chain provenance of the exact Deliberative v0.2 candidate without confusing three different revisions:

1. the Hugging Face repository snapshot previously observed by Morimil;
2. the commit pinned by Google's official AI Edge Gallery allowlist for the exact `.litertlm` file;
3. the deeper Transformers checkpoint revision used by Google to produce that binary.

Only the first two are currently public and exact. The third remains undisclosed and must not be invented.

## Official upstream identity

```text
Publisher:                    Google
Hugging Face repository:      google/gemma-3n-E2B-it-litert-lm
Gated:                        true
License:                      gemma
Artifact file:                gemma-3n-E2B-it-int4.litertlm
Artifact revision:            ba9ca88da013b537b6ed38108be609b8db1c3a16
Artifact size:                3655827456 bytes
Observed repository snapshot: c03b6f60b8da6c5400b6838a2cf26420f80c0a01
```

Google's official AI Edge Gallery allowlist pins the model entry in:

```text
repository: google-ai-edge/gallery
revision:   126501c8849affcfb094d2c5b193aa5deb1434a6
path:       model_allowlists/1_0_15.json
```

The local Morimil candidate is:

```text
filename: morimil-deliberative-v0.2.candidate.litertlm
size:     3655827456 bytes
SHA-256:  2ed7bc3a0026c93d5b8a4544b352d9d00cd66ff0bac3ef6a20ac3d2cba4010d6
```

Matching names and sizes is not enough to establish byte identity. The authenticated capture tool requires Hugging Face LFS metadata to expose the same SHA-256.

## Acquisition classification

Morimil did not convert the model.

```text
acquisition mode:                 DIRECT_UPSTREAM_BINARY_RENAME_ONLY
conversion performed by Morimil: false
rename only:                      true
```

Therefore the reproducible operation under Morimil's control is acquisition, not conversion:

```text
exact repository
+ exact artifact revision
+ exact filename
+ exact authenticated LFS SHA-256
+ exact size
= reproducible acquisition
```

The normal-runtime activation gate must require `REPRODUCIBLE_ACQUISITION_EVIDENCE_MISSING` until this capture exists. It must not demand conversion evidence for a conversion Morimil never performed.

The producer's mapping from the prebuilt `.litertlm` file to a specific `google/gemma-3n-E2B-it` Transformers commit remains a separate upstream provenance question.

## Strict evidence schema

The active schema is:

```text
schema version:
morimil.deliberative.v0.2.hf-acquisition-evidence.v0.1

schema path:
docs/model-artifacts/morimil-deliberative-v0.2-hf-acquisition-evidence-v0.1.schema.json

capture tool:
morimil.hf-provenance-capture.v0.1
```

The schema and Python validator are fail-closed:

- every root and nested object has an exact key set;
- unknown or contradictory claims are rejected;
- artifact SHA-256, size, names and revisions are constants;
- the Google allowlist section is exact;
- authority and promotion flags must remain false;
- promotion blockers must match exactly;
- `capturedAtUtc` must be a canonical UTC timestamp;
- the recorded Hugging Face identity must be a public username, never an email;
- the schema document is generated from the same constants and checked during tests.

The superseded `v0` schema was removed because it contained an incorrect SHA-256 constant and did not close all nested objects.

## Authenticated capture on Windows

The repository is gated. Accept the Gemma license in the Morimil Hugging Face account and expose the token only through the current PowerShell process:

```powershell
$env:HF_TOKEN = "hf_..."
python -m pip install huggingface_hub

python .\tools\model-artifacts\capture_gemma3n_e2b_hf_provenance_v0.py capture `
  "C:\Users\morim\MorimilModels\gemma3n-e2b-candidate-20260720-001337\morimil-deliberative-v0.2.candidate.litertlm" `
  --output ".\build\morimil-deliberative-v0.2-hf-acquisition-evidence-v0.1.json"

Remove-Item Env:HF_TOKEN
```

The tool accepts the token only from `HF_TOKEN`. It has no `--token` argument and does not read a second legacy token variable. This prevents accidental exposure in shell history or process arguments.

The generated evidence records:

```text
public Hugging Face username
huggingface_hub client version
capture timestamp
exact Hub and local artifact identity
```

It never records the token, email, full name or credentials.

Validate the evidence and local artifact:

```powershell
python .\tools\model-artifacts\capture_gemma3n_e2b_hf_provenance_v0.py check `
  ".\build\morimil-deliberative-v0.2-hf-acquisition-evidence-v0.1.json" `
  --artifact "C:\Users\morim\MorimilModels\gemma3n-e2b-candidate-20260720-001337\morimil-deliberative-v0.2.candidate.litertlm"
```

## Fail-closed outcomes

Capture stops when any of these differ:

- schema or capture-tool version;
- repository;
- artifact commit;
- filename;
- LFS SHA-256;
- size;
- gated status;
- Gemma license;
- observed repository snapshot;
- Google allowlist identity;
- local candidate filename, hash or size;
- exact authority boundary;
- exact promotion blockers;
- public authenticated username;
- canonical UTC timestamp;
- any unknown field.

A passing capture proves only exact upstream binary identity and reproducible acquisition. It does not prove the undisclosed base-checkpoint revision, model quality, certification or safe normal-runtime use.

## Authority boundary

A passing capture cannot:

```text
certify:                        false
sign:                           false
install:                        false
authorize personal activation: false
activate normal runtime:        false
write memory:                   false
change identity:                false
change lifecycle:               false
```

The generated evidence must retain:

```text
UPSTREAM_BASE_CHECKPOINT_REVISION_UNDISCLOSED
```

until Google publishes an exact mapping or Morimil obtains independently verifiable producer evidence.

The frozen 84/36/0 hybrid benchmark remains separate. It demonstrates containment by Morimil's deterministic authority; it does not clear the raw deliberative candidate's 40 false acceptances.
