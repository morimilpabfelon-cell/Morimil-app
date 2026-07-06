package com.morimil.app.core.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRelevanceScorerTest {
    @Test
    fun projectQueryPrioritizesMatchingProjectDecisionOverNoise() {
        val ranked = MemoryRelevanceScorer.rank(
            query = "Que decisiones tenemos para la boveda IonPay y sus agentes?",
            candidates = listOf(
                candidate(
                    hash = "sha256:ionpay",
                    kind = "decision",
                    body = "IonPay vive en una boveda. Sus agentes temporales trabajan por tareas y no escriben memoria principal."
                ),
                candidate(
                    hash = "sha256:hello",
                    kind = "chat_noise",
                    body = "hola dale listo",
                    importance = 8,
                    confidence = 40
                ),
                candidate(
                    hash = "sha256:other",
                    kind = "decision",
                    body = "High Tide usa un estilo visual de estudio de animacion."
                )
            ),
            limit = 3
        )

        assertEquals("sha256:ionpay", ranked.first().candidate.eventHash)
        assertTrue(ranked.none { item -> item.candidate.eventHash == "sha256:hello" })
    }

    @Test
    fun correctionQueriesPrioritizeCorrections() {
        val ranked = MemoryRelevanceScorer.rank(
            query = "corrige donde te equivocaste con la memoria",
            candidates = listOf(
                candidate(
                    hash = "sha256:correction",
                    kind = "correction",
                    body = "El usuario corrigio: no asumir que GitHub esta actualizado sin revisar archivos reales."
                ),
                candidate(
                    hash = "sha256:conversation",
                    kind = "conversation",
                    body = "Hablamos de memoria viva y app local."
                )
            ),
            limit = 2
        )

        assertEquals("sha256:correction", ranked.first().candidate.eventHash)
        assertTrue(ranked.first().reasons.any { reason -> reason.contains("correction") })
    }

    @Test
    fun preferenceQueriesPrioritizeUserPreferences() {
        val ranked = MemoryRelevanceScorer.rank(
            query = "cual es mi preferencia para trabajar en github?",
            candidates = listOf(
                candidate(
                    hash = "sha256:preference",
                    kind = "preference",
                    body = "El usuario prefiere trabajar primero en GitHub, luego actualizar local, con tests verdes antes de main."
                ),
                candidate(
                    hash = "sha256:generic",
                    kind = "conversation",
                    body = "Se hablo de GitHub y repositorios."
                )
            ),
            limit = 2
        )

        assertEquals("sha256:preference", ranked.first().candidate.eventHash)
    }

    @Test
    fun decisionQueriesPrioritizeStableRules() {
        val ranked = MemoryRelevanceScorer.rank(
            query = "recuerda la regla operativa siempre",
            candidates = listOf(
                candidate(
                    hash = "sha256:rule",
                    kind = "decision",
                    body = "Regla operativa: revisar GitHub real, cambios pequenos, tests verdes, build verde, commit, push, main."
                ),
                candidate(
                    hash = "sha256:chat",
                    kind = "conversation",
                    body = "Comentamos que salio verde."
                )
            ),
            limit = 2
        )

        assertEquals("sha256:rule", ranked.first().candidate.eventHash)
    }

    private fun candidate(
        hash: String,
        kind: String,
        body: String,
        importance: Int = 90,
        confidence: Int = 90,
        userConfirmed: Boolean = true,
        createdAtMillis: Long = 1_000L
    ): MemoryRelevanceCandidate {
        return MemoryRelevanceCandidate(
            eventHash = hash,
            memoryKind = kind,
            eventType = "test.$kind",
            actor = "user",
            source = "test",
            privacyVisibility = "private_local",
            importance = importance,
            confidence = confidence,
            userConfirmed = userConfirmed,
            tagsJson = "[\"$kind\"]",
            evidenceJson = "{}",
            body = body,
            createdAtMillis = createdAtMillis
        )
    }
}
