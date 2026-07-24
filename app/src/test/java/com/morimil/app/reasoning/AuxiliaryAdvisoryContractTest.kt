package com.morimil.app.reasoning

import com.morimil.app.data.local.ReasoningTurnAuthor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuxiliaryAdvisoryContractTest {
    @Test
    fun resultCannotMixMorimilReplyWithAuxiliaryAdvisory() {
        val result = runCatching {
            ReasoningKernelResult(
                state = state(),
                morimilReply = "respuesta de Morimil",
                auxiliaryAdvisory = advisory(),
                errorMessage = null
            )
        }

        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message.orEmpty()
                .contains("cannot_mix_morimil_reply_and_auxiliary_advisory")
        )
    }

    @Test
    fun advisoryCannotPopulateFinalReply() {
        val result = runCatching {
            ReasoningKernelResult(
                state = state().copy(finalReply = "salida externa"),
                morimilReply = null,
                auxiliaryAdvisory = advisory(),
                errorMessage = null
            )
        }

        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message.orEmpty()
                .contains("auxiliary_advisory_cannot_populate_final_reply")
        )
    }

    @Test
    fun advisoryAuthorIsExcludedFromTrustedConversation() {
        assertFalse(
            ReasoningTurnAuthor.isTrustedConversationAuthor(
                ReasoningTurnAuthor.AUXILIARY_ADVISORY
            )
        )
        assertTrue(ReasoningTurnAuthor.isTrustedConversationAuthor(ReasoningTurnAuthor.USER))
        assertTrue(ReasoningTurnAuthor.isTrustedConversationAuthor(ReasoningTurnAuthor.MORIMIL))
    }

    private fun advisory(): AuxiliaryAdvisory {
        return AuxiliaryAdvisory(
            content = "salida externa no verificada",
            providerLabel = "auxiliar:REMOTE_API",
            disclosurePolicyVersion = "test-disclosure-v1"
        )
    }

    private fun state(): ReasoningState {
        return ReasoningState(
            input = "tarea",
            mode = ReasoningMode.LOCAL_ONLY,
            intent = ReasoningIntent.GENERAL,
            modelBackendLabel = "test",
            executionOrigin = ReasoningExecutionOrigin.TEMPORARY_EXTERNAL,
            memoryContextSummary = "empty",
            capsuleContextSummary = "empty",
            policyDecision = "test",
            criticFindings = emptyList(),
            trace = emptyList()
        )
    }
}
