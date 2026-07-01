package com.morimil.app.data.genesis

data class GenesisBlock(
    val schemaVersion: String,
    val sourceRepo: String,
    val sourceMode: String,
    val agentId: String,
    val alias: String,
    val role: String,
    val owner: String,
    val riskTier: String,
    val allowedActions: List<String>,
    val blockedActions: List<String>,
    val localMemory: Boolean,
    val voicePushToTalk: Boolean,
    val genesisReader: Boolean,
    val githubSync: Boolean,
    val pcExecution: Boolean,
    val productionRelease: Boolean,
    val currentAppPhase: String
)
