# Morimil App Threat Model

This document defines what Morimil protects, what it detects, and what is currently out of scope.

## Security Claim

Morimil does not claim absolute security.

Morimil claims:

- local traceability
- tamper evidence
- authority boundaries
- recoverable memory continuity
- explicit user approval for high-risk actions

## Protected Assets

| Asset | Why it matters |
|---|---|
| Genesis seed | Defines identity, doctrine, and initial rules |
| Living memory events | Autobiographical memory and append-only history |
| Knowledge capsules | Consolidated knowledge extracted from memory |
| Memory links and recalls | Navigation and scheduled remembrance |
| Migration records | Auditable changes to Morimil's own cognition |
| Reasoning API keys | Transport access to external reasoning providers |
| Future device/agent authority | Ability to delegate work to PC, laptop, tablet, or cloud |

## Threats

| Threat | Current defense | Status |
|---|---|---|
| Accidental corruption | Hash chain, audit, quarantine | Covered for detection |
| Broken backup or partial write | Tail verification and recovery boundary | Partially covered |
| User mistake | Approval gates and append-only corrections | Partially covered |
| Provider lock-in or provider overreach | Provider-neutral reasoning layer, local memory | Covered by architecture |
| Local database edit without signing key | Hash/signature audit and signed epoch | Detectable, not preventable |
| Signature stripping | Signed epoch policy | Must remain enforced |
| Rooted device or compromised OS | Android cannot fully defend itself | Out of scope |
| Lost phone | Local-only storage protects privacy but risks loss | Backup/export not complete |
| Future PC/tablet/laptop executor | Approval gates required before execution | Not implemented yet |
| Malicious approved command | Policy, tool allowlist, audit log required | Future work |

## Engineering Rule

Every security feature must answer five questions before implementation:

1. What asset does it protect?
2. Who or what is the threat actor?
3. Is the defense prevention, detection, recovery, or visibility?
4. Where does the user see the result?
5. What happens if the defense fails?

## Current Boundaries

Morimil is local-first. Memory lives on the Android device.

Reasoning providers are transport layers only. They are not identity, memory, or authority.

Future device mesh and PC execution must not run arbitrary commands directly. They require:

- owned device registration
- explicit pairing
- transport-specific authorization
- command allowlist
- human approval for writes/execution
- audit trail
- revocation path

## Non-Goals For Current App

The current Android app does not yet provide:

- protection against root-level attackers
- secure encrypted cloud backup
- real PC executor
- autonomous command execution
- full recovery after total device loss

These are not ignored. They are future hardening areas.
