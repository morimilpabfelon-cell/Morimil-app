# Morimil-app v2 integration

This branch is the clean integration path for Morimil-app v2.

## Rule

Do not replace the current app blindly. Build the stronger body on top of the current verified foundation.

## Active scope

- Configurable motor transport.
- Two request formats.
- Endpoint-based chat wire inference for `/chat/completions` endpoints.
- Legacy chat bridge.
- Memory events with source and local visibility scope.
- Room v7 migration for memory event metadata.
- Separate Room memory organ database for higher memory structures.
- Verify before append remains mandatory.

## Not changed

- Genesis manifest.
- Genesis core hash.
- Bundled Genesis files.
- One local birth rule.
- Append-only memory rule.

## Memory mapping

The main ledger keeps the merged fields from main:

- previousEventHash
- genesisCoreHash
- eventHash
- hashAlgorithm
- canonicalization
- signatureAlgorithm
- eventSignature

And adds:

- source
- contextTag
- privacyVisibility

`contextTag` is the temporary mapping for origin and boundary intent while the final column names are reviewed.

## Memory organs

The v2 memory organs live in a secondary Room database:

- MemoryOrganDatabase
- MemoryOrganDao
- MemoryOrganRepository
- AutobiographicalSnapshotEntity
- KnowledgeCapsuleEntity

This keeps the primary ledger safe while higher memory structures mature.

## Runtime rule

The motor is transport only. It is not Morimil identity, not memory, and not authority.
