# Rest Cycle Maintenance

Morimil treats rest as local maintenance, not only summarization.

The current runtime pattern is:

1. Preflight: skip if Genesis is not born or the interval has not elapsed.
2. Immune audit: verify the full memory-event chain.
3. Organ reconciliation: compare memory links, recalls, capsules, and migration records against valid event hashes.
4. Capsule-chain audit: verify the knowledge-capsule hash chain.
5. Consolidation planning: classify risk and decide whether human approval is required.
6. Append: write a signed rest-cycle event through the shared append gate.
7. Graph links: link the rest-cycle event back to its source memories.
8. Snapshot compaction: rebuild the living-memory snapshot.
9. Audit notes: persist the maintenance report on the migration record.
10. Local notice: show a device notification after automatic maintenance when notification permission is available.

Scheduling:

- `RestCycleWorker` is registered through WorkManager as unique periodic work.
- It runs every 6 hours with a 1-hour flex window.
- It requires no network, but waits for battery and storage to be healthy.
- The Memory tab exposes scheduler state, manual execution, agenda activation, refresh, and pause controls.
- Android 13+ notifications are permission-gated; if permission is missing, maintenance continues silently.

Risk rule:

- `low`: clean chain, clean organs, no sensitive consolidation.
- `medium`: clean chain and organs, but policy says the consolidation is sensitive.
- `high`: broken memory chain, broken capsule chain, or organ reconciliation issues.

Execution rule:

- Low-risk work can execute locally.
- Medium/high risk is planned as a migration and waits for human approval unless manually forced.
- Failed rest cycles do not rewrite memory; they record failure notes.
- Orphaned memory links are marked `orphaned`.
- Orphaned recalls are degraded instead of deleted.
- Broken capsule chains raise risk; they are not auto-rewritten.

Tests:

- JVM tests cover scheduler status classification and maintenance/reconciliation policy.
- Android instrumented tests cover WorkManager registration for the unique periodic rest-cycle work.

This keeps the metaphor literal: sleep is consolidation plus immune sweep.
