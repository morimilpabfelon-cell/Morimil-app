package com.morimil.app.core.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StableIdDigestTest {
    @Test
    fun shortSha256HexIsStableAndNamespaceScoped() {
        val digest = StableIdDigest.shortSha256Hex(
            namespace = "memory_link",
            parts = listOf("1234567890", "source", "target", "derived_from")
        )
        val repeated = StableIdDigest.shortSha256Hex(
            namespace = "memory_link",
            parts = listOf("1234567890", "source", "target", "derived_from")
        )
        val differentNamespace = StableIdDigest.shortSha256Hex(
            namespace = "migration_record",
            parts = listOf("1234567890", "source", "target", "derived_from")
        )

        assertTrue(digest.matches(Regex("^[a-f0-9]{20}$")))
        assertEquals(repeated, digest)
        assertNotEquals(differentNamespace, digest)
    }
}
