package com.morimil.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MemoryMessageEntity::class,
        DecisionLogEntity::class,
        ProjectStateEntity::class,
        UserWorkspaceEntity::class,
        LocalInstanceIdentityEntity::class,
        GenesisCoreEntity::class,
        MemoryEventEntity::class,
        MemorySnapshotEntity::class,
        ImprovementDecisionHistoryEntity::class,
        GenesisUltraBirthCommitEntity::class,
        GenesisUltraBirthArtifactEntity::class,
        GenesisUltraBirthJournalEntity::class,
        GenesisUltraMemoryEventEntity::class
    ],
    version = 11,
    exportSchema = true
)
abstract class MorimilDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun genesisUltraBirthDao(): GenesisUltraBirthDao
    abstract fun genesisUltraMemoryDao(): GenesisUltraMemoryDao

    companion object {
        @Volatile
        private var instance: MorimilDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS user_workspace (
                        workspaceId TEXT NOT NULL PRIMARY KEY,
                        displayName TEXT NOT NULL,
                        genesisSource TEXT NOT NULL,
                        localPrimary INTEGER NOT NULL,
                        optionalRepoOwner TEXT,
                        optionalRepoName TEXT,
                        optionalRepoPrivate INTEGER NOT NULL,
                        repoProposalApproved INTEGER NOT NULL,
                        updatedAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS local_instance_identity (
                        instanceId TEXT NOT NULL PRIMARY KEY,
                        alias TEXT NOT NULL,
                        bornAtMillis INTEGER NOT NULL,
                        genesisAgentId TEXT NOT NULL,
                        genesisRole TEXT NOT NULL,
                        genesisRiskTier TEXT NOT NULL,
                        genesisSchemaVersion TEXT NOT NULL,
                        forkOwner TEXT NOT NULL,
                        forkRepo TEXT NOT NULL,
                        forkHtmlUrl TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS genesis_core (
                        coreId TEXT NOT NULL PRIMARY KEY,
                        instanceId TEXT NOT NULL,
                        aliasAtBirth TEXT NOT NULL,
                        copiedAtMillis INTEGER NOT NULL,
                        sourceOrigin TEXT NOT NULL,
                        schemaVersion TEXT NOT NULL,
                        agentId TEXT NOT NULL,
                        role TEXT NOT NULL,
                        owner TEXT NOT NULL,
                        riskTier TEXT NOT NULL,
                        doctrineRef TEXT NOT NULL,
                        policyRef TEXT NOT NULL,
                        allowedActionsJson TEXT NOT NULL,
                        disallowedActionsJson TEXT NOT NULL,
                        doctrineText TEXT,
                        policyText TEXT,
                        contentSha256 TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS memory_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        genesisCoreId TEXT NOT NULL,
                        eventType TEXT NOT NULL,
                        actor TEXT NOT NULL,
                        body TEXT NOT NULL,
                        importance INTEGER NOT NULL,
                        createdAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS memory_snapshots (
                        snapshotId TEXT NOT NULL PRIMARY KEY,
                        genesisCoreId TEXT NOT NULL,
                        summary TEXT NOT NULL,
                        eventCount INTEGER NOT NULL,
                        messageCount INTEGER NOT NULL,
                        updatedAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS local_instance_identity_new (
                        instanceId TEXT NOT NULL PRIMARY KEY,
                        alias TEXT NOT NULL,
                        bornAtMillis INTEGER NOT NULL,
                        genesisAgentId TEXT NOT NULL,
                        genesisRole TEXT NOT NULL,
                        genesisRiskTier TEXT NOT NULL,
                        genesisSchemaVersion TEXT NOT NULL,
                        localMemoryOwner TEXT NOT NULL,
                        localMemoryName TEXT NOT NULL,
                        localMemoryUri TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO local_instance_identity_new (
                        instanceId,
                        alias,
                        bornAtMillis,
                        genesisAgentId,
                        genesisRole,
                        genesisRiskTier,
                        genesisSchemaVersion,
                        localMemoryOwner,
                        localMemoryName,
                        localMemoryUri
                    )
                    SELECT
                        instanceId,
                        alias,
                        bornAtMillis,
                        genesisAgentId,
                        genesisRole,
                        genesisRiskTier,
                        genesisSchemaVersion,
                        forkOwner,
                        forkRepo,
                        forkHtmlUrl
                    FROM local_instance_identity
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE local_instance_identity")
                db.execSQL("ALTER TABLE local_instance_identity_new RENAME TO local_instance_identity")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE memory_events ADD COLUMN previousEventHash TEXT")
                db.execSQL("ALTER TABLE memory_events ADD COLUMN genesisCoreHash TEXT NOT NULL DEFAULT 'sha256:legacy-unverified'")
                db.execSQL("ALTER TABLE memory_events ADD COLUMN eventHash TEXT NOT NULL DEFAULT 'sha256:legacy-unverified'")
                db.execSQL("ALTER TABLE memory_events ADD COLUMN hashAlgorithm TEXT NOT NULL DEFAULT 'sha256'")
                db.execSQL("ALTER TABLE memory_events ADD COLUMN canonicalization TEXT NOT NULL DEFAULT 'morimil.memory_event_hash.v1'")
                db.execSQL("ALTER TABLE memory_events ADD COLUMN signatureAlgorithm TEXT")
                db.execSQL("ALTER TABLE memory_events ADD COLUMN eventSignature TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_events_eventHash ON memory_events(eventHash)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE memory_events ADD COLUMN source TEXT NOT NULL DEFAULT 'system'")
                db.execSQL("ALTER TABLE memory_events ADD COLUMN contextTag TEXT NOT NULL DEFAULT 'local_runtime'")
                db.execSQL("ALTER TABLE memory_events ADD COLUMN privacyVisibility TEXT NOT NULL DEFAULT 'private_local'")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_events_createdAtMillis ON memory_events(createdAtMillis)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE memory_events ADD COLUMN memoryKind TEXT NOT NULL DEFAULT 'observation'")
                db.execSQL("ALTER TABLE memory_events ADD COLUMN tagsJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE memory_events ADD COLUMN evidenceJson TEXT NOT NULL DEFAULT '{}'")
                db.execSQL("ALTER TABLE memory_events ADD COLUMN confidence INTEGER NOT NULL DEFAULT 70")
                db.execSQL("ALTER TABLE memory_events ADD COLUMN userConfirmed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_events_memoryKind ON memory_events(memoryKind)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_events_importance ON memory_events(importance)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS improvement_decision_history (
                        historyId TEXT NOT NULL PRIMARY KEY,
                        proposalId TEXT NOT NULL,
                        proposalTitle TEXT NOT NULL,
                        decision TEXT NOT NULL,
                        decidedAtMillis INTEGER NOT NULL,
                        source TEXT NOT NULL,
                        schemaVersion INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_improvement_decision_history_proposalId ON improvement_decision_history(proposalId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_improvement_decision_history_decidedAtMillis ON improvement_decision_history(decidedAtMillis)")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS genesis_ultra_birth_commit (
                        slotId TEXT NOT NULL,
                        schemaVersion TEXT NOT NULL,
                        birthId TEXT NOT NULL,
                        instanceId TEXT NOT NULL,
                        companionName TEXT NOT NULL,
                        seedId TEXT NOT NULL,
                        seedRootHash TEXT NOT NULL,
                        identityDigest TEXT NOT NULL,
                        freedomCharterDigest TEXT NOT NULL,
                        initialBodyId TEXT NOT NULL,
                        initialBodyRegistryDigest TEXT NOT NULL,
                        initialBodyKeyEpochDigest TEXT NOT NULL,
                        initialBodyPossessionDigest TEXT NOT NULL,
                        firstMemoryEventHash TEXT NOT NULL,
                        recoveryStateDigest TEXT NOT NULL,
                        birthStateDigest TEXT NOT NULL,
                        receiptDigest TEXT NOT NULL,
                        journalId TEXT NOT NULL,
                        bornAt TEXT NOT NULL,
                        birthStatus TEXT NOT NULL,
                        activeWriterBodyId TEXT NOT NULL,
                        activeWriterCount INTEGER NOT NULL,
                        guardianRole TEXT NOT NULL,
                        ownershipConferred INTEGER NOT NULL,
                        artifactCount INTEGER NOT NULL,
                        journalEntryCount INTEGER NOT NULL,
                        persistedAtMillis INTEGER NOT NULL,
                        PRIMARY KEY(slotId)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_genesis_ultra_birth_commit_birthId " +
                        "ON genesis_ultra_birth_commit(birthId)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_genesis_ultra_birth_commit_instanceId " +
                        "ON genesis_ultra_birth_commit(instanceId)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_genesis_ultra_birth_commit_journalId " +
                        "ON genesis_ultra_birth_commit(journalId)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS genesis_ultra_birth_artifacts (
                        slotId TEXT NOT NULL,
                        relativePath TEXT NOT NULL,
                        artifactKind TEXT NOT NULL,
                        contentDigest TEXT NOT NULL,
                        byteCount INTEGER NOT NULL,
                        payload BLOB NOT NULL,
                        PRIMARY KEY(slotId, relativePath)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_genesis_ultra_birth_artifacts_slotId_artifactKind " +
                        "ON genesis_ultra_birth_artifacts(slotId, artifactKind)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS genesis_ultra_birth_journal (
                        slotId TEXT NOT NULL,
                        schemaVersion TEXT NOT NULL,
                        journalId TEXT NOT NULL,
                        sequence INTEGER NOT NULL,
                        previousJournalDigest TEXT NOT NULL,
                        operationKind TEXT NOT NULL,
                        operationId TEXT NOT NULL,
                        instanceId TEXT NOT NULL,
                        coordinatorBodyId TEXT NOT NULL,
                        phase TEXT NOT NULL,
                        status TEXT NOT NULL,
                        previousStateDigest TEXT NOT NULL,
                        candidateStateDigest TEXT,
                        finalizationDigest TEXT,
                        commitMarkerDigest TEXT,
                        updatedAt TEXT NOT NULL,
                        journalDigest TEXT NOT NULL,
                        signatureSchemaVersion TEXT NOT NULL,
                        signatureProfile TEXT NOT NULL,
                        signerType TEXT NOT NULL,
                        signerId TEXT NOT NULL,
                        keyEpochId TEXT NOT NULL,
                        signedDomain TEXT NOT NULL,
                        signedDigest TEXT NOT NULL,
                        signatureValue TEXT NOT NULL,
                        signatureCreatedAt TEXT NOT NULL,
                        publicKeyRef TEXT NOT NULL,
                        sourceDigest TEXT NOT NULL,
                        sourceBytes BLOB NOT NULL,
                        PRIMARY KEY(slotId, sequence)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_genesis_ultra_birth_journal_journalId_sequence " +
                        "ON genesis_ultra_birth_journal(journalId, sequence)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_genesis_ultra_birth_journal_journalDigest " +
                        "ON genesis_ultra_birth_journal(journalDigest)"
                )
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS genesis_ultra_memory_events (
                        instanceId TEXT NOT NULL,
                        sequence INTEGER NOT NULL,
                        schemaVersion TEXT NOT NULL,
                        hashProfile TEXT NOT NULL,
                        eventId TEXT NOT NULL,
                        bodyId TEXT NOT NULL,
                        previousEventHash TEXT NOT NULL,
                        eventType TEXT NOT NULL,
                        actor TEXT NOT NULL,
                        contentDigest TEXT NOT NULL,
                        contentType TEXT NOT NULL,
                        contentRef TEXT,
                        observedAt TEXT NOT NULL,
                        provenanceDigest TEXT NOT NULL,
                        provenanceRef TEXT,
                        privacy TEXT NOT NULL,
                        eventHash TEXT NOT NULL,
                        signatureSchemaVersion TEXT NOT NULL,
                        signatureProfile TEXT NOT NULL,
                        signerType TEXT NOT NULL,
                        signerId TEXT NOT NULL,
                        keyEpochId TEXT NOT NULL,
                        signedDomain TEXT NOT NULL,
                        signedDigest TEXT NOT NULL,
                        signatureValue TEXT NOT NULL,
                        signatureCreatedAt TEXT NOT NULL,
                        publicKeyRef TEXT NOT NULL,
                        sourceDigest TEXT NOT NULL,
                        sourceBytes BLOB NOT NULL,
                        PRIMARY KEY(instanceId, sequence)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_genesis_ultra_memory_events_eventId " +
                        "ON genesis_ultra_memory_events(eventId)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_genesis_ultra_memory_events_eventHash " +
                        "ON genesis_ultra_memory_events(eventHash)"
                )
            }
        }

        fun getInstance(context: Context): MorimilDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MorimilDatabase::class.java,
                    "morimil_memory.db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11
                    )
                    .build()
                    .also { instance = it }
            }
        }
    }
}
