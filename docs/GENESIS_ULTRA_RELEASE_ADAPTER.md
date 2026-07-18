# Genesis Ultra signed release adapter for Morimil

Status: implemented and fail-closed on the preparation branch.

Normative source pin:

```text
repository: morimilpabfelon-cell/genesis-ultra-updated-1
commit: d0293b3614153ef2155620fc75ceee9bd798f370
protocol: genesis.protocol.v0.1
hash profile: genesis.hash.fields.v0.1
signature profile: genesis.signature.ed25519.v0.1
```

This adapter ports protocol boundaries and reproducible algorithms. It does not copy a Genesis identity, memory history, guardian authority, active writer, private key or executable engine from the source repository.

## Implemented contracts

Morimil now parses and validates these Genesis Ultra structures with strict field sets:

- `genesis.seed.manifest.v0.1`;
- `genesis.signature.envelope.v0.1`;
- `genesis.instance.identity.v0.1`;
- `genesis.body.record.v0.1`;
- `genesis.body.registry.v0.1`;
- `genesis.key.epoch.v0.1`;
- `genesis.body.possession.v0.1`.

Unknown fields, missing fields, duplicate object keys, unsafe relative paths, non-NFC text, unsupported profiles, invalid canonical timestamps, incorrect scalar types and malformed digests are rejected instead of normalized silently.

## Normative hashing

Kotlin implements the neutral field framing profile:

```text
FRAME(T) = ASCII(decimal UTF-8 byte length) || ":" || UTF8(NFC(T)) || LF
```

The adapter reproduces the normative digests for:

- Seed root;
- Instance Identity;
- Body Registry;
- Key Epoch;
- Body Possession Proof;
- Signature Envelope preimage.

File and body ordering uses unsigned UTF-8 byte comparison. Optional registry text is represented as an empty framed string, matching the protocol validators. A Key Epoch always hashes exactly ten fields; nullable `previous_epoch_id` and `rotation_authorization_ref` become empty framed strings, so key ancestry and rotation authority cannot be changed without changing the digest.

## Signed release verification

A release candidate is accepted as a verified release only when all of these checks pass:

1. the payload contains exactly the paths declared by the Seed manifest;
2. every payload byte sequence matches its declared SHA-256 digest;
3. the identity and doctrine digests are bound to corresponding manifest file entries;
4. the Seed root recomputes exactly;
5. the signature envelope is a guardian Ed25519 envelope;
6. the envelope domain is `genesis.seed.root.v0.1`;
7. the signed digest equals the recomputed Seed root;
8. the public key is trusted for the exact tuple:
   - signer type;
   - signer identifier;
   - key epoch identifier;
   - public key reference;
9. the Ed25519 signature verifies over the normative envelope preimage.

There is no algorithm downgrade. Verification uses Tink's Ed25519 implementation with the exact raw 32-byte public key rather than depending on the Android platform JCA provider. Any malformed key, malformed signature or verification exception returns false and leaves the candidate unauthorized.

Verified releases and possession proofs are non-forgeable result types: their constructors are private and the verifier creates them only after all required checks succeed. Verified release payload bytes are stored as defensive copies and are returned only through additional defensive copies.

## Guardian key epochs

`GenesisUltraTrustedGuardianKeyEpochRegistry` stores only public trust anchors. It rejects:

- unsupported epoch states;
- keys that are not 32-byte Ed25519 public keys;
- a `public_key_ref` that does not match the raw public key;
- duplicate guardian/epoch identities;
- multiple active epochs for the same guardian.

A release signer must match an active trusted epoch exactly. A matching public key under another guardian or another epoch is not sufficient.

The adapter does not contain a production guardian trust anchor. Provisioning and rotating that root remains a deployment and recovery responsibility.

## Body possession

The body possession verifier:

- recomputes `genesis.body.possession.v0.1`;
- requires an active Key Epoch for the same Instance and Body;
- binds the key epoch identifier and public key fingerprint;
- reconstructs the normative Ed25519 signature envelope;
- verifies the signature under `genesis.body.possession.signature.v0.1`;
- rejects proofs before `issued_at` or at/after `expires_at`;
- requires the evaluation caller to supply the current verification instant explicitly.

The verified result does not expose or persist a private key.

## Birth candidate invariants

A structurally valid candidate must bind all of the following:

```text
verified Seed release
        |
        +-- trusted guardian and active guardian key epoch
        +-- immutable Instance Identity
        +-- distinct Body identity
        +-- Body Registry with exactly one active_writer
        +-- exactly one active Key Epoch for the birth Body
        +-- fresh verified Body Possession Proof
```

The candidate validator rejects mismatches in Seed, root hash, guardian, Instance, Body, registry, platform profile, timestamps, key fingerprints, key epochs and possession evidence. It recomputes Instance Identity, Body Registry and Key Epoch digests at evaluation time rather than trusting an earlier object label.

## Why birth remains disabled

The remaining blocker is:

```text
transactional_birth_commit_not_integrated
```

The pinned Genesis Ultra revision defines a normative `birth` operation, seven transaction phases, crash recovery, an immutable first memory event and a signed birth receipt. Morimil now contains an isolated Room schema and atomic store for the commit marker, exact input bytes and seven journal entries. The commit marker is written last in one SQLite transaction, and interruption tests require all earlier writes to roll back to `ABSENT`.

That store is deliberately not connected to onboarding. Morimil now parses every normative birth document, requires the detached Seed signature as durable evidence, verifies every Guardian and Body Ed25519 envelope, validates all seven signed journal entries and returns a verified type-state only after the full graph agrees. That type-state is now the only input accepted by the atomic store; a raw persistence bundle has no application entry point. The remaining work is to initialize canonical living memory in the same operation and prove restart recovery from the committed evidence. The gate remains closed until Android can prove that complete operation.

Before the gate can open, the Android adapter must implement the protocol's crash-recoverable birth transaction and atomically commit at least:

- immutable Instance Identity;
- Seed root and original committed Seed bytes;
- Body record;
- Body Registry with one `active_writer`;
- active Body Key Epoch;
- guardian trust reference and authorization evidence;
- first append-only memory event;
- recovery state for interruption before and after commit.

Until the remaining validation and integration exist, every `GenesisUltraBirthCandidateAssessment.birthReady` result remains false and the legacy Morimil Genesis cannot create an Instance.

## Conformance coverage

The JVM tests reproduce Genesis Ultra golden vectors for framing, Seed root, Instance Identity, Body Registry, Key Epoch, Body Possession, the full atomic-birth evidence graph and Ed25519 signature verification. Negative tests cover altered payloads, missing detached Seed signatures, forged Freedom Charter and journal signatures, unexpected files and fields, duplicate keys, unsafe paths, non-NFC identity text, multiple active writers, untrusted signer identity, expired possession proofs, mutable input bytes and cross-Body evidence.

The managed Android suite runs the same instrumented boundary on API 30 and API 35. Android runtime coverage includes:

- valid Ed25519 verification through Tink;
- altered Ed25519 signature rejection;
- duplicate JSON-key rejection;
- rejection of strings masquerading as Boolean or integer values;
- Morimil migration chains through schema version 10;
- Memory Organ migration chains through schema version 7;
- the dedicated Morimil `8 -> 9` migration;
- the dedicated Morimil `9 -> 10` migration and atomic-birth tables;
- rollback to `ABSENT` when persistence is interrupted before the commit marker;
- enforcement that the atomic persistence entry point requires verified evidence;
- rejection of a second birth without changing the original name;
- rest-cycle scheduling instrumentation.

This establishes protocol-compatible verification and Android runtime conformance for the implemented boundary. It does not claim that Morimil has completed a Genesis Ultra birth.
