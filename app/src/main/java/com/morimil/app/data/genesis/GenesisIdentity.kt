package com.morimil.app.data.genesis

/**
 * Mirrors identity/orchestrator.identity.json from the Morimil Genesis repo
 * field-for-field. This is fetched from GitHub (or a bundled fallback), never
 * invented locally. It answers "who is the agent, what is it allowed to do."
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
 * A GenesisIdentity plus where it actually came from this run. The UI shows
 * this so the user always knows whether they are looking at a live GitHub
 * read or a bundled fallback (e.g. no network on first install).
 */
data class GenesisIdentitySource(
    val identity: GenesisIdentity,
    val origin: GenesisOrigin
)

enum class GenesisOrigin(val label: String) {
    GITHUB_LIVE("github_live"),
    BUNDLED_FALLBACK("bundled_fallback")
}
