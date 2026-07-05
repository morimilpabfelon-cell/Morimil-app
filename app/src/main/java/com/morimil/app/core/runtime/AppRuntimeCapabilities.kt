package com.morimil.app.core.runtime

data class AppRuntimeCapabilities(
    val genesisBundleVerification: Boolean = true,
    val genesisPrivateInstallation: Boolean = true,
    val singleLocalBirth: Boolean = true,
    val hashLinkedMemoryEvents: Boolean = true,
    val memoryHashV1Compatibility: Boolean = true,
    val memoryHashV2MetadataBinding: Boolean = true,
    val livingMemorySnapshot: Boolean = true,
    val configurableMotor: Boolean = true,
    val localMotorEndpoint: Boolean = true,
    val encryptedRuntimeStorage: Boolean = true,
    val memoryOrganDatabase: Boolean = true,
    val autobiographicalSnapshotEntity: Boolean = true,
    val knowledgeCapsuleEntity: Boolean = true,
    val recallSchedule: Boolean = true,
    val restCycle: Boolean = true,
    val migrationRecord: Boolean = true,
    val memoryLink: Boolean = true,
    val agentOrchestration: Boolean = true,
    val multiDeviceAuthorization: Boolean = true,
    val pcHandoffProtocol: Boolean = false,
    val pcCommandExecution: Boolean = false,
    val interactionState: Boolean = false
)

object CurrentRuntimeCapabilities {
    val value = AppRuntimeCapabilities()
}
