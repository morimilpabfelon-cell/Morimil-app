package com.morimil.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationRecordRepositoryTest {
    @Test
    fun migrationIdUsesDeterministicSha256Suffix() {
        val migrationId = MigrationRecordRepository.buildMigrationId(
            createdAtMillis = 1234567890L,
            migrationType = "schema_upgrade",
            fromVersion = "4",
            toVersion = "5"
        )
        val repeated = MigrationRecordRepository.buildMigrationId(
            createdAtMillis = 1234567890L,
            migrationType = "schema_upgrade",
            fromVersion = "4",
            toVersion = "5"
        )
        val changedVersion = MigrationRecordRepository.buildMigrationId(
            createdAtMillis = 1234567890L,
            migrationType = "schema_upgrade",
            fromVersion = "4",
            toVersion = "6"
        )

        assertTrue(migrationId.matches(Regex("^mig_1234567890_[a-f0-9]{20}$")))
        assertEquals(repeated, migrationId)
        assertNotEquals(changedVersion, migrationId)
    }
}
