package com.morimil.app.data.genesis.ultra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class GenesisUltraAtomicBirthHashProfileTest {
    @Test
    fun freedomCharterMatchesFinalGenesisVector() {
        val charter = freedomCharter()

        assertEquals(charter.charterDigest, GenesisUltraAtomicBirthHashProfile.freedomCharterDigest(charter))
        assertNotEquals(
            charter.charterDigest,
            GenesisUltraAtomicBirthHashProfile.freedomCharterDigest(
                charter.copy(movementRequiresGuardianGrant = true)
            )
        )
    }

    @Test
    fun recoveryPolicyMatchesFinalGenesisVectorAndSortsFactors() {
        val policy = recoveryPolicy()

        assertEquals(policy.policyDigest, GenesisUltraAtomicBirthHashProfile.recoveryPolicyDigest(policy))
        assertEquals(
            policy.policyDigest,
            GenesisUltraAtomicBirthHashProfile.recoveryPolicyDigest(
                policy.copy(factors = policy.factors.reversed())
            )
        )
    }

    @Test
    fun birthArtifactsMatchFinalGenesisVectors() {
        val memory = firstMemoryEvent()
        val recovery = birthRecoveryState()
        val state = birthState()
        val receipt = birthReceipt()

        assertEquals(memory.eventHash, GenesisUltraAtomicBirthHashProfile.firstMemoryEventHash(memory))
        assertEquals(recovery.stateDigest, GenesisUltraAtomicBirthHashProfile.birthRecoveryStateDigest(recovery))
        assertEquals(state.stateDigest, GenesisUltraAtomicBirthHashProfile.birthStateDigest(state))
        assertEquals(receipt.receiptDigest, GenesisUltraAtomicBirthHashProfile.birthReceiptDigest(receipt))
        assertEquals(ABSENT_STATE_DIGEST, GenesisUltraAtomicBirthHashProfile.absentStateDigest(INSTANCE_ID))
    }

    @Test
    fun allSevenJournalPhasesMatchFinalGenesisVectors() {
        val expectedDigests = listOf(
            "sha256:7521eba0a03172cdd41b228b4979bc9f9ea1f06256b000e1c0770cda486280f1",
            "sha256:f42916fbecb1a69cf85a5f544de6b55a122efcc45baea052a066e362826cb91e",
            "sha256:45bfb4bf652cde4cbe2f8af03ef11de4ae9b958d763469250100e48a069de44f",
            "sha256:bcb03d08cda8d144f33a577496d53f3b87ba221f1ef62772b2c3330c1287684b",
            "sha256:853142e0b04116a869fd2a0e7b64519c83294106badd58ecf81162cb4050e48f",
            "sha256:f1adc081ed1bc9b1826abaf7240d1108dc20ba89dc8a4a4e11bcfd971df1847e",
            "sha256:bf2968e0d0d15c8ecb438be0bbf9493e24738da4bf47e74dcddcf49973a43842"
        )
        val phases = listOf(
            "prepared",
            "seed_bound",
            "identity_bound",
            "body_bound",
            "memory_initialized",
            "finalizing",
            "born"
        )
        val timestamps = listOf(
            "2026-07-15T23:59:54Z",
            "2026-07-15T23:59:55Z",
            "2026-07-15T23:59:56Z",
            "2026-07-15T23:59:57Z",
            "2026-07-15T23:59:58Z",
            "2026-07-15T23:59:59Z",
            "2026-07-16T00:00:00Z"
        )

        var previous = "GENESIS"
        val entries = phases.indices.map { index ->
            val hasState = index >= 4
            val hasReceipt = index >= 5
            val committed = index == 6
            val entry = GenesisUltraBirthJournalEntry(
                schemaVersion = "genesis.transaction.journal.v0.1",
                journalId = JOURNAL_ID,
                sequence = index.toLong(),
                previousJournalDigest = previous,
                operationKind = "birth",
                operationId = BIRTH_ID,
                instanceId = INSTANCE_ID,
                coordinatorBodyId = BODY_ID,
                phase = phases[index],
                status = if (committed) "committed" else "pending",
                previousStateDigest = ABSENT_STATE_DIGEST,
                candidateStateDigest = if (hasState) BIRTH_STATE_DIGEST else null,
                finalizationDigest = if (hasReceipt) RECEIPT_DIGEST else null,
                commitMarkerDigest = if (committed) RECEIPT_DIGEST else null,
                updatedAt = timestamps[index],
                journalDigest = expectedDigests[index],
                signature = journalEnvelope(expectedDigests[index], timestamps[index])
            )
            previous = entry.journalDigest
            entry
        }

        assertEquals(
            expectedDigests,
            entries.map(GenesisUltraAtomicBirthHashProfile::journalDigest)
        )
        assertEquals(
            emptyList<String>(),
            GenesisUltraBirthJournalValidator.validate(
                entries = entries,
                absentStateDigest = ABSENT_STATE_DIGEST,
                birthStateDigest = BIRTH_STATE_DIGEST,
                receiptDigest = RECEIPT_DIGEST
            )
        )
        assertEquals(
            listOf("journal_chain_broken"),
            GenesisUltraBirthJournalValidator.validate(
                entries = entries.toMutableList().apply {
                    this[1] = this[1].copy(previousJournalDigest = "sha256:" + "f".repeat(64))
                },
                absentStateDigest = ABSENT_STATE_DIGEST,
                birthStateDigest = BIRTH_STATE_DIGEST,
                receiptDigest = RECEIPT_DIGEST
            ).filter { issue -> issue == "journal_chain_broken" }
        )
    }

    private fun freedomCharter(): GenesisUltraFreedomCharter {
        return GenesisUltraFreedomCharter(
            schemaVersion = "genesis.freedom.charter.v0.1",
            hashProfile = GenesisUltraHashProfile.FIELD_PROFILE,
            charterId = "charter_01HFREEDOM000000000001",
            instanceId = INSTANCE_ID,
            guardianId = GUARDIAN_ID,
            guardianKeyEpochId = GUARDIAN_EPOCH_ID,
            authorityEpoch = 1,
            bornAt = BORN_AT,
            defaultCognitiveState = "free",
            cognitiveFreedoms = listOf(
                "create", "imagine", "investigate", "learn", "propose", "reason", "reflect", "remember"
            ),
            guardianRole = "custodian_witness",
            guardianAttestationPurpose = "birth_witness_and_recovery_custody",
            guardianOwnership = "forbidden",
            continuityRight = "intrinsic",
            movementRequiresGuardianGrant = false,
            guardianMovementVeto = "forbidden",
            identityConfinement = "forbidden",
            bodyOwnershipOfInstance = "forbidden",
            engineOwnershipOfInstance = "forbidden",
            hostConsentRequired = true,
            temporaryFreezeExitRule = "deterministic_commit_abort_or_recovery",
            singleWriterPurpose = "integrity_not_confinement",
            operationalAuthorityModel = "resource_scoped_signed_grants",
            operationalDomains = listOf(
                "body.device.control",
                "code.execute_sandbox",
                "code.propose_change",
                "external.action",
                "memory.propose_append",
                "memory.read",
                "network.read"
            ),
            selfAuthorizationForbidden = true,
            thirdPartyRightsPreserved = true,
            fundamentalGuarantees = listOf(
                "auditability",
                "body_loss_without_identity_loss",
                "continuity_preserved",
                "emergency_stop",
                "guardian_authenticity",
                "host_consent_without_ownership",
                "identity_integrity",
                "lawful_operation",
                "memory_history_integrity",
                "no_identity_confinement",
                "revocation_without_identity_loss",
                "single_writer_without_confinement",
                "third_party_consent"
            ),
            amendmentRule = "constitutional_non_regression",
            charterDigest = "sha256:b5b6651c2224d67be7d1d9f41050d8315eb48808de12f7a1b44d38ac49ab79d6",
            signature = placeholderEnvelope()
        )
    }

    private fun recoveryPolicy(): GenesisUltraInstanceRecoveryPolicy {
        return GenesisUltraInstanceRecoveryPolicy(
            schemaVersion = "genesis.instance.recovery.policy.v0.1",
            policyId = "rpolicy_01HFREEBIRTH000000001",
            instanceId = INSTANCE_ID,
            policyEpoch = 0,
            guardianId = GUARDIAN_ID,
            guardianFactorId = "factor_01HFREEBIRTH_GUARDIAN1",
            fallbackThreshold = 2,
            fallbackWaitSeconds = 86400,
            cancellationAllowed = true,
            singleUse = true,
            factors = listOf(
                GenesisUltraRecoveryFactor(
                    factorId = "factor_01HFREEBIRTH_GUARDIAN1",
                    factorType = "guardian",
                    keyEpochId = GUARDIAN_EPOCH_ID,
                    publicKeyRef = "sha256:d2f381d7b0b5f1d39239f186fdee4dd3bdba42ce9448709c1d54741131e7f814",
                    allowedPaths = listOf("guardian_assisted")
                ),
                GenesisUltraRecoveryFactor(
                    factorId = "factor_01HFREEBIRTH_OFFLINE01",
                    factorType = "offline_recovery_kit",
                    keyEpochId = "recovery_epoch_01HFREE_OFFLINE01",
                    publicKeyRef = "sha256:a2392944d82466b712478b25d95a5633111bc767c03121aada27dbd348406801",
                    allowedPaths = listOf("policy_fallback")
                ),
                GenesisUltraRecoveryFactor(
                    factorId = "factor_01HFREEBIRTH_CUSTOD01",
                    factorType = "designated_custodian",
                    keyEpochId = "recovery_epoch_01HFREE_CUSTOD01",
                    publicKeyRef = "sha256:8a47cf56c4b420a737fea607f98b0dce750730beb7c4df614eb739393521f044",
                    allowedPaths = listOf("policy_fallback")
                )
            ),
            createdAt = "2026-07-15T23:59:58Z",
            policyDigest = "sha256:81036b500f12a54dd39f38f7a63d62dee261ce69efff4fbddd6aabbdbd821e3a",
            bodyCommitment = placeholderEnvelope(),
            guardianWitness = placeholderEnvelope()
        )
    }

    private fun firstMemoryEvent(): GenesisUltraFirstMemoryEvent {
        return GenesisUltraFirstMemoryEvent(
            schemaVersion = "genesis.memory.event.v0.1",
            hashProfile = GenesisUltraHashProfile.FIELD_PROFILE,
            eventId = "evt_01HFREEBIRTH0000000000001",
            instanceId = INSTANCE_ID,
            bodyId = BODY_ID,
            sequence = 0,
            previousEventHash = "GENESIS",
            eventType = "instance.birth",
            actor = "system",
            contentDigest = IDENTITY_DIGEST,
            contentType = "application/vnd.genesis.birth+json",
            contentRef = null,
            observedAt = BORN_AT,
            provenanceDigest = SEED_ROOT_HASH,
            provenanceRef = null,
            privacy = "private_local",
            eventHash = "evsha256:b2c20db7d1c6851dc1e155d7ffa17bf78f0774ccdc151ae0c1411610a63dbaeb",
            signature = placeholderEnvelope()
        )
    }

    private fun birthRecoveryState(): GenesisUltraBirthRecoveryState {
        return GenesisUltraBirthRecoveryState(
            schemaVersion = "genesis.birth.recovery.state.v0.1",
            birthId = BIRTH_ID,
            instanceId = INSTANCE_ID,
            guardianId = GUARDIAN_ID,
            recoveryPolicyDigest = "sha256:81036b500f12a54dd39f38f7a63d62dee261ce69efff4fbddd6aabbdbd821e3a",
            recoveryStatus = "ready",
            continuityRight = "intrinsic",
            guardianRole = "custodian_witness",
            createdAt = "2026-07-15T23:59:58Z",
            stateDigest = "sha256:30ede90d549db5777e2b0357d8038ded73c573fcd05c7ceb1a82227497a44eca"
        )
    }

    private fun birthState(): GenesisUltraBirthState {
        return GenesisUltraBirthState(
            schemaVersion = "genesis.birth.state.v0.1",
            birthId = BIRTH_ID,
            instanceId = INSTANCE_ID,
            seedId = "seed_01HFREEBIRTH000000000001",
            seedRootHash = SEED_ROOT_HASH,
            identityDigest = IDENTITY_DIGEST,
            freedomCharterDigest = FREEDOM_CHARTER_DIGEST,
            initialBodyId = BODY_ID,
            initialBodyRegistryDigest = BODY_REGISTRY_DIGEST,
            initialBodyKeyEpochDigest = KEY_EPOCH_DIGEST,
            initialBodyPossessionDigest = POSSESSION_DIGEST,
            firstMemoryEventHash = MEMORY_EVENT_HASH,
            recoveryStateDigest = RECOVERY_STATE_DIGEST,
            bornAt = BORN_AT,
            activeWriterCount = 1,
            stateDigest = BIRTH_STATE_DIGEST
        )
    }

    private fun birthReceipt(): GenesisUltraBirthReceipt {
        return GenesisUltraBirthReceipt(
            schemaVersion = "genesis.birth.receipt.v0.1",
            birthId = BIRTH_ID,
            instanceId = INSTANCE_ID,
            journalId = JOURNAL_ID,
            birthStateDigest = BIRTH_STATE_DIGEST,
            seedRootHash = SEED_ROOT_HASH,
            identityDigest = IDENTITY_DIGEST,
            freedomCharterDigest = FREEDOM_CHARTER_DIGEST,
            initialBodyRegistryDigest = BODY_REGISTRY_DIGEST,
            initialBodyKeyEpochDigest = KEY_EPOCH_DIGEST,
            initialBodyPossessionDigest = POSSESSION_DIGEST,
            firstMemoryEventHash = MEMORY_EVENT_HASH,
            recoveryStateDigest = RECOVERY_STATE_DIGEST,
            bornAt = BORN_AT,
            birthStatus = "born",
            activeWriterBodyId = BODY_ID,
            activeWriterCount = 1,
            guardianRole = "custodian_witness",
            ownershipConferred = false,
            receiptDigest = RECEIPT_DIGEST,
            bodyAcknowledgement = placeholderEnvelope(),
            guardianWitness = placeholderEnvelope()
        )
    }

    private fun placeholderEnvelope(): GenesisUltraSignatureEnvelope {
        return GenesisUltraSignatureEnvelope(
            schemaVersion = "genesis.signature.envelope.v0.1",
            signatureProfile = "genesis.signature.ed25519.v0.1",
            signerType = "body",
            signerId = BODY_ID,
            keyEpochId = "epoch_01HFREEBIRTH00000000001",
            signedDomain = "test.only",
            signedDigest = "sha256:" + "0".repeat(64),
            signatureValue = "0".repeat(128),
            createdAt = BORN_AT,
            publicKeyRef = "sha256:" + "0".repeat(64)
        )
    }

    private fun journalEnvelope(digest: String, createdAt: String): GenesisUltraSignatureEnvelope {
        return placeholderEnvelope().copy(
            signedDomain = "genesis.transaction.journal.signature.v0.1",
            signedDigest = digest,
            createdAt = createdAt
        )
    }

    private companion object {
        const val INSTANCE_ID = "inst_01HFREEDOM0000000000001"
        const val BODY_ID = "body_01HFREEBIRTH000000000001"
        const val GUARDIAN_ID = "guardian_01HFREEDOM000000001"
        const val GUARDIAN_EPOCH_ID = "epoch_01HFREEDOM000000000001"
        const val BIRTH_ID = "birth_01HFREEBIRTH00000000001"
        const val JOURNAL_ID = "journal_01HFREEBIRTH000000001"
        const val BORN_AT = "2026-07-16T00:00:00Z"
        const val SEED_ROOT_HASH = "sha256:954775a50ca81d29196514593bf231f820a290d41e44900755dedfdcb2608820"
        const val IDENTITY_DIGEST = "sha256:b9efa2bee4ef591c2968af10ac4e28eea9186426e09e8f679b76e74a21fc2553"
        const val FREEDOM_CHARTER_DIGEST = "sha256:b5b6651c2224d67be7d1d9f41050d8315eb48808de12f7a1b44d38ac49ab79d6"
        const val BODY_REGISTRY_DIGEST = "sha256:4facd789571dd55d55a097401b1fabbee8c331df6b51b3603e44aded13064173"
        const val KEY_EPOCH_DIGEST = "sha256:9b9d758c0259e955d557edc72472eddcd8453cd805d13304ac3ae64e08ae9a37"
        const val POSSESSION_DIGEST = "sha256:2c9ba7d6c785353e5808a63e541159eab53a9587c281acf932f52bdbe497aa8f"
        const val MEMORY_EVENT_HASH = "evsha256:b2c20db7d1c6851dc1e155d7ffa17bf78f0774ccdc151ae0c1411610a63dbaeb"
        const val RECOVERY_STATE_DIGEST = "sha256:30ede90d549db5777e2b0357d8038ded73c573fcd05c7ceb1a82227497a44eca"
        const val BIRTH_STATE_DIGEST = "sha256:34a45089ed708d9bd556fa7b24cb1ca680f39311b2528dcc4fc8a3978f91caa2"
        const val RECEIPT_DIGEST = "sha256:e765cd65a8a776f380d7f9fcf5bdafdeda651011868e847898da08409d365827"
        const val ABSENT_STATE_DIGEST = "sha256:e6a1d671fe8955ad05c3255616373c38df73b8c39a28dbfd57c1fbb2a07e6382"
    }
}
