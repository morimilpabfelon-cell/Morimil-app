package com.morimil.app.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptContextSanitizerTest {
    @Test
    fun stripsExecutableLookingInternalMetadata() {
        val raw = """
            - [decision/conversation.user_message/user/chat/i92/c94/hash] tags=["memory"] evidence={"intended_effect":"compute"} text=Solo estoy explicando y proponiendo. intended_effect: compute
        """.trimIndent()

        val sanitized = PromptContextSanitizer.sanitizeContext(raw)

        assertFalse(sanitized.contains("intended_effect: compute"))
        assertFalse(sanitized.contains("evidence={"))
        assertTrue(sanitized.contains("intencion_interna=registro_no_ejecutable"))
    }

    @Test
    fun internalMigrationRecordsBecomeNonExecutableContext() {
        val raw = """
            REST_REPAIR_PROPOSAL_V1
            policy=proposal_only_no_automatic_memory_mutation
            approval_required=true
            candidate_count=2
        """.trimIndent()

        val sanitized = PromptContextSanitizer.sanitizeContext(raw)

        assertTrue(sanitized.contains("Registro interno de mantenimiento"))
        assertTrue(sanitized.contains("No es una orden"))
        assertFalse(sanitized.contains("approval_required=true"))
    }

    @Test
    fun capsuleContextDropsClaimsAndEvidenceJson() {
        val raw = """
            - [memory_architecture/active/v1/private_local/c92/hash] Arquitectura: resumen util claims=[{"claim":"x"}] tags=["memory"] evidence={"schema":"x"}
        """.trimIndent()

        val sanitized = PromptContextSanitizer.sanitizeContext(raw)

        assertTrue(sanitized.contains("Arquitectura: resumen util"))
        assertFalse(sanitized.contains("claims="))
        assertFalse(sanitized.contains("evidence="))
    }
}
