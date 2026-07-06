package com.morimil.app.data.repository

import com.morimil.app.core.memory.MemoryIntegrityCore
import com.morimil.app.data.local.MemoryEventEntity
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutobiographicalMemoryConsolidatorTest {
    @Test
    fun buildsAutobiographicalDraftFromMeaningfulMemoryEvents() {
        val events = listOf(
            event(
                eventHash = "sha256:identity",
                memoryKind = "identity",
                body = "Morimil nacio como instancia local con memoria privada.",
                importance = 100,
                userConfirmed = false
            ),
            event(
                eventHash = "sha256:decision",
                memoryKind = "decision",
                body = "Regla: decision importante debe entrar al torrente firmado.",
                importance = 92,
                userConfirmed = true
            ),
            event(
                eventHash = "sha256:project",
                eventType = "project.vault_created",
                memoryKind = "conversation",
                tagsJson = "[\"project\"]",
                body = "Boveda de proyecto creada: Morimil-app.",
                importance = 88
            ),
            event(
                eventHash = "sha256:correction",
                memoryKind = "correction",
                body = "Correccion: no crear organos desconectados del torrente.",
                importance = 92,
                userConfirmed = true
            ),
            event(
                eventHash = "sha256:noise",
                memoryKind = "chat_noise",
                body = "dale",
                importance = 8
            )
        )

        val draft = AutobiographicalMemoryConsolidator.build(
            alias = "Morimil",
            sourceRestCycleEventHash = "sha256:rest-cycle",
            events = events,
            generatedAtMillis = 1234L
        )
        val evidence = JSONObject(draft.evidenceJson)

        assertTrue(draft.selfSummary.contains("Morimil (Morimil)"))
        assertTrue(draft.selfSummary.contains("decision importante"))
        assertTrue(draft.activeGoals.contains("Boveda de proyecto"))
        assertTrue(draft.importantConstraints.contains("organos desconectados"))
        assertFalse(draft.selfSummary.contains("dale"))
        assertEquals("morimil.autobiographical_consolidation.v1", evidence.getString("schema"))
        assertEquals("sha256:rest-cycle", evidence.getString("source_rest_cycle_event_hash"))
        assertEquals(5, evidence.getInt("source_event_count"))
        assertEquals(1, evidence.getInt("project_signal_count"))
    }

    @Test
    fun eventBodyIncludesSnapshotSections() {
        val draft = AutobiographicalMemoryDraft(
            alias = "Morimil",
            selfSummary = "self summary",
            stableTraits = "traits",
            activeGoals = "active goals",
            importantConstraints = "important constraints",
            evidenceJson = "{}"
        )

        val body = AutobiographicalMemoryConsolidator.eventBody(draft)

        assertTrue(body.contains("Autobiografia local consolidada"))
        assertTrue(body.contains("self summary"))
        assertTrue(body.contains("active goals"))
        assertTrue(body.contains("important constraints"))
    }

    private fun event(
        eventHash: String,
        eventType: String = "conversation.user_message",
        memoryKind: String,
        tagsJson: String = "[]",
        body: String,
        importance: Int,
        confidence: Int = 90,
        userConfirmed: Boolean = false,
        createdAtMillis: Long = 1000L
    ): MemoryEventEntity {
        return MemoryEventEntity(
            genesisCoreId = "primary_genesis",
            genesisCoreHash = "sha256:genesis",
            previousEventHash = null,
            eventHash = eventHash,
            hashAlgorithm = MemoryIntegrityCore.HASH_ALGORITHM_SHA256,
            canonicalization = MemoryIntegrityCore.MEMORY_EVENT_CANONICALIZATION_V3,
            signatureAlgorithm = MemoryIntegrityCore.MEMORY_EVENT_SIGNATURE_ALGORITHM_UNSIGNED,
            eventSignature = null,
            eventType = eventType,
            actor = "system",
            source = "test",
            contextTag = "test",
            privacyVisibility = "private_local",
            memoryKind = memoryKind,
            tagsJson = tagsJson,
            evidenceJson = "{}",
            confidence = confidence,
            userConfirmed = userConfirmed,
            body = body,
            importance = importance,
            createdAtMillis = createdAtMillis
        )
    }
}
