package com.morimil.app.data.repository

import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryLinkRepositoryTest {
    @Test
    fun memoryLinkIdMatchesGenesisContractShape() {
        val linkId = MemoryLinkRepository.buildMemoryLinkId(
            createdAtMillis = 1234567890L,
            sourceId = "sha256:source",
            targetId = "sha256:target",
            relation = MemoryLinkRepository.RELATION_DERIVED_FROM
        )

        assertTrue(linkId.matches(Regex("^mlink_[0-9]{6,}$")))
    }
}
