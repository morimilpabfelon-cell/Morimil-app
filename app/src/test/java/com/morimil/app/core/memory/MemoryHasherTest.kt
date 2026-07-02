package com.morimil.app.core.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryHasherTest {
    @Test
    fun sameFieldsProduceSameHash() {
        val hasher = MemoryHasher()
        val fieldsA = mapOf(
            "eventType" to "conversation.user_message",
            "actor" to "user",
            "body" to "hola",
            "importance" to 40
        )
        val fieldsB = mapOf(
            "importance" to 40,
            "body" to "hola",
            "actor" to "user",
            "eventType" to "conversation.user_message"
        )

        assertEquals(hasher.hash(fieldsA), hasher.hash(fieldsB))
    }

    @Test
    fun changedBodyChangesHash() {
        val hasher = MemoryHasher()
        val first = hasher.hash(mapOf("body" to "hola"))
        val second = hasher.hash(mapOf("body" to "hola editado"))

        assertNotEquals(first, second)
    }

    @Test
    fun hashUsesSha256Prefix() {
        val hasher = MemoryHasher()

        assertTrue(hasher.hash(mapOf("body" to "hola")).startsWith("sha256:"))
    }
}
