package com.morimil.app.core.memory

import org.json.JSONArray
import java.text.Normalizer
import java.util.Locale

data class MemoryRelevanceCandidate(
    val eventHash: String,
    val memoryKind: String,
    val eventType: String,
    val actor: String,
    val source: String,
    val privacyVisibility: String,
    val importance: Int,
    val confidence: Int,
    val userConfirmed: Boolean,
    val tagsJson: String,
    val evidenceJson: String,
    val body: String,
    val createdAtMillis: Long
)

data class RankedMemoryCandidate(
    val candidate: MemoryRelevanceCandidate,
    val score: Int,
    val reasons: List<String>
)

data class MemoryQueryIntent(
    val normalizedQuery: String,
    val terms: Set<String>,
    val requestedKinds: Set<String>,
    val projectTerms: Set<String>,
    val correctionSeeking: Boolean,
    val decisionSeeking: Boolean,
    val preferenceSeeking: Boolean
)

object MemoryRelevanceScorer {
    private val stopwords = setOf(
        "a", "al", "algo", "como", "con", "cuando", "de", "del", "el", "en", "es", "eso",
        "esta", "este", "esto", "la", "lo", "los", "las", "me", "mi", "no", "o", "para", "por",
        "que", "se", "si", "un", "una", "y", "yo", "the", "and", "for", "with", "what", "when"
    )

    private val projectMarkers = setOf(
        "ionpay", "ion", "exchange", "morimil", "abits", "137", "hightide", "high", "tide", "boveda", "vault", "proyecto"
    )

    fun buildIntent(query: String): MemoryQueryIntent {
        val normalized = normalize(query)
        val terms = normalized
            .split(Regex("[^a-z0-9]+"))
            .map { it.trim() }
            .filter { token -> token.length >= 3 && token !in stopwords }
            .toSet()
        val correctionSeeking = containsAny(normalized, listOf("corrige", "correccion", "equivo", "error", "mal", "dudoso"))
        val decisionSeeking = containsAny(normalized, listOf("decision", "regla", "siempre", "nunca", "operativa", "constitucion"))
        val preferenceSeeking = containsAny(normalized, listOf("prefiero", "preferencia", "gusta", "quiero", "estilo"))
        val requestedKinds = buildSet {
            if (correctionSeeking) {
                add("correction")
                add("error_detected")
            }
            if (decisionSeeking) add("decision")
            if (preferenceSeeking) add("preference")
            if (containsAny(normalized, listOf("hecho", "fuente", "documento", "conocimiento"))) add("learning")
            if (containsAny(normalized, listOf("agente", "tarea", "boveda", "proyecto"))) add("decision")
        }
        return MemoryQueryIntent(
            normalizedQuery = normalized,
            terms = terms,
            requestedKinds = requestedKinds,
            projectTerms = terms.filter { term -> term in projectMarkers || term.length >= 5 }.toSet(),
            correctionSeeking = correctionSeeking,
            decisionSeeking = decisionSeeking,
            preferenceSeeking = preferenceSeeking
        )
    }

    fun rank(query: String, candidates: List<MemoryRelevanceCandidate>, limit: Int): List<RankedMemoryCandidate> {
        val intent = buildIntent(query)
        return candidates
            .map { candidate -> rankCandidate(intent, candidate) }
            .filter { ranked -> ranked.score > 0 }
            .sortedWith(
                compareByDescending<RankedMemoryCandidate> { it.score }
                    .thenByDescending { it.candidate.userConfirmed }
                    .thenByDescending { it.candidate.importance }
                    .thenByDescending { it.candidate.confidence }
                    .thenByDescending { it.candidate.createdAtMillis }
            )
            .take(limit)
    }

    private fun rankCandidate(intent: MemoryQueryIntent, candidate: MemoryRelevanceCandidate): RankedMemoryCandidate {
        val text = normalize(
            listOf(candidate.memoryKind, candidate.eventType, candidate.tagsJson, candidate.evidenceJson, candidate.body)
                .joinToString(" ")
        )
        val tags = parseTags(candidate.tagsJson)
        val reasons = mutableListOf<String>()
        var score = 0

        val termMatches = intent.terms.count { term -> text.contains(term) }
        if (termMatches > 0) {
            score += termMatches * 18
            reasons += "query_term_match:$termMatches"
        }

        val projectMatches = intent.projectTerms.count { term -> text.contains(term) }
        if (projectMatches > 0) {
            score += projectMatches * 20
            reasons += "project_context_match:$projectMatches"
        }

        if (candidate.memoryKind in intent.requestedKinds) {
            score += 34
            reasons += "requested_memory_kind:${candidate.memoryKind}"
        }
        if (tags.any { tag -> tag in intent.requestedKinds }) {
            score += 12
            reasons += "requested_tag_match"
        }

        score += kindBaseWeight(candidate.memoryKind)
        if (candidate.userConfirmed) {
            score += 18
            reasons += "user_confirmed"
        }
        score += (candidate.importance / 10).coerceIn(0, 10)
        score += (candidate.confidence / 20).coerceIn(0, 5)

        if (candidate.memoryKind == "chat_noise" && !candidate.userConfirmed) {
            score -= 70
            reasons += "noise_penalty"
        }
        if (candidate.memoryKind == "conversation" && termMatches == 0 && !candidate.userConfirmed) {
            score -= 18
            reasons += "generic_conversation_penalty"
        }
        if (intent.correctionSeeking && candidate.memoryKind == "correction") {
            score += 28
            reasons += "correction_query_boost"
        }
        if (intent.decisionSeeking && candidate.memoryKind == "decision") {
            score += 24
            reasons += "decision_query_boost"
        }
        if (intent.preferenceSeeking && candidate.memoryKind == "preference") {
            score += 24
            reasons += "preference_query_boost"
        }

        return RankedMemoryCandidate(
            candidate = candidate,
            score = score,
            reasons = reasons.distinct()
        )
    }

    private fun kindBaseWeight(memoryKind: String): Int {
        return when (memoryKind) {
            "decision" -> 30
            "correction" -> 28
            "preference" -> 26
            "error_detected" -> 24
            "approval", "rejection" -> 22
            "learning", "identity" -> 18
            "rest_cycle" -> 16
            "conversation" -> 4
            "chat_noise" -> -30
            else -> 10
        }
    }

    private fun parseTags(tagsJson: String): Set<String> {
        return runCatching {
            val array = JSONArray(tagsJson)
            (0 until array.length()).mapNotNull { index -> array.optString(index).takeIf { it.isNotBlank() } }.toSet()
        }.getOrDefault(emptySet())
    }

    private fun containsAny(value: String, terms: List<String>): Boolean {
        return terms.any { term -> value.contains(normalize(term)) }
    }

    private fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)
    }
}
