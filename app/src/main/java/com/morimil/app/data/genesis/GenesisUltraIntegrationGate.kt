package com.morimil.app.data.genesis

/**
 * Prevents the legacy Morimil Genesis bundle from creating new identities.
 *
 * This gate remains closed until Morimil verifies and installs a signed
 * Genesis Ultra release bundle and implements the protocol birth transaction.
 */
object GenesisUltraIntegrationGate {
    const val BLOCK_CODE = "genesis_ultra_birth_adapter_not_ready"

    const val isBirthReady: Boolean = false

    const val statusMessage: String =
        "Nacimiento deshabilitado: el Genesis legado de Morimil no puede crear nuevas instancias. " +
            "Falta integrar y verificar el release firmado de Genesis Ultra."

    fun requireBirthReady() {
        check(isBirthReady) { "$BLOCK_CODE: $statusMessage" }
    }
}
