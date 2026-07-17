package com.morimil.app.data.genesis

import com.morimil.app.data.genesis.ultra.GenesisUltraBirthCandidate
import com.morimil.app.data.genesis.ultra.GenesisUltraBirthCandidateAssessment
import com.morimil.app.data.genesis.ultra.GenesisUltraBirthCandidateValidator

/**
 * Prevents the legacy Morimil Genesis bundle from creating new identities.
 *
 * Signed Genesis Ultra releases, guardian key epochs and body possession can
 * now be verified. Birth remains fail-closed until the protocol defines and
 * Morimil implements a crash-recoverable transactional birth commit.
 */
object GenesisUltraIntegrationGate {
    const val BLOCK_CODE = "genesis_ultra_birth_adapter_not_ready"

    const val isBirthReady: Boolean = false

    const val statusMessage: String =
        "Nacimiento deshabilitado: release, epoca del guardian y posesion del cuerpo " +
            "pueden validarse, pero falta el commit transaccional recuperable de nacimiento."

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
