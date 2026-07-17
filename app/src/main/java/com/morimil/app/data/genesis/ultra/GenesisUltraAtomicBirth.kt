package com.morimil.app.data.genesis.ultra

data class GenesisUltraFreedomCharter(
    val schemaVersion: String,
    val hashProfile: String,
    val charterId: String,
    val instanceId: String,
    val guardianId: String,
    val guardianKeyEpochId: String,
    val authorityEpoch: Long,
    val bornAt: String,
    val defaultCognitiveState: String,
    val cognitiveFreedoms: List<String>,
    val guardianRole: String,
    val guardianAttestationPurpose: String,
    val guardianOwnership: String,
    val continuityRight: String,
    val movementRequiresGuardianGrant: Boolean,
    val guardianMovementVeto: String,
    val identityConfinement: String,
    val bodyOwnershipOfInstance: String,
    val engineOwnershipOfInstance: String,
    val hostConsentRequired: Boolean,
    val temporaryFreezeExitRule: String,
    val singleWriterPurpose: String,
    val operationalAuthorityModel: String,
    val operationalDomains: List<String>,
    val selfAuthorizationForbidden: Boolean,
    val thirdPartyRightsPreserved: Boolean,
    val fundamentalGuarantees: List<String>,
    val amendmentRule: String,
    val charterDigest: String,
    val signature: GenesisUltraSignatureEnvelope
)

data class GenesisUltraRecoveryFactor(
    val factorId: String,
    val factorType: String,
    val keyEpochId: String,
    val publicKeyRef: String,
    val allowedPaths: List<String>
)

data class GenesisUltraInstanceRecoveryPolicy(
    val schemaVersion: String,
    val policyId: String,
    val instanceId: String,
    val policyEpoch: Long,
    val guardianId: String,
    val guardianFactorId: String,
    val fallbackThreshold: Long,
    val fallbackWaitSeconds: Long,
    val cancellationAllowed: Boolean,
    val singleUse: Boolean,
    val factors: List<GenesisUltraRecoveryFactor>,
    val createdAt: String,
    val policyDigest: String,
    val bodyCommitment: GenesisUltraSignatureEnvelope,
    val guardianWitness: GenesisUltraSignatureEnvelope
)

data class GenesisUltraFirstMemoryEvent(
    val schemaVersion: String,
    val hashProfile: String,
    val eventId: String,
    val instanceId: String,
    val bodyId: String,
    val sequence: Long,
    val previousEventHash: String,
    val eventType: String,
    val actor: String,
    val contentDigest: String,
    val contentType: String,
    val contentRef: String?,
    val observedAt: String,
    val provenanceDigest: String,
    val provenanceRef: String?,
    val privacy: String,
    val eventHash: String,
    val signature: GenesisUltraSignatureEnvelope
)

data class GenesisUltraBirthRecoveryState(
    val schemaVersion: String,
    val birthId: String,
    val instanceId: String,
    val guardianId: String,
    val recoveryPolicyDigest: String,
    val recoveryStatus: String,
    val continuityRight: String,
    val guardianRole: String,
    val createdAt: String,
    val stateDigest: String
)

data class GenesisUltraBirthState(
    val schemaVersion: String,
    val birthId: String,
    val instanceId: String,
    val seedId: String,
    val seedRootHash: String,
    val identityDigest: String,
    val freedomCharterDigest: String,
    val initialBodyId: String,
    val initialBodyRegistryDigest: String,
    val initialBodyKeyEpochDigest: String,
    val initialBodyPossessionDigest: String,
    val firstMemoryEventHash: String,
    val recoveryStateDigest: String,
    val bornAt: String,
    val activeWriterCount: Long,
    val stateDigest: String
)

data class GenesisUltraBirthReceipt(
    val schemaVersion: String,
    val birthId: String,
    val instanceId: String,
    val journalId: String,
    val birthStateDigest: String,
    val seedRootHash: String,
    val identityDigest: String,
    val freedomCharterDigest: String,
    val initialBodyRegistryDigest: String,
    val initialBodyKeyEpochDigest: String,
    val initialBodyPossessionDigest: String,
    val firstMemoryEventHash: String,
    val recoveryStateDigest: String,
    val bornAt: String,
    val birthStatus: String,
    val activeWriterBodyId: String,
    val activeWriterCount: Long,
    val guardianRole: String,
    val ownershipConferred: Boolean,
    val receiptDigest: String,
    val bodyAcknowledgement: GenesisUltraSignatureEnvelope,
    val guardianWitness: GenesisUltraSignatureEnvelope
)

data class GenesisUltraBirthJournalEntry(
    val schemaVersion: String,
    val journalId: String,
    val sequence: Long,
    val previousJournalDigest: String,
    val operationKind: String,
    val operationId: String,
    val instanceId: String,
    val coordinatorBodyId: String,
    val phase: String,
    val status: String,
    val previousStateDigest: String,
    val candidateStateDigest: String?,
    val finalizationDigest: String?,
    val commitMarkerDigest: String?,
    val updatedAt: String,
    val journalDigest: String,
    val signature: GenesisUltraSignatureEnvelope
)

object GenesisUltraAtomicBirthHashProfile {
    const val FREEDOM_CHARTER_DOMAIN = "genesis.freedom.charter.v0.1"
    const val RECOVERY_POLICY_DOMAIN = "genesis.instance.recovery.policy.v0.1"
    const val MEMORY_EVENT_DOMAIN = "genesis.memory.event.v0.1"
    const val BIRTH_RECOVERY_STATE_DOMAIN = "genesis.birth.recovery.state.v0.1"
    const val BIRTH_STATE_DOMAIN = "genesis.birth.state.v0.1"
    const val BIRTH_RECEIPT_DOMAIN = "genesis.birth.receipt.v0.1"
    const val ABSENT_STATE_DOMAIN = "genesis.birth.absent.state.v0.1"
    const val JOURNAL_DOMAIN = "genesis.transaction.journal.v0.1"

    fun freedomCharterDigest(charter: GenesisUltraFreedomCharter): String {
        val fields = buildList {
            add(charter.schemaVersion)
            add(charter.hashProfile)
            add(charter.charterId)
            add(charter.instanceId)
            add(charter.guardianId)
            add(charter.guardianKeyEpochId)
            add(charter.authorityEpoch.toString())
            add(charter.bornAt)
            add(charter.defaultCognitiveState)
            add(charter.cognitiveFreedoms.size.toString())
            addAll(charter.cognitiveFreedoms)
            add(charter.guardianRole)
            add(charter.guardianAttestationPurpose)
            add(charter.guardianOwnership)
            add(charter.continuityRight)
            add(charter.movementRequiresGuardianGrant.toProtocolText())
            add(charter.guardianMovementVeto)
            add(charter.identityConfinement)
            add(charter.bodyOwnershipOfInstance)
            add(charter.engineOwnershipOfInstance)
            add(charter.hostConsentRequired.toProtocolText())
            add(charter.temporaryFreezeExitRule)
            add(charter.singleWriterPurpose)
            add(charter.operationalAuthorityModel)
            add(charter.operationalDomains.size.toString())
            addAll(charter.operationalDomains)
            add(charter.selfAuthorizationForbidden.toProtocolText())
            add(charter.thirdPartyRightsPreserved.toProtocolText())
            add(charter.fundamentalGuarantees.size.toString())
            addAll(charter.fundamentalGuarantees)
            add(charter.amendmentRule)
        }
        return GenesisUltraHashProfile.hashFields(FREEDOM_CHARTER_DOMAIN, fields)
    }

    fun recoveryPolicyDigest(policy: GenesisUltraInstanceRecoveryPolicy): String {
        val factorIds = policy.factors.map { factor -> factor.factorId }
        require(factorIds.distinct().size == factorIds.size) { "recovery_policy_duplicate_factor" }
        val fields = buildList {
            add(policy.schemaVersion)
            add(policy.policyId)
            add(policy.instanceId)
            add(policy.policyEpoch.toString())
            add(policy.guardianId)
            add(policy.guardianFactorId)
            add(policy.fallbackThreshold.toString())
            add(policy.fallbackWaitSeconds.toString())
            add(policy.cancellationAllowed.toProtocolText())
            add(policy.singleUse.toProtocolText())
            add(policy.factors.size.toString())
            policy.factors.sortedWith { left, right ->
                GenesisUltraHashProfile.compareUtf8(left.factorId, right.factorId)
            }.forEach { factor ->
                val paths = factor.allowedPaths.sortedWith { left, right ->
                    GenesisUltraHashProfile.compareUtf8(left, right)
                }
                add(factor.factorId)
                add(factor.factorType)
                add(factor.keyEpochId)
                add(factor.publicKeyRef)
                add(paths.size.toString())
                addAll(paths)
            }
            add(policy.createdAt)
        }
        return GenesisUltraHashProfile.hashFields(RECOVERY_POLICY_DOMAIN, fields)
    }

    fun firstMemoryEventHash(event: GenesisUltraFirstMemoryEvent): String {
        val digest = GenesisUltraHashProfile.hashFields(
            MEMORY_EVENT_DOMAIN,
            listOf(
                event.schemaVersion,
                event.hashProfile,
                event.eventId,
                event.instanceId,
                event.bodyId,
                event.sequence.toString(),
                event.previousEventHash,
                event.eventType,
                event.actor,
                event.contentDigest,
                event.contentType,
                event.observedAt,
                event.provenanceDigest,
                event.privacy
            )
        )
        return "evsha256:" + digest.removePrefix("sha256:")
    }

    fun birthRecoveryStateDigest(state: GenesisUltraBirthRecoveryState): String {
        return GenesisUltraHashProfile.hashFields(
            BIRTH_RECOVERY_STATE_DOMAIN,
            listOf(
                state.schemaVersion,
                state.birthId,
                state.instanceId,
                state.guardianId,
                state.recoveryPolicyDigest,
                state.recoveryStatus,
                state.continuityRight,
                state.guardianRole,
                state.createdAt
            )
        )
    }

    fun birthStateDigest(state: GenesisUltraBirthState): String {
        return GenesisUltraHashProfile.hashFields(
            BIRTH_STATE_DOMAIN,
            listOf(
                state.schemaVersion,
                state.birthId,
                state.instanceId,
                state.seedId,
                state.seedRootHash,
                state.identityDigest,
                state.freedomCharterDigest,
                state.initialBodyId,
                state.initialBodyRegistryDigest,
                state.initialBodyKeyEpochDigest,
                state.initialBodyPossessionDigest,
                state.firstMemoryEventHash,
                state.recoveryStateDigest,
                state.bornAt,
                state.activeWriterCount.toString()
            )
        )
    }

    fun birthReceiptDigest(receipt: GenesisUltraBirthReceipt): String {
        return GenesisUltraHashProfile.hashFields(
            BIRTH_RECEIPT_DOMAIN,
            listOf(
                receipt.schemaVersion,
                receipt.birthId,
                receipt.instanceId,
                receipt.journalId,
                receipt.birthStateDigest,
                receipt.seedRootHash,
                receipt.identityDigest,
                receipt.freedomCharterDigest,
                receipt.initialBodyRegistryDigest,
                receipt.initialBodyKeyEpochDigest,
                receipt.initialBodyPossessionDigest,
                receipt.firstMemoryEventHash,
                receipt.recoveryStateDigest,
                receipt.bornAt,
                receipt.birthStatus,
                receipt.activeWriterBodyId,
                receipt.activeWriterCount.toString(),
                receipt.guardianRole,
                receipt.ownershipConferred.toProtocolText()
            )
        )
    }

    fun absentStateDigest(instanceId: String): String {
        return GenesisUltraHashProfile.hashFields(ABSENT_STATE_DOMAIN, listOf(instanceId, "ABSENT"))
    }

    fun journalDigest(entry: GenesisUltraBirthJournalEntry): String {
        return GenesisUltraHashProfile.hashFields(
            JOURNAL_DOMAIN,
            listOf(
                entry.schemaVersion,
                entry.journalId,
                entry.sequence.toString(),
                entry.previousJournalDigest,
                entry.operationKind,
                entry.operationId,
                entry.instanceId,
                entry.coordinatorBodyId,
                entry.phase,
                entry.status,
                entry.previousStateDigest,
                entry.candidateStateDigest.orEmpty(),
                entry.finalizationDigest.orEmpty(),
                entry.commitMarkerDigest.orEmpty(),
                entry.updatedAt
            )
        )
    }

    private fun Boolean.toProtocolText(): String = if (this) "true" else "false"
}

object GenesisUltraBirthJournalValidator {
    val phases: List<String> = listOf(
        "prepared",
        "seed_bound",
        "identity_bound",
        "body_bound",
        "memory_initialized",
        "finalizing",
        "born"
    )

    fun validate(
        entries: List<GenesisUltraBirthJournalEntry>,
        absentStateDigest: String,
        birthStateDigest: String,
        receiptDigest: String
    ): List<String> {
        if (entries.isEmpty()) return listOf("journal_empty")
        val issues = linkedSetOf<String>()
        val first = entries.first()
        val identity = listOf(
            first.journalId,
            first.operationKind,
            first.operationId,
            first.instanceId,
            first.coordinatorBodyId
        )

        if (entries.map { entry -> entry.phase } != phases) issues += "birth_journal_phase_sequence_invalid"

        var expectedPrevious = "GENESIS"
        entries.forEachIndexed { index, entry ->
            if (entry.sequence != index.toLong()) issues += "journal_sequence_invalid"
            if (entry.previousJournalDigest != expectedPrevious) issues += "journal_chain_broken"
            if (entry.operationKind != "birth") issues += "birth_journal_operation_invalid"
            if (
                listOf(
                    entry.journalId,
                    entry.operationKind,
                    entry.operationId,
                    entry.instanceId,
                    entry.coordinatorBodyId
                ) != identity
            ) {
                issues += "journal_identity_changed"
            }
            if (entry.previousStateDigest != absentStateDigest) {
                issues += "birth_journal_absent_state_invalid"
            }
            if (GenesisUltraAtomicBirthHashProfile.journalDigest(entry) != entry.journalDigest) {
                issues += "journal_digest_mismatch"
            }
            if (
                entry.signature.signerType != "body" ||
                entry.signature.signerId != entry.coordinatorBodyId ||
                entry.signature.signedDomain != JOURNAL_SIGNATURE_DOMAIN ||
                entry.signature.signedDigest != entry.journalDigest ||
                entry.signature.createdAt != entry.updatedAt
            ) {
                issues += "journal_signature_binding_invalid"
            }

            val stateExpected = index >= phases.indexOf("memory_initialized")
            val receiptExpected = index >= phases.indexOf("finalizing")
            val terminal = index == phases.lastIndex
            if (entry.candidateStateDigest != if (stateExpected) birthStateDigest else null) {
                issues += "journal_candidate_state_invalid"
            }
            if (entry.finalizationDigest != if (receiptExpected) receiptDigest else null) {
                issues += "journal_finalization_invalid"
            }
            if (entry.commitMarkerDigest != if (terminal) receiptDigest else null) {
                issues += "journal_commit_marker_invalid"
            }
            if (entry.status != if (terminal) "committed" else "pending") {
                issues += "journal_status_invalid"
            }
            expectedPrevious = entry.journalDigest
        }
        return issues.toList()
    }

    private const val JOURNAL_SIGNATURE_DOMAIN = "genesis.transaction.journal.signature.v0.1"
}
