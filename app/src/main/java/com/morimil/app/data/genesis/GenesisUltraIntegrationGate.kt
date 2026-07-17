package com.morimil.app.data.genesis

import com.morimil.app.data.genesis.ultra.GenesisUltraBirthCandidate
import com.morimil.app.data.genesis.ultra.GenesisUltraBirthCandidateAssessment
import com.morimil.app.data.genesis.ultra.GenesisUltraBirthCandidateValidator

/**
 * Prevents the legacy Morimil Genesis bundle from creating new identities.
 *
 * The Genesis Ultra contract verifier can now assess a candidate, but birth
 * remains fail-closed until guardian epochs, body possession and transactional
 * commit are implemented and verified.
 */
object GenesisUltraIntegrationGate {
    const val BLOCK_CODE = "genesis_ultra_birth_adapter_not_ready"

    const val isBirthReady: Boolean = false

    const val statusMessage: String =
        "Nacimiento deshabilitado: el contrato firmado de Genesis Ultra puede validarse, " +
            "pero faltan la epoca confiable del guardian, la prueba de posesion del cuerpo " +
            "y el commit transaccional de nacimiento."

    fun assess(candidate: GenesisUltraBirthCandidate): GenesisUltraBirthCandidateAssessment {
        return GenesisUltraBirthCandidateValidator.assess(candidate)
    }

    fun requireBirthReady(candidate: GenesisUltraBirthCandidate) {
        val assessment = assess(candidate)
        check(assessment.birthReady) {
            "$BLOCK_CODE: issues=${assessment.issues}; blockers=${assessment.remainingBlockers}"
        }
    }

    fun requireBirthReady() {
        check(isBirthReady) { "$BLOCK_CODE: $statusMessage" }
    }
}
