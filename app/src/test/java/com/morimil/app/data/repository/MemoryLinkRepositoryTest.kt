package com.morimil.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryLinkRepositoryTest {
    @Test
    fun memoryLinkIdUsesDeterministicSha256Suffix() {
        val linkId = MemoryLinkRepository.buildMemoryLinkId(
            createdAtMillis = 1234567890L,
            sourceId = "sha256:source",
            targetId = "sha256:target",
            relation = MemoryLinkRepository.RELATION_DERIVED_FROM
        )
        val repeated = MemoryLinkRepository.buildMemoryLinkId(
            createdAtMillis = 1234567890L,
            sourceId = "sha256:source",
            targetId = "sha256:target",
            relation = MemoryLinkRepository.RELATION_DERIVED_FROM
        )
        val changedTarget = MemoryLinkRepository.buildMemoryLinkId(
            createdAtMillis = 1234567890L,
            sourceId = "sha256:source",
            targetId = "sha256:other-target",
            relation = MemoryLinkRepository.RELATION_DERIVED_FROM
        )

        assertTrue(linkId.matches(Regex("^mlink_1234567890_[a-f0-9]{20}$")))
        assertEquals(repeated, linkId)
        assertNotEquals(changedTarget, linkId)
    }
}
