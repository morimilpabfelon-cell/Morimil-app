package com.morimil.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.morimil.app.MorimilAppContainer
import com.morimil.app.data.local.AgentProfileEntity
import com.morimil.app.data.local.AutobiographicalSnapshotEntity
import com.morimil.app.data.local.DecisionLogEntity
import com.morimil.app.data.local.DelegatedTaskEntity
import com.morimil.app.data.local.GenesisCoreEntity
import com.morimil.app.data.local.KnowledgeCapsuleEntity
import com.morimil.app.data.local.LocalInstanceIdentityEntity
import com.morimil.app.data.local.MemoryEventEntity
import com.morimil.app.data.local.MemoryLinkEntity
import com.morimil.app.data.local.MemorySnapshotEntity
import com.morimil.app.data.local.MigrationRecordEntity
import com.morimil.app.data.local.OrchestratorDeviceEntity
import com.morimil.app.data.local.ProjectStateEntity
import com.morimil.app.data.local.ProjectVaultEntity
import com.morimil.app.data.local.ReasoningTurnEntity
import com.morimil.app.data.local.RecallScheduleEntity
import com.morimil.app.data.local.UserWorkspaceEntity
import com.morimil.app.data.repository.LocalBirthState
import com.morimil.app.data.repository.RestCycleRepository
import com.morimil.app.runtime.RestCycleScheduleStatus
import com.morimil.app.runtime.RestCycleScheduler
import com.morimil.app.security.MemorySigningRuntimeIssues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MorimilViewModel(application: Application) : AndroidViewModel(application) {
    private val container = MorimilAppContainer.from(application)
    private val memoryDatabase = container.memoryDatabase
    private val repository = container.memoryRepository
    private val reasoningTranscriptRepository = container.reasoningTranscriptRepository
    private val memoryOrganRepository = container.memoryOrganRepository
    private val migrationRecordRepository = container.migrationRecordRepository
    private val recallScheduleRepository = container.recallScheduleRepository
    private val runRestCycleUseCase = container.runRestCycleUseCase
    private val proposeCognitiveMigrationUseCase = container.proposeCognitiveMigrationUseCase
    private val projectVaultRepository = container.projectVaultRepository
    private val agentOrchestrationRepository = container.agentOrchestrationRepository

    private val memoryGraphCoordinator by lazy {
        MorimilMemoryGraphCoordinator(
            container = container,
            scope = viewModelScope,
            observeTask = { component, block ->
                runObservedInternalTask(component) { block() }
            }
        )
    }

    private val chatCoordinator by lazy {
        MorimilChatCoordinator(
            application = application,
            container = container,
            scope = viewModelScope,
            localIdentity = localIdentity,
            messages = messages,
            observeTask = { component, block ->
                runObservedInternalTask(component) { block() }
            }
        )
    }

    val chatViewModel: ChatViewModel by lazy { ChatViewModel(this) }
    val memoryViewModel: MemoryViewModel by lazy { MemoryViewModel(this) }
    val healthViewModel: HealthViewModel by lazy { HealthViewModel(this) }
    val motorViewModel: MotorViewModel by lazy { MotorViewModel(this) }
    val pcHandoffViewModel: PcHandoffViewModel by lazy { PcHandoffViewModel(this) }

    val messages: StateFlow<List<ReasoningTurnEntity>> = reasoningTranscriptRepository.turns.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val decisions: StateFlow<List<DecisionLogEntity>> = repository.decisions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val projects: StateFlow<List<ProjectStateEntity>> = repository.projects.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val activeWorkspace: StateFlow<UserWorkspaceEntity?> = repository.activeWorkspace.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

    val localIdentity: StateFlow<LocalInstanceIdentityEntity?> = repository.localIdentity.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

    val genesisCore: StateFlow<GenesisCoreEntity?> = repository.genesisCore.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

    val recentMemoryEvents: StateFlow<List<MemoryEventEntity>> = repository.recentMemoryEvents.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val livingMemorySnapshot: StateFlow<MemorySnapshotEntity?> = repository.livingMemorySnapshot.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

    val selfSnapshot: StateFlow<AutobiographicalSnapshotEntity?> = memoryOrganRepository.selfSnapshot.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

    val knowledgeCapsules: StateFlow<List<KnowledgeCapsuleEntity>> = memoryOrganRepository.knowledgeCapsules.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val activeRecallSchedules: StateFlow<List<RecallScheduleEntity>> = recallScheduleRepository.activeRecallSchedules.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val recentMemoryLinks: StateFlow<List<MemoryLinkEntity>> = container.memoryLinkRepository.recentMemoryLinks.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val selectedMemoryEventHash: StateFlow<String?>
        get() = memoryGraphCoordinator.selectedMemoryEventHash

    val selectedMemoryLinks: StateFlow<List<MemoryLinkEntity>>
        get() = memoryGraphCoordinator.selectedMemoryLinks

    val selectedGraphEvents: StateFlow<List<MemoryEventEntity>>
        get() = memoryGraphCoordinator.selectedGraphEvents

    private val _memoryIntegrityAudit = MutableStateFlow(MemoryIntegrityAuditUiState())
    val memoryIntegrityAudit: StateFlow<MemoryIntegrityAuditUiState> = _memoryIntegrityAudit.asStateFlow()

    private val _chatOrganismStatus = MutableStateFlow(ChatOrganismStatusUiState())
    val chatOrganismStatus: StateFlow<ChatOrganismStatusUiState> = _chatOrganismStatus.asStateFlow()

    private val _organismHealth = MutableStateFlow(OrganismHealthUiState())
    val organismHealth: StateFlow<OrganismHealthUiState> = _organismHealth.asStateFlow()

    private val _internalRuntimeIssue = MutableStateFlow<InternalRuntimeIssueUiState?>(null)
    val internalRuntimeIssue: StateFlow<InternalRuntimeIssueUiState?> = _internalRuntimeIssue.asStateFlow()

    private val _restCycleScheduleStatus = MutableStateFlow(RestCycleScheduleStatus())
    val restCycleScheduleStatus: StateFlow<RestCycleScheduleStatus> = _restCycleScheduleStatus.asStateFlow()

    val recentMigrationRecords: StateFlow<List<MigrationRecordEntity>> = migrationRecordRepository.recentMigrationRecords.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val projectVaults: StateFlow<List<ProjectVaultEntity>> = projectVaultRepository.projectVaults.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val orchestratorDevices: StateFlow<List<OrchestratorDeviceEntity>> = agentOrchestrationRepository.orchestratorDevices.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val agentProfiles: StateFlow<List<AgentProfileEntity>> = agentOrchestrationRepository.agentProfiles.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val delegatedTasks: StateFlow<List<DelegatedTaskEntity>> = agentOrchestrationRepository.delegatedTasks.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val genesisResult get() = chatCoordinator.genesisResult
    val isSending get() = chatCoordinator.isSending
    val chatError get() = chatCoordinator.chatError

    init {
        viewModelScope.launch(Dispatchers.IO) {
            MemorySigningRuntimeIssues.latestIssue.collect { issue ->
                if (issue == null) {
                    clearInternalRuntimeIssue(MemorySigningRuntimeIssues.KEYSTORE_FAILURE_COMPONENT)
                } else {
                    recordInternalRuntimeIssue(
                        component = issue.component,
                        message = issue.message,
                        failureCount = issue.failureCount,
                        occurredAtMillis = issue.occurredAtMillis
                    )
                }
                runCatching { refreshOrganismHealthOnWorker() }
                    .onFailure { error -> recordInternalRuntimeIssue("organism_health.refresh", error) }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            when (repository.readLocalBirthState()) {
                LocalBirthState.ABSENT -> Unit
                LocalBirthState.COMPLETE -> {
                    RestCycleScheduler.schedule(application)
                    refreshRestCycleScheduleStatusOnWorker(application)
                    repository.seedInitialStateIfNeeded()
                    reasoningTranscriptRepository.seedIntroTurnsIfNeeded()
                    agentOrchestrationRepository.seedDefaultOrchestrationIfNeeded()
                    runObservedInternalTask("rest_cycle.startup") { runRestCycleUseCase() }
                    runObservedInternalTask("recall.startup") { recallScheduleRepository.seedFromRecentMemoryIfNeeded() }
                }
                LocalBirthState.INCONSISTENT -> {
                    recordInternalRuntimeIssue(
                        component = "birth_state",
                        message = "Local birth state is incomplete; durable runtime startup is blocked.",
                        failureCount = 1,
                        occurredAtMillis = System.currentTimeMillis()
                    )
                }
            }
        }
        viewModelScope.launch {
            recentMemoryEvents.collect {
                refreshChatOrganismStatus()
                refreshOrganismHealth()
            }
        }
        refreshGenesis()
        refreshChatOrganismStatus()
        refreshOrganismHealth()
    }

    fun refreshGenesis() = chatCoordinator.refreshGenesis()

    fun approveMemoryEvent(event: MemoryEventEntity) = memoryGraphCoordinator.approveMemoryEvent(event)

    fun degradeMemoryEvent(event: MemoryEventEntity) = memoryGraphCoordinator.degradeMemoryEvent(event)

    fun requestMemoryCorrection(event: MemoryEventEntity) = memoryGraphCoordinator.requestMemoryCorrection(event)

    fun selectMemoryEvent(eventHash: String) = memoryGraphCoordinator.selectMemoryEvent(eventHash)

    fun clearSelectedMemoryEvent() = memoryGraphCoordinator.clearSelectedMemoryEvent()

    fun runMemoryIntegrityAudit() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val memoryVerified = repository.auditLivingMemoryChain()
                val capsulesVerified = memoryOrganRepository.auditKnowledgeCapsuleChain()
                MemoryIntegrityAuditUiState(
                    memoryChainVerified = memoryVerified,
                    capsuleChainVerified = capsulesVerified,
                    checkedAtMillis = System.currentTimeMillis(),
                    errorMessage = null
                )
            }.getOrElse { error ->
                MemoryIntegrityAuditUiState(
                    memoryChainVerified = null,
                    capsuleChainVerified = null,
                    checkedAtMillis = System.currentTimeMillis(),
                    errorMessage = error.message ?: error::class.java.simpleName
                )
            }
            _memoryIntegrityAudit.value = result
            refreshChatOrganismStatus()
            refreshOrganismHealthOnWorker()
        }
    }

    fun refreshChatOrganismStatus() {
        val activeSlot = container.reasoningConfigStore.loadActiveSlot()
        val modelLabel = activeSlot.config.model.ifBlank { "modelo pendiente" }
        val hasQuarantine = recentMemoryEvents.value.any { event ->
            event.eventType == MEMORY_INTEGRITY_QUARANTINE_EVENT_TYPE ||
                event.memoryKind == "integrity_quarantine"
        }
        val audit = _memoryIntegrityAudit.value
        val memoryLabel = when {
            hasQuarantine -> "memoria: cuarentena"
            audit.errorMessage != null -> "memoria: error de auditoria"
            audit.memoryChainVerified == true && audit.capsuleChainVerified == true -> "memoria: verificada"
            audit.memoryChainVerified == false || audit.capsuleChainVerified == false -> "memoria: revisar"
            else -> "memoria: sin auditar"
        }

        _chatOrganismStatus.value = ChatOrganismStatusUiState(
            motorLabel = activeSlot.displayName,
            modelLabel = modelLabel,
            memoryIntegrityLabel = memoryLabel,
            memoryNeedsAttention = hasQuarantine ||
                audit.errorMessage != null ||
                audit.memoryChainVerified == false ||
                audit.capsuleChainVerified == false
        )
    }

    fun refreshOrganismHealth() {
        viewModelScope.launch(Dispatchers.IO) {
            refreshOrganismHealthOnWorker()
        }
    }

    private suspend fun refreshOrganismHealthOnWorker() {
        val activeSlot = container.reasoningConfigStore.loadActiveSlot()
        val audit = _memoryIntegrityAudit.value
        val hasQuarantine = recentMemoryEvents.value.any { event ->
            event.eventType == MEMORY_INTEGRITY_QUARANTINE_EVENT_TYPE ||
                event.memoryKind == "integrity_quarantine"
        }
        val memoryDao = memoryDatabase.memoryDao()
        val now = System.currentTimeMillis()
        val recalls = activeRecallSchedules.value
        val latestCompletedRestCycle = migrationRecordRepository.loadLatestCompletedMigration(
            RestCycleRepository.REST_CYCLE_MIGRATION_TYPE
        )
        _organismHealth.value = OrganismHealthUiStateBuilder.build(
            activeSlot = activeSlot,
            audit = audit,
            restCycleAudit = latestCompletedRestCycle?.toRestCycleAuditSignal(),
            hasQuarantine = hasQuarantine,
            internalIssue = _internalRuntimeIssue.value,
            eventCount = memoryDao.countMemoryEvents(),
            recallPendingCount = recalls.size,
            recallOverdueCount = recalls.count { recall -> recall.dueAtMillis <= now },
            restCycleScheduleStatus = _restCycleScheduleStatus.value,
            latestRestCycleAtMillis = latestCompletedRestCycle?.updatedAtMillis
                ?: memoryDao.loadLatestRestCycleEvent()?.createdAtMillis,
            nowMillis = now
        )
    }

    private fun MigrationRecordEntity.toRestCycleAuditSignal(): RestCycleAuditSignal {
        return RestCycleAuditSignal(
            memoryChainVerified = chainVerified,
            capsuleChainVerified = expectedEffect
                .lines()
                .firstOrNull { line -> line.startsWith("organ_reconciliation_capsule_chain_verified=") }
                ?.substringAfter("=")
                ?.toBooleanStrictOrNull(),
            checkedAtMillis = updatedAtMillis
        )
    }

    fun seedRecallScheduleIfNeeded() {
        viewModelScope.launch(Dispatchers.IO) {
            runObservedInternalTask("recall.seed") {
                recallScheduleRepository.seedFromRecentMemoryIfNeeded()
            }
        }
    }

    fun reinforceRecall(recallId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            runObservedInternalTask("recall.reinforce") {
                recallScheduleRepository.reinforceRecall(recallId)
            }
        }
    }

    fun postponeRecall(recallId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            runObservedInternalTask("recall.postpone") {
                recallScheduleRepository.postponeRecall(recallId)
            }
        }
    }

    fun degradeRecall(recallId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            runObservedInternalTask("recall.degrade") {
                recallScheduleRepository.degradeRecall(recallId)
            }
        }
    }

    fun runRestCycleNow() {
        viewModelScope.launch(Dispatchers.IO) {
            runObservedInternalTask("rest_cycle.manual") {
                runRestCycleUseCase(force = true)
            }
            refreshOrganismHealthOnWorker()
        }
    }

    fun enableRestCycleSchedule() {
        val application = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            runObservedInternalTask("rest_cycle.schedule.enable") {
                RestCycleScheduler.ensureScheduled(application)
                refreshRestCycleScheduleStatusOnWorker(application)
            }.onFailure { error ->
                _restCycleScheduleStatus.value = RestCycleScheduleStatus(errorMessage = error.message)
            }
            refreshOrganismHealthOnWorker()
        }
    }

    fun cancelRestCycleSchedule() {
        val application = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            runObservedInternalTask("rest_cycle.schedule.cancel") {
                RestCycleScheduler.cancel(application)
                refreshRestCycleScheduleStatusOnWorker(application)
            }.onFailure { error ->
                _restCycleScheduleStatus.value = RestCycleScheduleStatus(errorMessage = error.message)
            }
            refreshOrganismHealthOnWorker()
        }
    }

    fun refreshRestCycleScheduleStatus() {
        val application = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            runObservedInternalTask("rest_cycle.schedule.refresh") {
                refreshRestCycleScheduleStatusOnWorker(application)
            }.onFailure { error ->
                _restCycleScheduleStatus.value = RestCycleScheduleStatus(errorMessage = error.message)
            }
            refreshOrganismHealthOnWorker()
        }
    }

    fun approveRestCycleConsolidation(migrationId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runObservedInternalTask("rest_cycle.approve") {
                runRestCycleUseCase.approveAndRun(migrationId)
            }
        }
    }

    private suspend fun refreshRestCycleScheduleStatusOnWorker(application: Application) {
        _restCycleScheduleStatus.value = RestCycleScheduler.readStatus(application)
    }

    private suspend fun <T> runObservedInternalTask(
        component: String,
        block: suspend () -> T
    ): Result<T> {
        val result = runCatching { block() }
        result
            .onSuccess { clearInternalRuntimeIssue(component) }
            .onFailure { error -> recordInternalRuntimeIssue(component, error) }
        runCatching { refreshOrganismHealthOnWorker() }
            .onFailure { error -> recordInternalRuntimeIssue("organism_health.refresh", error) }
        return result
    }

    private fun recordInternalRuntimeIssue(component: String, error: Throwable) {
        val previous = _internalRuntimeIssue.value
        recordInternalRuntimeIssue(
            component = component,
            message = error.message?.take(180) ?: error::class.java.simpleName,
            failureCount = if (previous?.component == component) previous.failureCount + 1 else 1,
            occurredAtMillis = System.currentTimeMillis()
        )
    }

    private fun recordInternalRuntimeIssue(
        component: String,
        message: String,
        failureCount: Int,
        occurredAtMillis: Long
    ) {
        _internalRuntimeIssue.value = InternalRuntimeIssueUiState(
            component = component,
            message = message.take(180),
            failureCount = failureCount,
            occurredAtMillis = occurredAtMillis
        )
    }

    private fun clearInternalRuntimeIssue(component: String) {
        if (_internalRuntimeIssue.value?.component == component) {
            _internalRuntimeIssue.value = null
        }
    }

    fun proposeCognitiveMigration() {
        viewModelScope.launch(Dispatchers.IO) {
            runObservedInternalTask("migration.propose") {
                proposeCognitiveMigrationUseCase()
            }
        }
    }

    fun approveCognitiveMigration(migrationId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runObservedInternalTask("migration.approve") {
                proposeCognitiveMigrationUseCase.approve(migrationId)
            }
        }
    }

    fun executeCognitiveMigration(migrationId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runObservedInternalTask("migration.execute") {
                proposeCognitiveMigrationUseCase.execute(migrationId)
            }
        }
    }

    fun rollbackCognitiveMigration(migrationId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runObservedInternalTask("migration.rollback") {
                proposeCognitiveMigrationUseCase.rollback(migrationId)
            }
        }
    }

    fun createProjectVault(displayName: String, mission: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runObservedInternalTask("project_vault.create") {
                projectVaultRepository.createProjectVaultFromIntent(
                    displayName = displayName,
                    mission = mission,
                    sourceContext = "founder_instruction"
                )
            }
        }
    }

    fun completeProjectVault(vaultId: String, finalSummary: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runObservedInternalTask("project_vault.complete") {
                projectVaultRepository.completeProjectVault(vaultId, finalSummary)
            }
        }
    }

    fun archiveProjectVault(vaultId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runObservedInternalTask("project_vault.archive") {
                projectVaultRepository.archiveProjectVault(vaultId, "archived_by_founder")
            }
        }
    }

    fun seedAgentOrchestration() {
        viewModelScope.launch(Dispatchers.IO) {
            runObservedInternalTask("agent_orchestration.seed") {
                agentOrchestrationRepository.seedDefaultOrchestrationIfNeeded()
            }
        }
    }

    fun proposeDelegatedTask(goal: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runObservedInternalTask("agent_orchestration.propose_task") {
                agentOrchestrationRepository.proposeDelegatedTask(goal)
            }
        }
    }

    fun approveDelegatedTask(taskId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runObservedInternalTask("agent_orchestration.approve_task") {
                agentOrchestrationRepository.approveDelegatedTask(taskId)
            }
        }
    }

    fun rejectDelegatedTask(taskId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runObservedInternalTask("agent_orchestration.reject_task") {
                agentOrchestrationRepository.rejectDelegatedTask(taskId, "rejected_by_founder")
            }
        }
    }

    suspend fun bornInstance(alias: String): Result<Unit> = chatCoordinator.bornInstance(alias)

    fun sendMessage(body: String) {
        refreshChatOrganismStatus()
        refreshOrganismHealth()
        chatCoordinator.sendMessage(body)
    }

    suspend fun renameWorkspace(displayName: String): List<String> {
        return repository.renameWorkspace(displayName)
    }

    private companion object {
        const val MEMORY_INTEGRITY_QUARANTINE_EVENT_TYPE = "memory_integrity.quarantine"
    }
}
