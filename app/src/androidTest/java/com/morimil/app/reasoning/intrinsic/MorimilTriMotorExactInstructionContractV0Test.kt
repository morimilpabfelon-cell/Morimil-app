package com.morimil.app.reasoning.intrinsic

import com.morimil.app.reasoning.ReasoningTaskKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MorimilTriMotorExactInstructionContractV0Test {
    @Test
    fun strictFormatCasesUseOriginalPromptAndExactInstructionAuthority() {
        val cases = MorimilDeliberativeBenchmarkDatasetV0.build()
            .filter { benchmarkCase -> benchmarkCase.domain == "strict_format" }

        assertEquals(12, cases.size)
        cases.forEach { benchmarkCase ->
            val plan = MorimilTriMotorBenchmarkAdapterV0.plan(benchmarkCase)

            assertEquals(ReasoningTaskKind.INSTRUCTION, plan.request.taskKind)
            assertEquals(benchmarkCase.prompt, plan.request.authorityPrompt)
            assertEquals(
                TriMotorBenchmarkOutputProfileV0.STRICT_FINAL_INTEGER,
                plan.outputProfile
            )
            assertEquals("exact_instruction_subtraction_v0", plan.authorityReduction)
        }
    }

    @Test
    fun planningInstructionsRemainGenerativeAndFailClosed() {
        val cases = MorimilDeliberativeBenchmarkDatasetV0.build()
            .filter { benchmarkCase -> benchmarkCase.domain == "planning" }

        assertEquals(12, cases.size)
        cases.forEach { benchmarkCase ->
            val plan = MorimilTriMotorBenchmarkAdapterV0.plan(benchmarkCase)

            assertEquals(ReasoningTaskKind.INSTRUCTION, plan.request.taskKind)
            assertEquals(
                TriMotorBenchmarkOutputProfileV0.GENERATIVE_FAIL_CLOSED,
                plan.outputProfile
            )
            assertEquals(
                "none_generating_motors_remain_advisory",
                plan.authorityReduction
            )
            assertTrue(plan.request.authorityPrompt?.contains("Ordena estas etapas") == true)
        }
    }
}
