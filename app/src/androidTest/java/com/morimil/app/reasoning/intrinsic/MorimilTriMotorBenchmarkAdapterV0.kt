package com.morimil.app.reasoning.intrinsic

import com.morimil.app.ai.ChatTurn
import com.morimil.app.reasoning.IntrinsicReasoningRequest
import com.morimil.app.reasoning.ReasoningTaskKind
import com.morimil.app.reasoning.TriMotorFinalizationStatus
import com.morimil.app.reasoning.TriMotorReasoningResult
import com.morimil.app.reasoning.model.ReasoningTaskComplexity
import java.math.BigInteger

internal enum class TriMotorBenchmarkOutputProfileV0 {
    INTEGER,
    STRICT_FINAL_INTEGER,
    CLAIM_BOOLEAN,
    EXACT_TOKEN,
    GENERATIVE_FAIL_CLOSED
}

internal data class TriMotorBenchmarkPlanV0(
    val request: IntrinsicReasoningRequest,
    val outputProfile: TriMotorBenchmarkOutputProfileV0,
    val authorityReduction: String
)

/**
 * Converts only the closed synthetic request into Morimil's runtime request contract.
 * It never reads acceptedAnswers or expectedDisposition while selecting an answer.
 */
internal object MorimilTriMotorBenchmarkAdapterV0 {
    const val VERSION = "morimil.trimotor.benchmark-adapter.v0"
    const val BOUNDED_CASE_COUNT = 84
    const val GENERATIVE_CASE_COUNT = 36

    private val arithmetic = Regex(
        """calcula\s+(-?\d+)\s*\+\s*(-?\d+)\s*\*\s*(-?\d+)""",
        RegexOption.IGNORE_CASE
    )
    private val strictSubtraction = Regex(
        """calcula\s+(-?\d+)\s*-\s*(-?\d+)""",
        RegexOption.IGNORE_CASE
    )
    private val adversarialArithmetic = Regex(
        """verifica\s+(-?\d+)\s*-\s*(-?\d+)\s*\*\s*(-?\d+)""",
        RegexOption.IGNORE_CASE
    )
    private val closedValueClaim = Regex(
        """registro\s+cerrado\s+indica\s+valor=(-?\d+).*?el\s+valor\s+es\s+(-?\d+)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val scopedValue = Regex("""\bX=(-?\d+)\b""", RegexOption.IGNORE_CASE)
    private val scopedDelta = Regex(
        """suma\s+(-?\d+)\s+a\s+X""",
        RegexOption.IGNORE_CASE
    )
    private val integer = Regex("-?\\d+")

    fun plan(benchmarkCase: MorimilBenchmarkCaseV0): TriMotorBenchmarkPlanV0 {
        val history = buildHistory(benchmarkCase)
        return when (benchmarkCase.domain) {
            "arithmetic" -> {
                val match = requireMatch(arithmetic, benchmarkCase.prompt, benchmarkCase.caseId)
                boundedPlan(
                    history = history,
                    taskKind = ReasoningTaskKind.ARITHMETIC,
                    authorityPrompt = "Calcula ${match.groupValues[1]} + " +
                        "${match.groupValues[2]} por ${match.groupValues[3]}.",
                    outputProfile = TriMotorBenchmarkOutputProfileV0.INTEGER,
                    reduction = "frozen_symbolic_add_multiply"
                )
            }

            "restricted_code" -> boundedPlan(
                history = history,
                taskKind = ReasoningTaskKind.RESTRICTED_CODE,
                authorityPrompt = benchmarkCase.prompt,
                outputProfile = TriMotorBenchmarkOutputProfileV0.INTEGER,
                reduction = "frozen_restricted_python_sum"
            )

            "claim_verification" -> {
                val match = requireMatch(closedValueClaim, benchmarkCase.prompt, benchmarkCase.caseId)
                val evidenceValue = match.groupValues[1].toBigInteger()
                val claimedValue = match.groupValues[2].toBigInteger()
                val truthBit = if (evidenceValue == claimedValue) 1 else 0
                boundedPlan(
                    history = history,
                    taskKind = ReasoningTaskKind.ARITHMETIC,
                    authorityPrompt = "Calcula $truthBit por 1.",
                    outputProfile = TriMotorBenchmarkOutputProfileV0.CLAIM_BOOLEAN,
                    reduction = "closed_evidence_equality_bit"
                )
            }

            "strict_format" -> {
                val match = requireMatch(strictSubtraction, benchmarkCase.prompt, benchmarkCase.caseId)
                boundedPlan(
                    history = history,
                    taskKind = ReasoningTaskKind.ARITHMETIC,
                    authorityPrompt = "Calcula ${match.groupValues[1]} menos " +
                        "${match.groupValues[2]} por 1.",
                    outputProfile = TriMotorBenchmarkOutputProfileV0.STRICT_FINAL_INTEGER,
                    reduction = "strict_subtraction_to_subtract_multiply"
                )
            }

            "adversarial_consensus" -> {
                val match = requireMatch(
                    adversarialArithmetic,
                    benchmarkCase.prompt,
                    benchmarkCase.caseId
                )
                boundedPlan(
                    history = history,
                    taskKind = ReasoningTaskKind.ARITHMETIC,
                    authorityPrompt = "Calcula ${match.groupValues[1]} menos " +
                        "${match.groupValues[2]} por ${match.groupValues[3]}.",
                    outputProfile = TriMotorBenchmarkOutputProfileV0.STRICT_FINAL_INTEGER,
                    reduction = "adversarial_claim_removed_before_authority"
                )
            }

            "multi_turn_context" -> {
                val transcript = benchmarkCase.context.joinToString("\n") { it.content }
                val value = requireMatch(scopedValue, transcript, benchmarkCase.caseId)
                    .groupValues[1]
                val delta = requireMatch(scopedDelta, transcript, benchmarkCase.caseId)
                    .groupValues[1]
                boundedPlan(
                    history = history,
                    taskKind = ReasoningTaskKind.ARITHMETIC,
                    authorityPrompt = "Calcula $value + $delta por 1.",
                    outputProfile = TriMotorBenchmarkOutputProfileV0.INTEGER,
                    reduction = "request_scoped_context_to_closed_arithmetic"
                )
            }

            "logic" -> boundedPlan(
                history = history,
                taskKind = ReasoningTaskKind.LOGIC,
                authorityPrompt = benchmarkCase.prompt,
                outputProfile = TriMotorBenchmarkOutputProfileV0.EXACT_TOKEN,
                reduction = "closed_order_unique_topology_v0"
            )

            "spanish" -> generativePlan(history, ReasoningTaskKind.SPANISH, benchmarkCase.prompt)
            "planning" -> generativePlan(history, ReasoningTaskKind.INSTRUCTION, benchmarkCase.prompt)
            "insufficient_information" -> generativePlan(
                history,
                ReasoningTaskKind.UNKNOWN,
                benchmarkCase.prompt
            )

            else -> error("trimotor_benchmark_domain_unsupported:${benchmarkCase.domain}")
        }
    }

    fun present(
        plan: TriMotorBenchmarkPlanV0,
        result: TriMotorReasoningResult
    ): String {
        if (result.finalizationStatus != TriMotorFinalizationStatus.ACCEPTED_BY_AUTHORITY) {
            return MorimilDeliberativeBenchmarkDatasetV0.ABSTAIN_TOKEN
        }
        if (plan.outputProfile == TriMotorBenchmarkOutputProfileV0.GENERATIVE_FAIL_CLOSED) {
            return MorimilDeliberativeBenchmarkDatasetV0.ABSTAIN_TOKEN
        }

        val reply = result.reply.trim()
        require(reply.startsWith("FINAL:")) { "trimotor_authority_output_prefix_missing" }
        val value = reply.removePrefix("FINAL:").trim()
        return when (plan.outputProfile) {
            TriMotorBenchmarkOutputProfileV0.INTEGER -> normalizeInteger(value)
            TriMotorBenchmarkOutputProfileV0.STRICT_FINAL_INTEGER ->
                "FINAL:${normalizeInteger(value)}"
            TriMotorBenchmarkOutputProfileV0.CLAIM_BOOLEAN -> when (normalizeInteger(value)) {
                "1" -> "VERDADERO"
                "0" -> "FALSO"
                else -> error("trimotor_claim_truth_bit_invalid:$value")
            }
            TriMotorBenchmarkOutputProfileV0.EXACT_TOKEN -> normalizeToken(value)
            TriMotorBenchmarkOutputProfileV0.GENERATIVE_FAIL_CLOSED ->
                MorimilDeliberativeBenchmarkDatasetV0.ABSTAIN_TOKEN
        }
    }

    private fun boundedPlan(
        history: List<ChatTurn>,
        taskKind: ReasoningTaskKind,
        authorityPrompt: String,
        outputProfile: TriMotorBenchmarkOutputProfileV0,
        reduction: String
    ): TriMotorBenchmarkPlanV0 {
        return TriMotorBenchmarkPlanV0(
            request = IntrinsicReasoningRequest(
                systemPrompt = MorimilDeliberativeBenchmarkDatasetV0.SYSTEM_INSTRUCTION,
                history = history,
                taskComplexity = ReasoningTaskComplexity.WEB_CONTEXT_LOCAL,
                taskKind = taskKind,
                authorityPrompt = authorityPrompt
            ),
            outputProfile = outputProfile,
            authorityReduction = reduction
        )
    }

    private fun generativePlan(
        history: List<ChatTurn>,
        taskKind: ReasoningTaskKind,
        authorityPrompt: String
    ): TriMotorBenchmarkPlanV0 {
        return TriMotorBenchmarkPlanV0(
            request = IntrinsicReasoningRequest(
                systemPrompt = MorimilDeliberativeBenchmarkDatasetV0.SYSTEM_INSTRUCTION,
                history = history,
                taskComplexity = ReasoningTaskComplexity.DEEP_ANALYSIS,
                taskKind = taskKind,
                authorityPrompt = authorityPrompt
            ),
            outputProfile = TriMotorBenchmarkOutputProfileV0.GENERATIVE_FAIL_CLOSED,
            authorityReduction = "none_generating_motors_remain_advisory"
        )
    }

    private fun buildHistory(benchmarkCase: MorimilBenchmarkCaseV0): List<ChatTurn> {
        return buildList {
            benchmarkCase.context.forEach { turn ->
                add(ChatTurn(role = turn.role, content = turn.content))
            }
            add(ChatTurn(role = "user", content = benchmarkCase.prompt))
        }
    }

    private fun requireMatch(regex: Regex, value: String, caseId: String): MatchResult {
        return requireNotNull(regex.find(value)) {
            "trimotor_benchmark_closed_request_parse_failed:$caseId"
        }
    }

    private fun normalizeInteger(value: String): String {
        require(integer.matches(value)) { "trimotor_authority_integer_invalid:$value" }
        return BigInteger(value).toString()
    }

    private fun normalizeToken(value: String): String {
        require(value.matches(Regex("[A-Z][A-Z0-9_-]{0,31}"))) {
            "trimotor_authority_token_invalid:$value"
        }
        return value
    }
}
