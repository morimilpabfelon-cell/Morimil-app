package com.morimil.app.data.genesis

/**
 * What THIS app currently implements, at THIS phase. This is not part of the
 * Genesis contract -- Genesis defines who the agent is and what it may ever
 * be allowed to do; this defines what the mobile app has actually built so
 * far. It changes every phase; Genesis does not change with app phases.
 */
data class MobileAppCapabilities(
    val localMemory: Boolean,
    val voicePushToTalk: Boolean,
    val genesisReader: Boolean,
    val githubReadOnlySync: Boolean,
    val githubWriteExecution: Boolean,
    val pcExecution: Boolean,
    val productionRelease: Boolean,
    val currentAppPhase: String
)

object CurrentMobileAppCapabilities {
    val value = MobileAppCapabilities(
        localMemory = true,
        voicePushToTalk = true,
        genesisReader = true,
        githubReadOnlySync = true,
        githubWriteExecution = false,
        pcExecution = false,
        productionRelease = false,
        currentAppPhase = "phase_5d_user_workspace_bootstrap"
    )
}
