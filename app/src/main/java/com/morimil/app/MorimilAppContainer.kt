package com.morimil.app

import android.content.Context
import com.morimil.app.ai.ReasoningClient
import com.morimil.app.ai.ReasoningConfigStore
import com.morimil.app.core.memory.MemoryIntegrityCore
import com.morimil.app.data.genesis.GenesisReader
import com.morimil.app.data.local.MemoryOrganDatabase
import com.morimil.app.data.local.MorimilDatabase
import com.morimil.app.data.repository.AgentInstanceLifecycleRepository
import com.morimil.app.data.repository.AgentOrchestrationRepository
import com.morimil.app.data.repository.CognitiveMigrationRepository
import com.morimil.app.data.repository.MemoryLinkRepository
import com.morimil.app.data.repository.MemoryOrganRepository
import com.morimil.app.data.repository.MemoryRepository
import com.morimil.app.data.repository.MigrationRecordRepository
import com.morimil.app.data.repository.ProjectVaultRepository
import com.morimil.app.data.repository.RecallScheduleRepository
import com.morimil.app.data.repository.ReasoningTranscriptRepository
import com.morimil.app.data.repository.RestCycleRepository
import com.morimil.app.domain.usecase.AppendLivingMemoryUseCase
import com.morimil.app.domain.usecase.ProposeCognitiveMigrationUseCase
import com.morimil.app.domain.usecase.RunRestCycleUseCase
import com.morimil.app.reasoning.IntrinsicTriMotorCoordinator
import com.morimil.app.reasoning.ReasoningClientTemporaryExternalProvider
import com.morimil.app.reasoning.ReasoningKernel
import com.morimil.app.reasoning.RepositoryReasoningContextReader
import com.morimil.app.security.AndroidKeyStoreMemoryEventSigner
import com.morimil.app.security.SecretVault
import com.morimil.app.security.SharedPreferencesMemorySignatureEpochPolicy

class MorimilAppContainer(context: Context) {
    private val appContext = context.applicationContext

    val memoryDatabase: MorimilDatabase by lazy {
        MorimilDatabase.getInstance(appContext)
    }

    val organDatabase: MemoryOrganDatabase by lazy {
        MemoryOrganDatabase.getInstance(appContext)
    }

    val memorySignatureEpochPolicy: SharedPreferencesMemorySignatureEpochPolicy by lazy {
        SharedPreferencesMemorySignatureEpochPolicy(appContext)
    }

    val memoryEventSigner: AndroidKeyStoreMemoryEventSigner by lazy {
        AndroidKeyStoreMemoryEventSigner(
            signatureEpochRecorder = memorySignatureEpochPolicy
        )
    }

    val memoryIntegrityCore: MemoryIntegrityCore by lazy {
        MemoryIntegrityCore(
            signatureVerifier = memoryEventSigner,
            signatureEpochPolicy = memorySignatureEpochPolicy
        )
    }

    val memoryRepository: MemoryRepository by lazy {
        MemoryRepository(
            database = memoryDatabase,
            memoryIntegrityCore = memoryIntegrityCore,
            memoryEventSigner = memoryEventSigner
        )
    }

    val reasoningTranscriptRepository: ReasoningTranscriptRepository by lazy {
        ReasoningTranscriptRepository(memoryDatabase)
    }

    val restCycleRepository: RestCycleRepository by lazy {
        RestCycleRepository(
            database = memoryDatabase,
            organDatabase = organDatabase,
            memoryIntegrityCore = memoryIntegrityCore,
            memoryEventSigner = memoryEventSigner,
            memoryRepository = memoryRepository
        )
    }

    val memoryOrganRepository: MemoryOrganRepository by lazy {
        MemoryOrganRepository(
            database = organDatabase,
            memoryIntegrityCore = memoryIntegrityCore
        )
    }

    val memoryLinkRepository: MemoryLinkRepository by lazy {
        MemoryLinkRepository(organDatabase)
    }

    val migrationRecordRepository: MigrationRecordRepository by lazy {
        MigrationRecordRepository(organDatabase)
    }

    val cognitiveMigrationRepository: CognitiveMigrationRepository by lazy {
        CognitiveMigrationRepository(
            organDatabase = organDatabase,
            memoryDatabase = memoryDatabase,
            memoryRepository = memoryRepository
        )
    }

    val recallScheduleRepository: RecallScheduleRepository by lazy {
        RecallScheduleRepository(
            organDatabase = organDatabase,
            memoryDatabase = memoryDatabase
        )
    }

    val projectVaultRepository: ProjectVaultRepository by lazy {
        ProjectVaultRepository(
            organDatabase = organDatabase,
            memoryRepository = memoryRepository
        )
    }

    val agentOrchestrationRepository: AgentOrchestrationRepository by lazy {
        AgentOrchestrationRepository(
            organDatabase = organDatabase,
            memoryRepository = memoryRepository
        )
    }

    val agentInstanceLifecycleRepository: AgentInstanceLifecycleRepository by lazy {
        AgentInstanceLifecycleRepository(
            organDatabase = organDatabase,
            memoryRepository = memoryRepository
        )
    }

    val appendLivingMemoryUseCase: AppendLivingMemoryUseCase by lazy {
        AppendLivingMemoryUseCase(memoryRepository)
    }

    val runRestCycleUseCase: RunRestCycleUseCase by lazy {
        RunRestCycleUseCase(restCycleRepository)
    }

    val proposeCognitiveMigrationUseCase: ProposeCognitiveMigrationUseCase by lazy {
        ProposeCognitiveMigrationUseCase(cognitiveMigrationRepository)
    }

    val genesisReader: GenesisReader by lazy {
        GenesisReader(appContext)
    }

    val secretVault: SecretVault by lazy {
        SecretVault(appContext)
    }

    val reasoningConfigStore: ReasoningConfigStore by lazy {
        ReasoningConfigStore(appContext)
    }

    val reasoningClient: ReasoningClient by lazy {
        ReasoningClient()
    }

    val reasoningKernel: ReasoningKernel by lazy {
        ReasoningKernel(
            contextReader = RepositoryReasoningContextReader(
                memoryRepository = memoryRepository,
                memoryOrganRepository = memoryOrganRepository
            ),
            intrinsicCoordinator = IntrinsicTriMotorCoordinator(),
            temporaryExternalProvider = ReasoningClientTemporaryExternalProvider(reasoningClient)
        )
    }

    companion object {
        fun from(context: Context): MorimilAppContainer {
            val applicationContext = context.applicationContext
            return requireNotNull((applicationContext as? MorimilApplication)?.container) {
                "MorimilApplication is not registered in AndroidManifest.xml."
            }
        }
    }
}
