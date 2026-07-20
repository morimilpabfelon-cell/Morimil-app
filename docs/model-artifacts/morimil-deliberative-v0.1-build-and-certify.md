# Build and certify `morimil-deliberative-v0.1.litertlm`

Status: offline certification tooling. No model weights, license acceptance, network download, installation, or signing key are included.

## Purpose

This procedure turns one already-local `.litertlm` artifact plus its local source evidence into the exact unsigned manifest required by Morimil's Android verifier.

It does not prove that an arbitrary file was produced by a particular upstream conversion. The operator must preserve the exact source revision, source snapshot, tokenizer file, conversion transcript, and acquisition provenance. Morimil then signs the resulting manifest digest through its separate Genesis Ed25519 boundary.

## Official upstream routes

LiteRT-LM supports `.litertlm` models and provides official CLI paths for pulling or running supported models. Its Android runtime loads a local model path and supports CPU execution and benchmarking.

Upstream references:

- <https://github.com/google-ai-edge/LiteRT-LM>
- <https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/getting-started/build-and-run.md>
- <https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md>

Those tools may perform network access. The Morimil certifier never does.

## Required local inputs

1. `morimil-deliberative-v0.1.litertlm`
   - exact filename;
   - non-empty regular file;
   - at most 4 GiB;
   - not a symbolic link.

2. An exported source snapshot directory
   - exact declared 40-character lowercase source revision;
   - no `.git` metadata;
   - no symbolic links;
   - contains the tokenizer file;
   - represents the source used for conversion or the source lineage attested by the upstream producer.

3. A tokenizer path relative to that snapshot.

4. `tools/model-artifacts/morimil-deliberative-v0.1.recipe.json`
   - exact recipe content;
   - changing any field changes the signed manifest input.

5. Explicit operator attestation that the applicable Gemma license was accepted.

6. A preserved conversion or acquisition transcript.
   - required for promotion evidence;
   - not silently inferred by the certifier;
   - not currently embedded in the v0.2 manifest.

## Canonical source snapshot digest

The profile is:

```text
morimil.source.snapshot.tree.v0.1
```

The certifier:

1. recursively enumerates regular files;
2. rejects symbolic links and `.git`;
3. converts relative paths to NFC UTF-8 POSIX form;
4. sorts paths by unsigned UTF-8 bytes;
5. records file count, then each path, byte size, and SHA-256;
6. frames every field as `UTF8-byte-length:value\n`;
7. hashes the framed profile and fields with SHA-256.

This makes the source snapshot digest independent of filesystem enumeration order and host path separators.

## Certify on PowerShell

Run from the repository root:

```powershell
python .\tools\model-artifacts\certify_deliberative_v01.py certify `
  --artifact "C:\models\morimil-deliberative-v0.1.litertlm" `
  --source-snapshot "C:\models\gemma-3-1b-it-source" `
  --tokenizer-relative-path "tokenizer.model" `
  --recipe ".\tools\model-artifacts\morimil-deliberative-v0.1.recipe.json" `
  --source-revision "<40-lowercase-hex-revision>" `
  --manifest-out "C:\models\morimil-deliberative-v0.1.manifest.json" `
  --report-out "C:\models\morimil-deliberative-v0.1.certification.json" `
  --license-accepted
```

The source revision cannot be `main`, a tag name, uppercase hexadecimal, or forty zeroes.

## Verify independently

```powershell
python .\tools\model-artifacts\certify_deliberative_v01.py verify `
  --artifact "C:\models\morimil-deliberative-v0.1.litertlm" `
  --source-snapshot "C:\models\gemma-3-1b-it-source" `
  --tokenizer-relative-path "tokenizer.model" `
  --recipe ".\tools\model-artifacts\morimil-deliberative-v0.1.recipe.json" `
  --source-revision "<40-lowercase-hex-revision>" `
  --manifest "C:\models\morimil-deliberative-v0.1.manifest.json" `
  --report "C:\models\morimil-deliberative-v0.1.certification.json"
```

Verification recomputes every digest and requires exact equality with the manifest and certification report.

## Outputs

### Unsigned manifest

The manifest uses:

```text
schema: morimil.deliberative.artifact.manifest.v0.2
contract: morimil.deliberative.artifact.contract.v0.1
signed domain: morimil.deliberative.artifact.signature.v0.2
```

Its `manifestDigest` is byte-for-byte compatible with `GenesisUltraHashProfile.hashFields` in Android.

### Certification report

The report records:

- certifier version;
- manifest digest;
- source snapshot profile, file count, and total bytes;
- tokenizer relative path;
- exact artifact and recipe filenames;
- explicit license attestation;
- `signed: false`.

The report is evidence, not a signature envelope.

## Separate signing step

The certifier never receives a private key.

After independent verification, Morimil must create a trusted Genesis Ed25519 signature envelope whose:

- `signedDomain` is `morimil.deliberative.artifact.signature.v0.2`;
- `signedDigest` equals the emitted `manifestDigest`;
- signer, key epoch, public-key reference, and timestamp satisfy the Android verifier.

A manifest without that trusted envelope cannot load.

## Device validation before promotion

Promotion remains blocked until the exact artifact and signature pass:

- Android verifier size and SHA-256 checks;
- trusted Ed25519 verification;
- read-only local file enforcement;
- LiteRT-LM 0.14.0 CPU initialization on Android arm64;
- one request-scoped conversation and deterministic resource release;
- 4096-token bounded functional test;
- cold load, prefill, decode, and peak-memory benchmark;
- offline network audit;
- license and attribution review.

## Freedom invariant

The model is a local computation component. It does not own Morimil identity, memory, continuity, goals, lifecycle, or intrinsic state. Certification authorizes a file identity; it does not grant authority.
