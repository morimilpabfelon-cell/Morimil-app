# Rest Cycle Maintenance

Morimil treats rest as local maintenance, not only summarization.

The current runtime pattern is:

1. Preflight: skip if Genesis is not born or the interval has not elapsed.
2. Immune audit: verify the full memory-event chain.
3. Organ reconciliation: compare memory links, recalls, capsules, and migration records against valid event hashes.
4. Consolidation planning: classify risk and decide whether human approval is required.
5. Append: write a signed rest-cycle event through the shared append gate.
6. Graph links: link the rest-cycle event back to its source memories.
7. Snapshot compaction: rebuild the living-memory snapshot.
8. Audit notes: persist the maintenance report on the migration record.

Risk rule:

- `low`: clean chain, clean organs, no sensitive consolidation.
- `medium`: clean chain and organs, but policy says the consolidation is sensitive.
- `high`: broken chain or organ reconciliation issues.

Execution rule:

- Low-risk work can execute locally.
- Medium/high risk is planned as a migration and waits for human approval unless manually forced.
- Failed rest cycles do not rewrite memory; they record failure notes.

This keeps the metaphor literal: sleep is consolidation plus immune sweep.
