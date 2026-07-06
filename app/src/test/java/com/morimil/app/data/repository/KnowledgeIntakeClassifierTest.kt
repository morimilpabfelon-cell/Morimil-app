package com.morimil.app.data.repository

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeIntakeClassifierTest {
    @Test
    fun accentedCapsuleIntentIsRecognized() {
        assertTrue(
            KnowledgeIntakeClassifier.hasExplicitCapsuleIntent(
                "Guarda esto como cápsula: Morimil debe conservar reglas estables con fuente clara."
            )
        )
    }

    @Test
    fun accentedDocumentAndDesignWordsAreCategorized() {
        val text = "documentación estable de diseño: Morimil debe guardar reglas visuales con procedencia."

        assertEquals("technical_docs", KnowledgeIntakeClassifier.inferCategory(text))
        assertTrue("design" in KnowledgeIntakeClassifier.inferTags(text, "technical_docs"))
    }

    @Test
    fun evidenceKeepsStrongProvenanceFields() {
        val evidence = JSONObject(
            KnowledgeIntakeClassifier.buildEvidenceJson(
                source = "user_approved_notes",
                sourceEventHash = "sha256:source-event",
                summary = """
                    source_type: repo_file
                    file: docs/ARCHITECTURE.md
                    scope: morimil_app
                    Morimil debe usar cápsulas con fuente, hash y aprobación humana cuando sean reglas estables.
                """.trimIndent()
            )
        )

        assertEquals("morimil.knowledge_source.v1", evidence.getString("schema"))
        assertEquals("repo_file", evidence.getString("source_type"))
        assertEquals("docs/ARCHITECTURE.md", evidence.getString("source_ref"))
        assertEquals("morimil_app", evidence.getString("scope"))
        assertTrue(evidence.getString("document_hash").startsWith("sha256:"))
        assertEquals("user_approved", evidence.getString("approval_state"))
        assertFalse(evidence.getBoolean("requires_human_review"))
    }

    @Test
    fun architectureAndSecurityRulesRequireHumanReview() {
        val evidence = JSONObject(
            KnowledgeIntakeClassifier.buildEvidenceJson(
                source = "user_approved_notes",
                sourceEventHash = "sha256:source-event",
                summary = "Regla estable: la arquitectura de seguridad siempre debe requerir aprobación humana."
            )
        )

        assertTrue(evidence.getBoolean("requires_human_review"))
        assertEquals("global", evidence.getString("scope"))
    }
}
