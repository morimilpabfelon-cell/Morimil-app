package com.morimil.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryEventClassifierTest {
    @Test
    fun accentedSpanishAcknowledgementsNormalizeToChatNoise() {
        val si = MemoryEventClassifier.classify(
            eventType = "conversation.user_message",
            actor = "user",
            body = "s\u00ed"
        )
        val aja = MemoryEventClassifier.classify(
            eventType = "conversation.user_message",
            actor = "user",
            body = "aj\u00e1"
        )

        assertEquals("chat_noise", si.memoryKind)
        assertEquals("chat_noise", aja.memoryKind)
        assertEquals(8, si.importance)
        assertEquals(8, aja.importance)
    }

    @Test
    fun explicitShortMemoryCommandIsNotTinyAckNoise() {
        val classification = MemoryEventClassifier.classify(
            eventType = "conversation.user_message",
            actor = "user",
            body = "recuerda esto"
        )

        assertTrue(classification.memoryKind != "chat_noise")
        assertTrue(classification.importance > 8)
    }

    @Test
    fun autobiographicalUpdateIsIdentityMemory() {
        val classification = MemoryEventClassifier.classify(
            eventType = "memory.autobiography_updated",
            actor = "system",
            body = "Autobiografia local consolidada desde rest cycle."
        )

        assertEquals("identity", classification.memoryKind)
        assertTrue("autobiography" in classification.tags)
        assertTrue("identity" in classification.tags)
        assertEquals(100, classification.importance)
    }
}
