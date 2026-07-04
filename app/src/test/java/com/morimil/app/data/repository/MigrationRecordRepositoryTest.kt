package com.morimil.app.data.repository

import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationRecordRepositoryTest {
    @Test
    fun migrationIdMatchesGenesisContractShape() {
        val migrationId = MigrationRecordRepository.buildMigrationId(
            createdAtMillis = 1234567890L,
            migrationType = "schema_upgrade",
            fromVersion = "4",
            toVersion = "5"
        )

        assertTrue(migrationId.matches(Regex("^mig_[0-9]{6,}$")))
    }
}
