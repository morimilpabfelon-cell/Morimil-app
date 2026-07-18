package com.morimil.app.data.genesis.ultra

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.crypto.tink.subtle.Ed25519Sign
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
import java.lang.reflect.Modifier
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
        val commit = store.persistVerified(testOnlyVerifiedBirth(bundle), persistedAtMillis = 1000L)

        assertEquals(GenesisUltraPersistedBirthState.COMMITTED, store.readState())
        assertEquals(GenesisUltraBirthCommitEntity.PRIMARY_SLOT, commit.slotId)
        assertEquals(bundle.instanceIdentity.companionName, commit.companionName)
        assertEquals(bundle.birthReceipt.receiptDigest, commit.receiptDigest)
        assertEquals(bundle.artifacts.size.toLong(), commit.artifactCount)
        assertEquals(7L, commit.journalEntryCount)
        assertEquals(
            bundle.birthState.firstMemoryEventHash,
            store.readLivingMemoryRoot()?.event?.eventHash
        )

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
            store.persistVerified(
                testOnlyVerifiedBirth(validBundle()),
                persistedAtMillis = 1000L
            )
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
        store.persistVerified(testOnlyVerifiedBirth(original), persistedAtMillis = 1000L)

        val second = validBundle(companionName = "Otro Nombre")
        val failure = runCatching {
            store.persistVerified(testOnlyVerifiedBirth(second), persistedAtMillis = 2000L)
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        val persisted = database.genesisUltraBirthDao()
            .loadBirthCommit(GenesisUltraBirthCommitEntity.PRIMARY_SLOT)
        assertEquals("Genesis Libre", persisted?.companionName)
        assertEquals(1, database.genesisUltraBirthDao().countBirthCommits())
    }

    @Test
    fun persistenceEntryPointRequiresVerifiedTypeState() {
        val persistenceEntryPoints = GenesisUltraAtomicBirthStore::class.java.declaredMethods
            .filter { method ->
                method.name.startsWith("persist") && !Modifier.isPrivate(method.modifiers)
            }

        assertEquals(listOf("persistVerified"), persistenceEntryPoints.map { method -> method.name })
        assertEquals(
            GenesisUltraVerifiedAtomicBirth::class.java,
            persistenceEntryPoints.single().parameterTypes.first()
        )
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

    @Test
    fun committedLivingMemoryRootSurvivesARealDatabaseRestart() = runBlocking {
        val databaseName = "genesis-ultra-birth-restart.db"
        context.deleteDatabase(databaseName)
        var fileDatabase: MorimilDatabase? = null
        try {
            fileDatabase = Room.databaseBuilder(context, MorimilDatabase::class.java, databaseName)
                .allowMainThreadQueries()
                .build()
            val initialDatabase = requireNotNull(fileDatabase)
            val bundle = validBundle()
            GenesisUltraAtomicBirthStore(initialDatabase).persistVerified(
                testOnlyVerifiedBirth(bundle),
                persistedAtMillis = 1000L
            )
            initialDatabase.close()

            fileDatabase = Room.databaseBuilder(context, MorimilDatabase::class.java, databaseName)
                .allowMainThreadQueries()
                .build()
            val restartedStore = GenesisUltraAtomicBirthStore(requireNotNull(fileDatabase))

            assertEquals(GenesisUltraPersistedBirthState.COMMITTED, restartedStore.readState())
            assertEquals(
                bundle.birthState.firstMemoryEventHash,
                restartedStore.readLivingMemoryRoot()?.event?.eventHash
            )
        } finally {
            fileDatabase?.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun restartAuditRejectsAlteredMemoryEvenWhenRowDigestWasAlsoChanged() = runBlocking {
        val bundle = validBundle()
        val store = GenesisUltraAtomicBirthStore(database)
        store.persistVerified(testOnlyVerifiedBirth(bundle), persistedAtMillis = 1000L)
        val artifact = database.genesisUltraBirthDao()
            .loadBirthArtifacts(GenesisUltraBirthCommitEntity.PRIMARY_SLOT)
            .single { entity -> entity.artifactKind == "first_memory_event" }
        val root = JSONObject(artifact.payload.toString(StandardCharsets.UTF_8))
        val altered = GenesisUltraAtomicBirthDocumentParser.parseFirstMemoryEvent(
            root.toString()
        ).copy(eventType = "instance.reborn")
        val alteredHash = GenesisUltraAtomicBirthHashProfile.firstMemoryEventHash(altered)
        root.put("event_type", altered.eventType)
            .put("event_hash", alteredHash)
        root.getJSONObject("signature").put("signed_digest", alteredHash)
        val alteredBytes = root.toString().utf8()

        database.openHelper.writableDatabase.execSQL(
            """
            UPDATE genesis_ultra_birth_artifacts
            SET payload = ?, contentDigest = ?, byteCount = ?
            WHERE slotId = ? AND relativePath = ?
            """.trimIndent(),
            arrayOf(
                alteredBytes,
                GenesisUltraHashProfile.sha256(alteredBytes),
                alteredBytes.size.toLong(),
                artifact.slotId,
                artifact.relativePath
            )
        )

        assertEquals(GenesisUltraPersistedBirthState.INCONSISTENT, store.readState())
    }

    @Test
    fun canonicalAppendContinuesAtOneWithoutDuplicatingBirthRootOrLegacyMemory() = runBlocking {
        val bundle = validBundle()
        GenesisUltraAtomicBirthStore(database).persistVerified(
            testOnlyVerifiedBirth(bundle),
            persistedAtMillis = 1000L
        )
        val recovered = testOnlyRecoveredBirth(bundle)
        val memoryStore = GenesisUltraCanonicalMemoryStore(database)

        val first = memoryStore.append(recovered, bodyMemorySigner(), appendRequest(1))
        val second = memoryStore.append(recovered, bodyMemorySigner(), appendRequest(2))
        val stream = memoryStore.recoverStream(recovered, bodyMemoryKey())

        assertEquals(1L, first.event.sequence)
        assertEquals(bundle.birthState.firstMemoryEventHash, first.event.previousEventHash)
        assertEquals(2L, second.event.sequence)
        assertEquals(first.event.eventHash, second.event.previousEventHash)
        assertEquals(2, stream.postBirthEventCount)
        assertEquals(2, database.genesisUltraMemoryDao().countAll())
        assertEquals(0, database.memoryDao().countMemoryEvents())
        assertEquals(
            bundle.birthState.firstMemoryEventHash,
            stream.livingRoot.event.eventHash
        )
    }

    @Test
    fun signingFailureRollsBackWithoutUnsignedFallback() = runBlocking {
        val bundle = validBundle()
        GenesisUltraAtomicBirthStore(database).persistVerified(
            testOnlyVerifiedBirth(bundle),
            persistedAtMillis = 1000L
        )
        val failingSigner = object : GenesisUltraBodyMemorySigner {
            override val key = bodyMemoryKey()
            override fun sign(signingBytes: ByteArray): ByteArray = error("keystore_unavailable")
        }

        val failure = runCatching {
            GenesisUltraCanonicalMemoryStore(database).append(
                testOnlyRecoveredBirth(bundle),
                failingSigner,
                appendRequest(1)
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message?.contains("body_memory_signing_failed") == true)
        assertEquals(0, database.genesisUltraMemoryDao().countAll())
    }

    @Test
    fun coordinatedStoredSignatureTamperIsRejectedDuringRecovery() = runBlocking {
        val bundle = validBundle()
        GenesisUltraAtomicBirthStore(database).persistVerified(
            testOnlyVerifiedBirth(bundle),
            persistedAtMillis = 1000L
        )
        val recovered = testOnlyRecoveredBirth(bundle)
        val memoryStore = GenesisUltraCanonicalMemoryStore(database)
        memoryStore.append(recovered, bodyMemorySigner(), appendRequest(1))
        val entity = database.genesisUltraMemoryDao().loadAscending(INSTANCE_ID).single()
        val root = JSONObject(entity.sourceBytes.toString(StandardCharsets.UTF_8))
        root.getJSONObject("signature").put("signature_value", "00".repeat(64))
        val alteredSource = root.toString().utf8()
        database.openHelper.writableDatabase.execSQL(
            """
            UPDATE genesis_ultra_memory_events
            SET signatureValue = ?, sourceDigest = ?, sourceBytes = ?
            WHERE instanceId = ? AND sequence = ?
            """.trimIndent(),
            arrayOf(
                "00".repeat(64),
                GenesisUltraHashProfile.sha256(alteredSource),
                alteredSource,
                entity.instanceId,
                entity.sequence
            )
        )

        val failure = runCatching {
            memoryStore.recoverStream(recovered, bodyMemoryKey())
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message?.contains("canonical_memory_signature_invalid:1") == true)
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
        val keyDraft = GenesisUltraKeyEpoch(
            schemaVersion = "genesis.key.epoch.v0.1",
            keyEpochId = BODY_KEY_EPOCH_ID,
            instanceId = identity.instanceId,
            bodyId = BODY_ID,
            epochNumber = 0L,
            publicKeyFingerprint = bodyMemoryKey().publicKeyRef,
            createdAt = "2026-07-15T23:59:56Z",
            status = "active",
            previousEpochId = null,
            rotationAuthorizationRef = null,
            epochDigest = "sha256:" + "0".repeat(64),
            signature = null
        )
        val keyEpoch = keyDraft.copy(
            epochDigest = GenesisUltraHashProfile.keyEpochDigest(keyDraft)
        )
        val firstMemoryDraft = GenesisUltraFirstMemoryEvent(
            schemaVersion = "genesis.memory.event.v0.1",
            hashProfile = GenesisUltraHashProfile.FIELD_PROFILE,
            eventId = "event_01HATOMICBIRTH00000001",
            instanceId = identity.instanceId,
            bodyId = BODY_ID,
            sequence = 0L,
            previousEventHash = "GENESIS",
            eventType = "instance.birth",
            actor = "system",
            contentDigest = identity.identityDigest,
            contentType = "application/vnd.genesis.birth+json",
            contentRef = null,
            observedAt = identity.bornAt,
            provenanceDigest = manifest.rootHash,
            provenanceRef = null,
            privacy = "private_local",
            eventHash = "evsha256:" + "0".repeat(64),
            signature = placeholderEnvelope()
        )
        val firstMemoryHash = GenesisUltraAtomicBirthHashProfile.firstMemoryEventHash(
            firstMemoryDraft
        )
        val firstMemory = firstMemoryDraft.copy(
            eventHash = firstMemoryHash,
            signature = firstMemoryDraft.signature.copy(
                signedDomain = "genesis.memory.event.signature.v0.1",
                signedDigest = firstMemoryHash
            )
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
            initialBodyKeyEpochDigest = keyEpoch.epochDigest,
            initialBodyPossessionDigest = digest("body possession"),
            firstMemoryEventHash = firstMemory.eventHash,
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
            add(artifact("birth/seed-signature.json", "seed_signature"))
            add(artifact("birth/instance-identity.json", "instance_identity", identityJson(identity)))
            add(artifact("birth/freedom-charter.json", "freedom_charter"))
            add(artifact("birth/initial-body-record.json", "initial_body_record"))
            add(artifact("birth/initial-body-registry.json", "initial_body_registry"))
            add(
                artifact(
                    "birth/initial-body-key-epoch.json",
                    "initial_body_key_epoch",
                    keyEpochJson(keyEpoch)
                )
            )
            add(artifact("birth/initial-body-possession.json", "initial_body_possession"))
            add(
                artifact(
                    "birth/first-memory-event.json",
                    "first_memory_event",
                    firstMemoryJson(firstMemory)
                )
            )
            add(artifact("birth/recovery-policy.json", "recovery_policy"))
            add(artifact("birth/birth-recovery-state.json", "birth_recovery_state"))
            add(artifact("birth/birth-state.json", "birth_state", birthStateJson(state)))
            add(artifact("birth/birth-receipt.json", "birth_receipt", birthReceiptJson(receipt)))
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

    /**
     * Store mechanics are tested independently from cryptography. This bridge
     * exists only in the instrumentation-test APK; application code has no raw
     * bundle factory and must call GenesisUltraAtomicBirthEvidenceVerifier.
     */
    private fun testOnlyVerifiedBirth(
        bundle: GenesisUltraAtomicBirthPersistenceBundle
    ): GenesisUltraVerifiedAtomicBirth {
        val constructor = GenesisUltraVerifiedAtomicBirth::class.java.getDeclaredConstructor(
            GenesisUltraAtomicBirthPersistenceBundle::class.java
        )
        constructor.isAccessible = true
        return constructor.newInstance(bundle)
    }

    private fun testOnlyRecoveredBirth(
        bundle: GenesisUltraAtomicBirthPersistenceBundle
    ): GenesisUltraRecoveredAtomicBirth {
        val verified = testOnlyVerifiedBirth(bundle)
        val constructor = GenesisUltraRecoveredAtomicBirth::class.java.getDeclaredConstructor(
            GenesisUltraVerifiedAtomicBirth::class.java,
            GenesisUltraLivingMemoryRoot::class.java
        )
        constructor.isAccessible = true
        return constructor.newInstance(verified, verified.copyLivingMemoryRoot())
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
                sourceBytes = journalJson(entry)
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

    private fun firstMemoryJson(event: GenesisUltraFirstMemoryEvent): ByteArray {
        return JSONObject()
            .put("schema_version", event.schemaVersion)
            .put("hash_profile", event.hashProfile)
            .put("event_id", event.eventId)
            .put("instance_id", event.instanceId)
            .put("body_id", event.bodyId)
            .put("sequence", event.sequence)
            .put("previous_event_hash", event.previousEventHash)
            .put("event_type", event.eventType)
            .put("actor", event.actor)
            .put("content_digest", event.contentDigest)
            .put("content_type", event.contentType)
            .put("content_ref", event.contentRef ?: JSONObject.NULL)
            .put("observed_at", event.observedAt)
            .put("provenance_digest", event.provenanceDigest)
            .put("provenance_ref", event.provenanceRef ?: JSONObject.NULL)
            .put("privacy", event.privacy)
            .put("event_hash", event.eventHash)
            .put("signature", signatureJson(event.signature))
            .toString()
            .utf8()
    }

    private fun keyEpochJson(epoch: GenesisUltraKeyEpoch): ByteArray {
        return JSONObject()
            .put("schema_version", epoch.schemaVersion)
            .put("key_epoch_id", epoch.keyEpochId)
            .put("instance_id", epoch.instanceId)
            .put("body_id", epoch.bodyId)
            .put("epoch_number", epoch.epochNumber)
            .put("public_key_fingerprint", epoch.publicKeyFingerprint)
            .put("created_at", epoch.createdAt)
            .put("status", epoch.status)
            .put("previous_epoch_id", JSONObject.NULL)
            .put("rotation_authorization_ref", JSONObject.NULL)
            .put("epoch_digest", epoch.epochDigest)
            .put("signature", JSONObject.NULL)
            .toString()
            .utf8()
    }

    private fun birthStateJson(state: GenesisUltraBirthState): ByteArray {
        return JSONObject()
            .put("schema_version", state.schemaVersion)
            .put("birth_id", state.birthId)
            .put("instance_id", state.instanceId)
            .put("seed_id", state.seedId)
            .put("seed_root_hash", state.seedRootHash)
            .put("identity_digest", state.identityDigest)
            .put("freedom_charter_digest", state.freedomCharterDigest)
            .put("initial_body_id", state.initialBodyId)
            .put("initial_body_registry_digest", state.initialBodyRegistryDigest)
            .put("initial_body_key_epoch_digest", state.initialBodyKeyEpochDigest)
            .put("initial_body_possession_digest", state.initialBodyPossessionDigest)
            .put("first_memory_event_hash", state.firstMemoryEventHash)
            .put("recovery_state_digest", state.recoveryStateDigest)
            .put("born_at", state.bornAt)
            .put("active_writer_count", state.activeWriterCount)
            .put("state_digest", state.stateDigest)
            .toString()
            .utf8()
    }

    private fun birthReceiptJson(receipt: GenesisUltraBirthReceipt): ByteArray {
        return JSONObject()
            .put("schema_version", receipt.schemaVersion)
            .put("birth_id", receipt.birthId)
            .put("instance_id", receipt.instanceId)
            .put("journal_id", receipt.journalId)
            .put("birth_state_digest", receipt.birthStateDigest)
            .put("seed_root_hash", receipt.seedRootHash)
            .put("identity_digest", receipt.identityDigest)
            .put("freedom_charter_digest", receipt.freedomCharterDigest)
            .put("initial_body_registry_digest", receipt.initialBodyRegistryDigest)
            .put("initial_body_key_epoch_digest", receipt.initialBodyKeyEpochDigest)
            .put("initial_body_possession_digest", receipt.initialBodyPossessionDigest)
            .put("first_memory_event_hash", receipt.firstMemoryEventHash)
            .put("recovery_state_digest", receipt.recoveryStateDigest)
            .put("born_at", receipt.bornAt)
            .put("birth_status", receipt.birthStatus)
            .put("active_writer_body_id", receipt.activeWriterBodyId)
            .put("active_writer_count", receipt.activeWriterCount)
            .put("guardian_role", receipt.guardianRole)
            .put("ownership_conferred", receipt.ownershipConferred)
            .put("receipt_digest", receipt.receiptDigest)
            .put("body_acknowledgement", signatureJson(receipt.bodyAcknowledgement))
            .put("guardian_witness", signatureJson(receipt.guardianWitness))
            .toString()
            .utf8()
    }

    private fun signatureJson(signature: GenesisUltraSignatureEnvelope): JSONObject {
        return JSONObject()
            .put("schema_version", signature.schemaVersion)
            .put("signature_profile", signature.signatureProfile)
            .put("signer_type", signature.signerType)
            .put("signer_id", signature.signerId)
            .put("key_epoch_id", signature.keyEpochId)
            .put("signed_domain", signature.signedDomain)
            .put("signed_digest", signature.signedDigest)
            .put("signature_value", signature.signatureValue)
            .put("created_at", signature.createdAt)
            .put("public_key_ref", signature.publicKeyRef)
    }

    private fun journalJson(entry: GenesisUltraBirthJournalEntry): ByteArray {
        return JSONObject()
            .put("schema_version", entry.schemaVersion)
            .put("journal_id", entry.journalId)
            .put("sequence", entry.sequence)
            .put("previous_journal_digest", entry.previousJournalDigest)
            .put("operation_kind", entry.operationKind)
            .put("operation_id", entry.operationId)
            .put("instance_id", entry.instanceId)
            .put("coordinator_body_id", entry.coordinatorBodyId)
            .put("phase", entry.phase)
            .put("status", entry.status)
            .put("previous_state_digest", entry.previousStateDigest)
            .put("candidate_state_digest", entry.candidateStateDigest ?: JSONObject.NULL)
            .put("finalization_digest", entry.finalizationDigest ?: JSONObject.NULL)
            .put("commit_marker_digest", entry.commitMarkerDigest ?: JSONObject.NULL)
            .put("updated_at", entry.updatedAt)
            .put("journal_digest", entry.journalDigest)
            .put("signature", signatureJson(entry.signature))
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
            keyEpochId = BODY_KEY_EPOCH_ID,
            signedDomain = "test.only",
            signedDigest = digest("placeholder"),
            signatureValue = "0".repeat(128),
            createdAt = BORN_AT,
            publicKeyRef = bodyMemoryKey().publicKeyRef
        )
    }

    private fun appendRequest(index: Int): GenesisUltraCanonicalMemoryAppendRequest {
        return GenesisUltraCanonicalMemoryAppendRequest(
            eventId = "event_01HPOSTBIRTH000000000$index",
            eventType = "memory.observation",
            actor = "instance",
            contentDigest = digest("content-$index"),
            contentType = "application/vnd.genesis.memory+json",
            observedAt = "2026-07-16T00:00:0${index}Z",
            provenanceDigest = digest("provenance-$index")
        )
    }

    private fun bodyMemorySigner(): GenesisUltraBodyMemorySigner {
        val pair = bodyKeyPair()
        return GenesisUltraTinkBodyMemorySigner(
            key = bodyMemoryKey(),
            signer = Ed25519Sign(pair.privateKey)
        )
    }

    private fun bodyMemoryKey(): GenesisUltraBodyMemoryKey {
        val publicKey = bodyKeyPair().publicKey
        return GenesisUltraBodyMemoryKey(
            instanceId = INSTANCE_ID,
            bodyId = BODY_ID,
            keyEpochId = BODY_KEY_EPOCH_ID,
            publicKeyRef = GenesisUltraHashProfile.sha256(publicKey),
            rawPublicKey = publicKey
        )
    }

    private fun bodyKeyPair(): Ed25519Sign.KeyPair {
        return Ed25519Sign.KeyPair.newKeyPairFromSeed(ByteArray(32) { 0x42.toByte() })
    }

    private fun digest(value: String): String = GenesisUltraHashProfile.sha256(value.utf8())

    private fun String.utf8(): ByteArray = toByteArray(StandardCharsets.UTF_8)

    private companion object {
        const val SEED_ID = "seed_01HATOMICBIRTH000000001"
        const val INSTANCE_ID = "inst_01HATOMICBIRTH000000001"
        const val GUARDIAN_ID = "guardian_01HATOMICBIRTH000001"
        const val BODY_ID = "body_01HATOMICBIRTH000000001"
        const val BODY_KEY_EPOCH_ID = "epoch_01HATOMICBIRTH000000001"
        const val BIRTH_ID = "birth_01HATOMICBIRTH00000001"
        const val JOURNAL_ID = "journal_01HATOMICBIRTH000001"
        const val BORN_AT = "2026-07-16T00:00:00Z"
    }
}
