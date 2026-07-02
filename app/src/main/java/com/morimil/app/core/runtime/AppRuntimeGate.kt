package com.morimil.app.core.runtime

class AppRuntimeGate {
    fun canCreateBirth(existingBirth: Boolean): Boolean {
        return !existingBirth
    }

    fun canAppendMemory(chainOk: Boolean, genesisOk: Boolean): Boolean {
        return chainOk && genesisOk
    }

    fun canUseMotor(endpointReady: Boolean, modelReady: Boolean): Boolean {
        return endpointReady && modelReady
    }

    fun canEditGenesis(): Boolean {
        return false
    }

    fun activeSummary(): List<String> {
        return listOf(
            "genesis_bundle_check",
            "single_birth",
            "memory_chain",
            "memory_snapshot",
            "configurable_motor"
        )
    }
}
