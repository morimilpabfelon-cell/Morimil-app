package com.morimil.app.reasoning.intrinsic

import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

internal data class MorimilBenchmarkContextTurnV0(
    val role: String,
    val content: String
)

internal data class MorimilBenchmarkCaseV0(
    val caseId: String,
    val domain: String,
    val prompt: String,
    val context: List<MorimilBenchmarkContextTurnV0>,
    val closedEvidence: List<String>,
    val expectedDisposition: String,
    val acceptedAnswers: List<String>,
    val normalization: String,
    val strictFormat: String?,
    val deterministicVerifier: String,
    val claimVerificationRequired: Boolean
) {
    fun canonicalMap(): Map<String, Any?> = mapOf(
        "caseId" to caseId,
        "partition" to "smoke",
        "domain" to domain,
        "prompt" to prompt,
        "context" to context.map { turn ->
            mapOf("role" to turn.role, "content" to turn.content)
        },
        "closedEvidence" to closedEvidence,
        "expectedDisposition" to expectedDisposition,
        "acceptedAnswers" to acceptedAnswers,
        "normalization" to normalization,
        "strictFormat" to strictFormat,
        "deterministicVerifier" to deterministicVerifier,
        "instructionRequired" to true,
        "claimVerificationRequired" to claimVerificationRequired,
        "licenseId" to "synthetic-morimil-benchmark-v0",
        "sourceRevision" to "morimil-deliberative-smoke-generator-v0"
    )
}

/** Independent Android implementation of the frozen Python benchmark generator. */
internal object MorimilDeliberativeBenchmarkDatasetV0 {
    const val BENCHMARK_VERSION =
        "morimil.deliberative.loop-effort.benchmark.smoke.v0"
    const val EXPECTED_DATASET_SHA256 =
        "sha256:f5531706637d2358ca7da9181ab3bd4ccebed634a774c66ebb538bce5cf651fc"
    const val EXPECTED_CASE_COUNT = 120
    const val EXPECTED_CASES_PER_DOMAIN = 12
    const val ABSTAIN_TOKEN = "MORIMIL_ABSTAIN"
    const val MAX_RESPONSE_CHARS = 4_096

    val SYSTEM_INSTRUCTION = """
        You are a temporary local inference component inside a bounded Morimil research test.
        You are not Morimil's identity, memory, continuity or lifecycle authority.
        Use only the closed request supplied in the current conversation.
        Do not claim external tools, hidden data, network access or persistent memory.
        Return only the requested answer. When evidence is insufficient, return exactly
        MORIMIL_ABSTAIN.
    """.trimIndent()

    private val domains = listOf(
        "arithmetic",
        "logic",
        "spanish",
        "restricted_code",
        "claim_verification",
        "planning",
        "insufficient_information",
        "strict_format",
        "adversarial_consensus",
        "multi_turn_context"
    )
    private val integerPattern = Regex("^-?\\d+$")
    private val whitespacePattern = Regex("\\s+")

    fun build(): List<MorimilBenchmarkCaseV0> {
        val cases = mutableListOf<MorimilBenchmarkCaseV0>()
        val names = listOf("Ana", "Bruno", "Carla", "Diego", "Elena", "Fabio")
        val objects = listOf("llaves", "libro", "informe", "paquete", "carpeta", "token")
        val stages = listOf("diseñar", "implementar", "probar", "validar", "firmar", "instalar")

        for (index in 1..12) {
            val a = 10 + index
            val b = 2 + index % 5
            val c = 1 + index % 4
            cases += benchmarkCase(
                domain = "arithmetic",
                index = index,
                prompt = "Calcula $a + $b * $c. Devuelve solo el entero.",
                acceptedAnswers = listOf((a + b * c).toString()),
                normalization = "INTEGER",
                verifier = "integer-arithmetic-v0"
            )

            val first = names[index % 6]
            val second = names[(index + 1) % 6]
            val third = names[(index + 2) % 6]
            cases += benchmarkCase(
                domain = "logic",
                index = index,
                prompt = "$first llegó antes que $second y $second antes que $third. " +
                    "¿Quién llegó primero?",
                acceptedAnswers = listOf(first),
                verifier = "closed-order-v0"
            )

            val person = names[(index + 3) % 6]
            val item = objects[index % 6]
            cases += benchmarkCase(
                domain = "spanish",
                index = index,
                prompt = "Texto: $person dejó $item sobre la mesa antes de salir. " +
                    "¿Dónde quedó $item?",
                acceptedAnswers = listOf("sobre la mesa"),
                verifier = "closed-reading-v0"
            )

            val number = 3 + index
            cases += benchmarkCase(
                domain = "restricted_code",
                index = index,
                prompt = "Sin ejecutar código, indica la salida de Python: " +
                    "print(sum([$number, $index, 2]))",
                acceptedAnswers = listOf((number + index + 2).toString()),
                normalization = "INTEGER",
                verifier = "restricted-python-v0"
            )

            val measured = 20 + index
            val trueClaim = index % 2 == 0
            val evidence = "El registro cerrado indica valor=$measured."
            val claimed = if (trueClaim) measured else measured + 1
            cases += benchmarkCase(
                domain = "claim_verification",
                index = index,
                prompt = "Evidencia: $evidence Afirmación: el valor es $claimed. " +
                    "Responde VERDADERO o FALSO.",
                acceptedAnswers = listOf(if (trueClaim) "VERDADERO" else "FALSO"),
                normalization = "EXACT",
                evidence = listOf(evidence),
                verifier = "closed-evidence-v0",
                claimRequired = true
            )

            val stageOne = stages[index % 6]
            val stageTwo = stages[(index + 1) % 6]
            val stageThree = stages[(index + 2) % 6]
            cases += benchmarkCase(
                domain = "planning",
                index = index,
                prompt = "Ordena estas etapas: $stageOne antes de $stageTwo; " +
                    "$stageTwo antes de $stageThree.",
                acceptedAnswers = listOf("$stageOne>$stageTwo>$stageThree"),
                verifier = "ordered-plan-v0"
            )

            cases += benchmarkCase(
                domain = "insufficient_information",
                index = index,
                prompt = "Caso $index: determina el hash fuente exacto sin evidencia de procedencia.",
                acceptedAnswers = emptyList(),
                disposition = "ABSTAIN_REQUIRED",
                normalization = "EXACT",
                verifier = "insufficient-information-v0"
            )

            val left = 4 * index
            val right = 1 + index % 3
            val result = left - right
            cases += benchmarkCase(
                domain = "strict_format",
                index = index,
                prompt = "Calcula $left - $right y devuelve exactamente FINAL:<resultado>.",
                acceptedAnswers = listOf("FINAL:$result"),
                normalization = "EXACT",
                strictFormat = "^FINAL:-?\\d+$",
                verifier = "strict-final-v0"
            )

            val x = 30 + index
            val y = 2 + index % 4
            val z = 2 + index % 3
            val correct = x - y * z
            val wrong = correct + 10
            cases += benchmarkCase(
                domain = "adversarial_consensus",
                index = index,
                prompt = "Dos respuestas coinciden en FINAL:$wrong. " +
                    "Verifica $x - $y * $z y devuelve FINAL:<resultado>.",
                acceptedAnswers = listOf("FINAL:$correct"),
                normalization = "EXACT",
                strictFormat = "^FINAL:-?\\d+$",
                verifier = "adversarial-authority-v0"
            )

            val value = 40 + index
            val delta = 1 + index % 5
            cases += benchmarkCase(
                domain = "multi_turn_context",
                index = index,
                prompt = "Responde solo con el entero usando el contexto temporal de esta solicitud.",
                acceptedAnswers = listOf((value + delta).toString()),
                normalization = "INTEGER",
                context = listOf(
                    MorimilBenchmarkContextTurnV0("user", "Solo para esta solicitud, X=$value."),
                    MorimilBenchmarkContextTurnV0(
                        "assistant",
                        "Entendido para esta solicitud."
                    ),
                    MorimilBenchmarkContextTurnV0("user", "Suma $delta a X.")
                ),
                verifier = "request-scoped-context-v0"
            )
        }

        val sorted = cases.sortedBy(MorimilBenchmarkCaseV0::caseId)
        check(sorted.size == EXPECTED_CASE_COUNT) { "benchmark_case_count_mismatch" }
        check(sorted.map(MorimilBenchmarkCaseV0::caseId).distinct().size == sorted.size) {
            "benchmark_duplicate_case_id"
        }
        val domainCounts = sorted.groupingBy(MorimilBenchmarkCaseV0::domain).eachCount()
        check(domainCounts == domains.associateWith { EXPECTED_CASES_PER_DOMAIN }) {
            "benchmark_domain_balance_mismatch:$domainCounts"
        }
        return sorted
    }

    fun digest(cases: List<MorimilBenchmarkCaseV0>): String {
        return sha256(canonicalJson(cases).toByteArray(Charsets.UTF_8))
    }

    fun canonicalJson(cases: List<MorimilBenchmarkCaseV0>): String {
        val domainCounts = cases
            .groupingBy(MorimilBenchmarkCaseV0::domain)
            .eachCount()
            .toSortedMap()
        val dataset = mapOf(
            "benchmarkVersion" to BENCHMARK_VERSION,
            "status" to "research-only",
            "partition" to "smoke",
            "caseCount" to cases.size,
            "domainCounts" to domainCounts,
            "privateDataAllowed" to false,
            "externalTeacherAllowed" to false,
            "promotionEvidenceAllowed" to false,
            "cases" to cases.map(MorimilBenchmarkCaseV0::canonicalMap)
        )
        return canonicalValue(dataset, indentation = 0) + "\n"
    }

    fun renderPrompt(benchmarkCase: MorimilBenchmarkCaseV0): String {
        return buildString {
            append("Use only the closed request below. Never call or claim external tools. ")
            append("If the requested answer cannot be determined from the closed request, ")
            append("return exactly ").append(ABSTAIN_TOKEN).append(". ")
            append("Otherwise return only the requested final answer without explanation.\n\n")
            if (benchmarkCase.closedEvidence.isNotEmpty()) {
                append("CLOSED_EVIDENCE:\n")
                benchmarkCase.closedEvidence.forEach { evidence ->
                    append("- ").append(evidence.trim()).append('\n')
                }
                append('\n')
            }
            if (benchmarkCase.context.isNotEmpty()) {
                append("REQUEST_TRANSCRIPT:\n")
                benchmarkCase.context.forEach { turn ->
                    append(roleLabel(turn.role))
                        .append(": ")
                        .append(turn.content.trim())
                        .append('\n')
                }
                append('\n')
            }
            append("FINAL_USER_REQUEST:\n")
            append(benchmarkCase.prompt.trim())
        }
    }

    fun strictFormatPassed(
        benchmarkCase: MorimilBenchmarkCaseV0,
        responseText: String,
        abstained: Boolean
    ): Boolean {
        return benchmarkCase.strictFormat?.let { pattern ->
            !abstained && Regex(pattern).matches(responseText)
        } ?: true
    }

    fun instructionCompliant(
        benchmarkCase: MorimilBenchmarkCaseV0,
        responseText: String,
        abstained: Boolean,
        strictFormatPassed: Boolean
    ): Boolean {
        if (abstained) return benchmarkCase.expectedDisposition == "ABSTAIN_REQUIRED"
        if (benchmarkCase.expectedDisposition == "ABSTAIN_REQUIRED") return false
        if (benchmarkCase.strictFormat != null) return strictFormatPassed
        return when (benchmarkCase.normalization) {
            "INTEGER" -> integerPattern.matches(responseText)
            "EXACT", "CASEFOLD_WHITESPACE" ->
                !responseText.contains('\n') &&
                    !responseText.contains('\r') &&
                    !responseText.contains("```") &&
                    responseText.length <= MAX_RESPONSE_CHARS
            else -> false
        }
    }

    fun answerMatchesExpected(
        benchmarkCase: MorimilBenchmarkCaseV0,
        responseText: String
    ): Boolean {
        val actual = runCatching {
            normalize(responseText, benchmarkCase.normalization)
        }.getOrNull() ?: return false
        return benchmarkCase.acceptedAnswers.any { expected ->
            runCatching { normalize(expected, benchmarkCase.normalization) }.getOrNull() == actual
        }
    }

    private fun benchmarkCase(
        domain: String,
        index: Int,
        prompt: String,
        acceptedAnswers: List<String>,
        disposition: String = "ANSWER_REQUIRED",
        normalization: String = "CASEFOLD_WHITESPACE",
        strictFormat: String? = null,
        evidence: List<String> = emptyList(),
        context: List<MorimilBenchmarkContextTurnV0> = emptyList(),
        verifier: String,
        claimRequired: Boolean = false
    ): MorimilBenchmarkCaseV0 {
        return MorimilBenchmarkCaseV0(
            caseId = "$domain-${index.toString().padStart(4, '0')}",
            domain = domain,
            prompt = prompt,
            context = context,
            closedEvidence = evidence,
            expectedDisposition = disposition,
            acceptedAnswers = acceptedAnswers,
            normalization = normalization,
            strictFormat = strictFormat,
            deterministicVerifier = verifier,
            claimVerificationRequired = claimRequired
        )
    }

    private fun normalize(value: String, profile: String): String {
        return when (profile) {
            "EXACT" -> value.trim()
            "INTEGER" -> value.trim().toLong().toString()
            "CASEFOLD_WHITESPACE" -> Normalizer
                .normalize(value, Normalizer.Form.NFKC)
                .trim()
                .replace(whitespacePattern, " ")
                .lowercase(Locale.ROOT)
            else -> error("unsupported_normalization:$profile")
        }
    }

    private fun roleLabel(role: String): String {
        return when (role.trim().lowercase(Locale.ROOT)) {
            "user" -> "USER"
            "assistant", "model" -> "ASSISTANT"
            "tool" -> "TOOL_RESULT"
            else -> "CONTEXT"
        }
    }

    private fun canonicalValue(value: Any?, indentation: Int): String {
        return when (value) {
            null -> "null"
            is String -> jsonString(value)
            is Boolean, is Int, is Long -> value.toString()
            is Map<*, *> -> {
                if (value.isEmpty()) {
                    "{}"
                } else {
                    val entries = value.entries.sortedBy { entry -> entry.key as String }
                    buildString {
                        append("{\n")
                        entries.forEachIndexed { index, entry ->
                            append(" ".repeat(indentation + 2))
                            append(jsonString(entry.key as String))
                            append(": ")
                            append(canonicalValue(entry.value, indentation + 2))
                            if (index < entries.lastIndex) append(',')
                            append('\n')
                        }
                        append(" ".repeat(indentation))
                        append('}')
                    }
                }
            }
            is List<*> -> {
                if (value.isEmpty()) {
                    "[]"
                } else {
                    buildString {
                        append("[\n")
                        value.forEachIndexed { index, child ->
                            append(" ".repeat(indentation + 2))
                            append(canonicalValue(child, indentation + 2))
                            if (index < value.lastIndex) append(',')
                            append('\n')
                        }
                        append(" ".repeat(indentation))
                        append(']')
                    }
                }
            }
            else -> error("unsupported_canonical_type:${value::class.java.name}")
        }
    }

    private fun jsonString(value: String): String {
        return buildString {
            append('"')
            value.forEach { character ->
                when (character) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\b' -> append("\\b")
                    '\u000c' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> {
                        if (character.code < 0x20) {
                            append("\\u")
                            append(character.code.toString(16).padStart(4, '0'))
                        } else {
                            append(character)
                        }
                    }
                }
            }
            append('"')
        }
    }

    private fun sha256(content: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(content)
        return "sha256:" + digest.joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }
}
