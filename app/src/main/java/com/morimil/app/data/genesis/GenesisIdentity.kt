package com.morimil.app.data.genesis

/**
 * Mirrors identity/orchestrator.identity.json from the bundled Genesis seed.
 * It answers "who is the agent, what is it allowed to do."
 */
data class GenesisIdentity(
    val schemaVersion: String,
    val agentId: String,
    val alias: String,
    val role: String,
    val owner: String,
    val riskTier: String,
    val allowedActions: List<String>,
    val disallowedActions: List<String>,
    val doctrineRef: String,
    val policyRef: String
)

/**
 * A GenesisIdentity plus where it came from this run.
 */
data class GenesisIdentitySource(
    val identity: GenesisIdentity,
    val origin: GenesisOrigin,
    val manifest: GenesisManifestVerification
)

enum class GenesisOrigin(val label: String) {
    BUNDLED_SEED("bundled_seed")
}
