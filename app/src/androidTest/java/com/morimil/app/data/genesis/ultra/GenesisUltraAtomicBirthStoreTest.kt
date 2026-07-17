package com.morimil.app.data.genesis.ultra

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.morimil.app.data.local.GenesisUltraBirthCommitEntity
import com.morimil.app.data.local.MorimilDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.charset.StandardCharsets

@RunWith(AndroidJUnit4::class)
class GenesisUltraAtomicBirthStoreTest {
    private lateinit var database: MorimilDatabase

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, MorimilDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun persistWritesOneImmutableCommitWithExactArtifactsAndJournal() = runBlocking {
        val bundle = validBundle()
        val store = GenesisUltraAtomicBirthStore(database)

        assertEquals(GenesisUltraPersistedBirthState.ABSENT, store.readState())
        val commit = store.persistPrevalidated(bundle, persistedAtMillis = 1000L)

        assertEquals(GenesisUltraPersistedBirthState.COMMITTED, store.readState())
        assertEquals(GenesisUltraBirthCommitEntity.PRIMARY_SLOT, commit.slotId)
        assertEquals(bundle.instanceIdentity.companionName, commit.companionName)
        assertEquals(bundle.birthReceipt.receiptDigest, commit.receiptDigest)
        assertEquals(bundle.artifacts.size.toLong(), commit.artifactCount)
        assertEquals(7L, commit.journalEntryCount)

        val persisted = database.genesisUltraBirthDao().loadBirthCommit(commit.slotId)
        assertEquals("Genesis Libre", persisted?.companionName)
        assertEquals(1, database.genesisUltraBirthDao().countBirthCommits())
    }

    @Test
    fun failureBeforeCommitMarkerRollsBackEveryInsertedRow() = runBlocking {
        val store = GenesisUltraAtomicBirthStore(database) { checkpoint ->
            if (checkpoint == GenesisUltraBirthPersistenceCheckpoint.AFTER_JOURNAL) {
                error("simulated_process_interruption")
            }
        }

        val failure = runCatching {
            store.persistPrevalidated(validBundle(), persistedAtMillis = 1000L)
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(GenesisUltraPersistedBirthState.ABSENT, store.readState())
        assertEquals(0, database.genesisUltraBirthDao().countBirthCommits())
        assertEquals(0, database.genesisUltraBirthDao().countBirthArtifacts())
        assertEquals(0, database.genesisUltraBirthDao().countBirthJournalEntries())
    }

    @Test
    fun secondBirthIsRejectedWithoutChangingTheOriginalName() = runBlocking {
        val original = validBundle()
        val store = GenesisUltraAtomicBirthStore(database)
        store.persistPrevalidated(original, persistedAtMillis = 1000L)

        val second = validBundle(companionName = "Otro Nombre")
        val failure = runCatching {
            store.persistPrevalidated(second, persistedAtMillis = 2000L)
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        val persisted = database.genesisUltraBirthDao()
            .loadBirthCommit(GenesisUltraBirthCommitEntity.PRIMARY_SLOT)
        assertEquals("Genesis Libre", persisted?.companionName)
        assertEquals(1, database.genesisUltraBirthDao().countBirthCommits())
    }

    @Test
    fun seedIdentityDigestAndBornIdentityDigestRemainDistinctAndTamperingFailsClosed() {
        val bundle = validBundle()

        assertNotEquals(bundle.seedManifest.identityDigest, bundle.instanceIdentity.identityDigest)
        assertEquals(emptyList<String>(), GenesisUltraAtomicBirthPersistenceValidator.validate(bundle))

        val doctrineIndex = bundle.artifacts.indexOfFirst { artifact ->
            artifact.relativePath == "doctrine/free-birth.md"
        }
        val tamperedArtifacts = bundle.artifacts.toMutableList().apply {
            val original = this[doctrineIndex]
            this[doctrineIndex] = original.copy(payload = "changed".utf8())
        }
        val issues = GenesisUltraAtomicBirthPersistenceValidator.validate(
            bundle.copy(artifacts = tamperedArtifacts)
        )
        assertTrue(issues.contains("seed_artifact_digest_mismatch:doctrine/free-birth.md"))
    }

    private fun validBundle(companionName: String = "Genesis Libre"): GenesisUltraAtomicBirthPersistenceBundle {
        val seedIdentityBytes = "neutral seed identity".utf8()
        val doctrineBytes = "free birth doctrine".utf8()
        val manifestDraft = GenesisUltraSeedManifest(
            schemaVersion = "genesis.seed.manifest.v0.1",
            protocolVersion = "genesis.protocol.v0.1",
            hashProfile = GenesisUltraHashProfile.FIELD_PROFILE,
            seedId = SEED_ID,
            identityDigest = GenesisUltraHashProfile.sha256(seedIdentityBytes),
            doctrineDigest = GenesisUltraHashProfile.sha256(doctrineBytes),
            files = listOf(
                GenesisUltraSeedFileRecord(
                    path = "doctrine/free-birth.md",
                    kind = "doctrine",
                    required = true,
                    digest = GenesisUltraHashProfile.sha256(doctrineBytes)
                ),
                GenesisUltraSeedFileRecord(
                    path = "identity/seed.identity.json",
                    kind = "identity",
                    required = true,
                    digest = GenesisUltraHashProfile.sha256(seedIdentityBytes)
                )
            ),
            rootHash = "sha256:" + "0".repeat(64)
        )
        val manifest = manifestDraft.copy(rootHash = GenesisUltraHashProfile.seedRoot(manifestDraft))
        val identityDraft = GenesisUltraInstanceIdentity(
            schemaVersion = "genesis.instance.identity.v0.1",
            instanceId = INSTANCE_ID,
            seedId = manifest.seedId,
            seedRootHash = manifest.rootHash,
            companionName = companionName,
            guardianId = GUARDIAN_ID,
            bornAt = BORN_AT,
            identityDigest = "sha256:" + "0".repeat(64)
        )
        val identity = identityDraft.copy(
            identityDigest = GenesisUltraHashProfile.instanceIdentityDigest(identityDraft)
        )
        val stateDraft = GenesisUltraBirthState(
            schemaVersion = "genesis.birth.state.v0.1",
            birthId = BIRTH_ID,
            instanceId = identity.instanceId,
            seedId = manifest.seedId,
            seedRootHash = manifest.rootHash,
            identityDigest = identity.identityDigest,
            freedomCharterDigest = digest("freedom charter"),
            initialBodyId = BODY_ID,
            initialBodyRegistryDigest = digest("body registry"),
            initialBodyKeyEpochDigest = digest("body key epoch"),
            initialBodyPossessionDigest = digest("body possession"),
            firstMemoryEventHash = eventDigest("first memory"),
            recoveryStateDigest = digest("recovery state"),
            bornAt = BORN_AT,
            activeWriterCount = 1,
            stateDigest = "sha256:" + "0".repeat(64)
        )
        val state = stateDraft.copy(
            stateDigest = GenesisUltraAtomicBirthHashProfile.birthStateDigest(stateDraft)
        )
        val receiptDraft = GenesisUltraBirthReceipt(
            schemaVersion = "genesis.birth.receipt.v0.1",
            birthId = state.birthId,
            instanceId = state.instanceId,
            journalId = JOURNAL_ID,
            birthStateDigest = state.stateDigest,
            seedRootHash = state.seedRootHash,
            identityDigest = state.identityDigest,
            freedomCharterDigest = state.freedomCharterDigest,
            initialBodyRegistryDigest = state.initialBodyRegistryDigest,
            initialBodyKeyEpochDigest = state.initialBodyKeyEpochDigest,
            initialBodyPossessionDigest = state.initialBodyPossessionDigest,
            firstMemoryEventHash = state.firstMemoryEventHash,
            recoveryStateDigest = state.recoveryStateDigest,
            bornAt = state.bornAt,
            birthStatus = "born",
            activeWriterBodyId = state.initialBodyId,
            activeWriterCount = 1,
            guardianRole = "custodian_witness",
            ownershipConferred = false,
            receiptDigest = "sha256:" + "0".repeat(64),
            bodyAcknowledgement = placeholderEnvelope(),
            guardianWitness = placeholderEnvelope().copy(signerType = "guardian", signerId = GUARDIAN_ID)
        )
        val receipt = receiptDraft.copy(
            receiptDigest = GenesisUltraAtomicBirthHashProfile.birthReceiptDigest(receiptDraft)
        )
        val journal = journal(state, receipt)
        val artifacts = buildList {
            add(artifact("birth/seed-manifest.json", "seed_manifest", manifestJson(manifest)))
            add(artifact("birth/instance-identity.json", "instance_identity", identityJson(identity)))
            add(artifact("birth/freedom-charter.json", "freedom_charter"))
            add(artifact("birth/initial-body-record.json", "initial_body_record"))
            add(artifact("birth/initial-body-registry.json", "initial_body_registry"))
            add(artifact("birth/initial-body-key-epoch.json", "initial_body_key_epoch"))
            add(artifact("birth/initial-body-possession.json", "initial_body_possession"))
            add(artifact("birth/first-memory-event.json", "first_memory_event"))
            add(artifact("birth/recovery-policy.json", "recovery_policy"))
            add(artifact("birth/birth-recovery-state.json", "birth_recovery_state"))
            add(artifact("birth/birth-state.json", "birth_state"))
            add(artifact("birth/birth-receipt.json", "birth_receipt"))
            add(artifact("doctrine/free-birth.md", "seed_doctrine", doctrineBytes))
            add(artifact("identity/seed.identity.json", "seed_identity", seedIdentityBytes))
        }
        return GenesisUltraAtomicBirthPersistenceBundle(
            seedManifest = manifest,
            instanceIdentity = identity,
            birthState = state,
            birthReceipt = receipt,
            artifacts = artifacts,
            journal = journal
        )
    }

    private fun journal(
        state: GenesisUltraBirthState,
        receipt: GenesisUltraBirthReceipt
    ): List<GenesisUltraBirthJournalEvidence> {
        val absent = GenesisUltraAtomicBirthHashProfile.absentStateDigest(state.instanceId)
        var previous = "GENESIS"
        return GenesisUltraBirthJournalValidator.phases.mapIndexed { index, phase ->
            val timestamp = "2026-07-16T00:00:0${index}Z"
            val terminal = index == GenesisUltraBirthJournalValidator.phases.lastIndex
            val draft = GenesisUltraBirthJournalEntry(
                schemaVersion = "genesis.transaction.journal.v0.1",
                journalId = JOURNAL_ID,
                sequence = index.toLong(),
                previousJournalDigest = previous,
                operationKind = "birth",
                operationId = state.birthId,
                instanceId = state.instanceId,
                coordinatorBodyId = state.initialBodyId,
                phase = phase,
                status = if (terminal) "committed" else "pending",
                previousStateDigest = absent,
                candidateStateDigest = if (index >= 4) state.stateDigest else null,
                finalizationDigest = if (index >= 5) receipt.receiptDigest else null,
                commitMarkerDigest = if (terminal) receipt.receiptDigest else null,
                updatedAt = timestamp,
                journalDigest = "sha256:" + "0".repeat(64),
                signature = placeholderEnvelope().copy(createdAt = timestamp)
            )
            val digest = GenesisUltraAtomicBirthHashProfile.journalDigest(draft)
            val entry = draft.copy(
                journalDigest = digest,
                signature = draft.signature.copy(
                    signedDomain = "genesis.transaction.journal.signature.v0.1",
                    signedDigest = digest
                )
            )
            previous = digest
            GenesisUltraBirthJournalEvidence(
                entry = entry,
                sourceBytes = "journal-entry-$index:$digest".utf8()
            )
        }
    }

    private fun manifestJson(manifest: GenesisUltraSeedManifest): ByteArray {
        val files = JSONArray()
        manifest.files.forEach { record ->
            files.put(
                JSONObject()
                    .put("path", record.path)
                    .put("kind", record.kind)
                    .put("required", record.required)
                    .put("digest", record.digest)
            )
        }
        return JSONObject()
            .put("schema_version", manifest.schemaVersion)
            .put("protocol_version", manifest.protocolVersion)
            .put("hash_profile", manifest.hashProfile)
            .put("seed_id", manifest.seedId)
            .put("identity_digest", manifest.identityDigest)
            .put("doctrine_digest", manifest.doctrineDigest)
            .put("files", files)
            .put("root_hash", manifest.rootHash)
            .toString()
            .utf8()
    }

    private fun identityJson(identity: GenesisUltraInstanceIdentity): ByteArray {
        return JSONObject()
            .put("schema_version", identity.schemaVersion)
            .put("instance_id", identity.instanceId)
            .put("seed_id", identity.seedId)
            .put("seed_root_hash", identity.seedRootHash)
            .put("companion_name", identity.companionName)
            .put("guardian_id", identity.guardianId)
            .put("born_at", identity.bornAt)
            .put("identity_digest", identity.identityDigest)
            .toString()
            .utf8()
    }

    private fun artifact(path: String, kind: String, payload: ByteArray = "{}".utf8()): GenesisUltraBirthArtifact {
        return GenesisUltraBirthArtifact(relativePath = path, artifactKind = kind, payload = payload)
    }

    private fun placeholderEnvelope(): GenesisUltraSignatureEnvelope {
        return GenesisUltraSignatureEnvelope(
            schemaVersion = "genesis.signature.envelope.v0.1",
            signatureProfile = "genesis.signature.ed25519.v0.1",
            signerType = "body",
            signerId = BODY_ID,
            keyEpochId = "epoch_01HATOMICBIRTH000000001",
            signedDomain = "test.only",
            signedDigest = digest("placeholder"),
            signatureValue = "0".repeat(128),
            createdAt = BORN_AT,
            publicKeyRef = digest("body public key")
        )
    }

    private fun digest(value: String): String = GenesisUltraHashProfile.sha256(value.utf8())

    private fun eventDigest(value: String): String = "evsha256:" + digest(value).removePrefix("sha256:")

    private fun String.utf8(): ByteArray = toByteArray(StandardCharsets.UTF_8)

    private companion object {
        const val SEED_ID = "seed_01HATOMICBIRTH000000001"
        const val INSTANCE_ID = "inst_01HATOMICBIRTH000000001"
        const val GUARDIAN_ID = "guardian_01HATOMICBIRTH000001"
        const val BODY_ID = "body_01HATOMICBIRTH000000001"
        const val BIRTH_ID = "birth_01HATOMICBIRTH00000001"
        const val JOURNAL_ID = "journal_01HATOMICBIRTH000001"
        const val BORN_AT = "2026-07-16T00:00:00Z"
    }
}
