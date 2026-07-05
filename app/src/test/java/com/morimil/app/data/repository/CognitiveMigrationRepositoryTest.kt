package com.morimil.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class CognitiveMigrationRepositoryTest {
    @Test
    fun cognitiveMigrationTypeIsStableForAuditFilters() {
        assertEquals(
            "cognitive.memory_refinement",
            CognitiveMigrationRepository.COGNITIVE_MIGRATION_TYPE
        )
    }
}
