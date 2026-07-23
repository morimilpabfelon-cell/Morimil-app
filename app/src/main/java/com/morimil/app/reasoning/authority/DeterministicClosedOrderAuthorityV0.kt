package com.morimil.app.reasoning.authority

import java.text.Normalizer
import java.util.Locale

/**
 * Exact authority for one bounded Spanish order grammar.
 *
 * Supported form:
 *
 * `Ana llegó antes que Bruno y Bruno antes que Carla. ¿Quién llegó primero?`
 *
 * The relation graph must have one unique topological order. Cycles, ties, repeated
 * edges, extra prose, malformed entities and unsupported questions abstain.
 */
internal object DeterministicClosedOrderAuthorityV0 {
    private const val MAX_PROMPT_CHARS = 512
    private const val MIN_RELATIONS = 2
    private const val MAX_RELATIONS = 8
    private const val ENTITY = "[a-z][a-z0-9_-]{0,31}"

    private val promptPattern = Regex(
        """^(.+?)\.\s*quien\s+llego\s+(primero|ultimo)\s*\??$"""
    )
    private val firstRelationPattern = Regex(
        """^($ENTITY)\s+llego\s+antes\s+que\s+($ENTITY)$"""
    )
    private val continuationRelationPattern = Regex(
        """^($ENTITY)\s+antes\s+que\s+($ENTITY)$"""
    )
    private val relationSeparator = Regex("""\s+y\s+""")
    private val whitespace = Regex("""\s+""")

    fun solve(prompt: String): DeterministicAuthorityResult {
        if (prompt.length > MAX_PROMPT_CHARS) {
            return DeterministicAuthorityResult.unsupported(
                "deterministic_closed_order_prompt_too_long"
            )
        }

        val normalized = normalize(prompt)
        val promptMatch = promptPattern.matchEntire(normalized)
            ?: return DeterministicAuthorityResult.unsupported(
                "deterministic_closed_order_prompt_unsupported"
            )
        val query = promptMatch.groupValues[2]
        val relationClauses = promptMatch.groupValues[1]
            .split(relationSeparator)
            .map(String::trim)

        if (relationClauses.size !in MIN_RELATIONS..MAX_RELATIONS) {
            return DeterministicAuthorityResult.unsupported(
                "deterministic_closed_order_relation_count_unsupported"
            )
        }

        val nodes = linkedSetOf<String>()
        val edges = linkedMapOf<String, MutableSet<String>>()

        for ((index, clause) in relationClauses.withIndex()) {
            val match = if (index == 0) {
                firstRelationPattern.matchEntire(clause)
            } else {
                continuationRelationPattern.matchEntire(clause)
            } ?: return DeterministicAuthorityResult.unsupported(
                "deterministic_closed_order_relation_malformed"
            )

            val before = match.groupValues[1]
            val after = match.groupValues[2]
            if (before == after) {
                return DeterministicAuthorityResult.unsupported(
                    "deterministic_closed_order_self_relation"
                )
            }

            nodes += before
            nodes += after
            val targets = edges.getOrPut(before) { linkedSetOf() }
            edges.getOrPut(after) { linkedSetOf() }
            if (!targets.add(after)) {
                return DeterministicAuthorityResult.unsupported(
                    "deterministic_closed_order_duplicate_relation"
                )
            }
        }

        val order = uniqueTopologicalOrder(nodes, edges)
            ?: return DeterministicAuthorityResult.unsupported(
                "deterministic_closed_order_not_unique"
            )
        val answer = when (query) {
            "primero" -> order.first()
            "ultimo" -> order.last()
            else -> return DeterministicAuthorityResult.unsupported(
                "deterministic_closed_order_question_unsupported"
            )
        }

        return DeterministicAuthorityResult.acceptedToken(
            value = answer,
            reason = "deterministic_closed_order_unique_topology",
            trace = "order=${order.joinToString(">")};query=$query"
        )
    }

    private fun uniqueTopologicalOrder(
        nodes: Set<String>,
        edges: Map<String, Set<String>>
    ): List<String>? {
        val indegree = nodes.associateWith { 0 }.toMutableMap()
        edges.values.forEach { targets ->
            targets.forEach { target ->
                indegree[target] = requireNotNull(indegree[target]) + 1
            }
        }

        val remaining = nodes.toMutableSet()
        val ordered = ArrayList<String>(nodes.size)
        while (remaining.isNotEmpty()) {
            val available = remaining.filter { node -> indegree[node] == 0 }
            if (available.size != 1) return null

            val current = available.single()
            remaining.remove(current)
            ordered += current
            edges[current].orEmpty().forEach { target ->
                indegree[target] = requireNotNull(indegree[target]) - 1
            }
        }
        return ordered.takeIf { it.size == nodes.size }
    }

    private fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(value.trim(), Normalizer.Form.NFKD)
        return decomposed
            .filterNot { character ->
                Character.getType(character) == Character.NON_SPACING_MARK.toInt()
            }
            .lowercase(Locale.ROOT)
            .replace('¿', ' ')
            .replace('¡', ' ')
            .replace(whitespace, " ")
            .trim()
    }
}
