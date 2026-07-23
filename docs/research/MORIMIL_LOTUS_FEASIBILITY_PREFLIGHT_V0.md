# Morimil LOTUS feasibility preflight v0

Status: **research-only; no model execution**

This preflight is the first evidence-bearing step after the v0.3 loop-effort
contract. It fixes the exact public LOTUS source and model reference before any
GPU spend, weight download, training, conversion or Android implementation.

## Decision

LOTUS remains the selected architecture reference for Morimil's future
deliberative motor. The reference is verified, but an experiment is not yet
authorized to claim that LOTUS works for Morimil, Gemma 3n or Android.

```text
preflight decision:  REFERENCE_VERIFIED_EXPERIMENT_BLOCKED
normal runtime:      unchanged
GPU used:            false
weights downloaded: false
training run:        false
model inference:     false
```

## Pinned upstream source

```text
repository: https://github.com/yingfan-bot/lotus.git
commit:     eb77e2f7909c5006f58ff0ad7cd6629b942caa9e
license:    MIT for the source repository
```

The capture tool verifies the commit, clean worktree, origin and SHA-256 of the
source, evaluation, dependency and configuration files needed to understand the
published experiment.

## Pinned Hugging Face reference

```text
model:      yingfanbot/gsm-lotus-llama3b
revision:   b392d2cb7aaa73475b93028221523c47f49f66a2
base model: meta-llama/Llama-3.2-3B-Instruct
license tag reported by derived repository: mit
```

The two model shards and tokenizer LFS objects are identified by exact size and
SHA-256 through public Hugging Face metadata. The preflight does not download
them.

The derived repository's `mit` tag does not settle the complete license chain.
Llama 3.2 has its own license, and the augmented GSM8K training lineage must
also be reviewed independently. Both remain explicit blockers.

## What was extracted

The pinned upstream code demonstrates these desktop reference mechanisms:

- hidden-state reinjection between loop iterations;
- reuse of one backbone inside the loop;
- fixed padded latent workspace;
- direct output through the base language-model head;
- published Llama 3.2 3B profile using latent width `25` and `6` loop
  iterations.

Those values describe the published GSM8K experiment. They are not Morimil
defaults and must not be copied into the Android runtime without evidence.

## Current incompatibility boundary

Morimil's current Gemma 3n E2B LiteRT-LM adapter exposes a request-scoped text
conversation. It does not expose hidden tensors or a hidden-state reinjection
operation. Therefore:

```text
current adapter state kind:             TEXTUAL_CONVERSATION
hidden-state reinjection:               false
shared backbone inside a latent loop:   false
genuine LOTUS claim on current adapter: forbidden
```

This blocks direct integration. It does not prove that Gemma 3n itself can
never be adapted through a different export graph, native runtime or model
architecture.

## Reproduce the preflight

Clone the exact upstream source outside this repository, then run:

```text
python tools/model-artifacts/lotus_feasibility_preflight_v0.py capture \
  /path/to/lotus \
  --output build/morimil-lotus-feasibility-preflight-v0.json
```

Validate the frozen evidence and source checkout:

```text
python tools/model-artifacts/lotus_feasibility_preflight_v0.py check \
  docs/model-artifacts/morimil-lotus-feasibility-preflight-v0.json \
  --checkout /path/to/lotus
```

The command accesses only public repository metadata. It does not accept a
model token, invoke a teacher, download weights or use Morimil data.

## Next gate

The next experiment may reproduce the pinned LOTUS checkpoint on desktop only
after the license chain and compute budget are explicitly accepted. Its output
must be compared against the immutable v0.2 baseline and must not use:

- private Morimil memory;
- Genesis or identity material;
- personal conversations;
- the hidden promotion holdout;
- a remote teacher as sole evaluator.

No result from that experiment can install, sign, promote or activate a model.
