package com.morimil.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.morimil.app.MorimilAppContainer
import com.morimil.app.ai.ChatTurn
import com.morimil.app.ai.ReasoningClient
import com.morimil.app.data.genesis.GenesisIdentitySource
import com.morimil.app.data.local.AutobiographicalSnapshotEntity
import com.morimil.app.data.local.DecisionLogEntity
import com.morimil.app.data.local.GenesisCoreEntity
import com.morimil.app.data.local.KnowledgeCapsuleEntity
import com.morimil.app.data.local.LocalInstanceIdentityEntity
import com.morimil.app.data.local.MemoryEventEntity
import com.morimil.app.data.local.MemoryLinkEntity
import com.morimil.app.data.local.MemoryMessageEntity
import com.morimil.app.data.local.MemorySnapshotEntity
import com.morimil.app.data.local.MigrationRecordEntity
import com.morimil.app.data.local.ProjectStateEntity
import com.morimil.app.data.local.ProjectVaultEntity
import com.morimil.app.data.local.RecallScheduleEntity
import com.morimil.app.data.local.UserWorkspaceEntity
import com.morimil.app.data.repository.MemoryLinkRepository
import com.morimil.app.data.repository.RestCycleRepository
import com.morimil.app.reasoning.ReasoningKernelRequest
import com.morimil.app.runtime.RestCycleScheduler
import com.morimil.app.runtime.RestCycleScheduleStatus
import com.morimil.app.security.MemorySigningRuntimeIssues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.morimil.app.data.local.AgentProfileEntity
import com.morimil.app.data.local.DelegatedTaskEntity
import com.morimil.app.data.local.OrchestratorDeviceEntity

class MorimilViewModel(application: Application) : AndroidViewModel(application) {
    private val container = MorimilAppContainer.from(application)
    private val memoryDatabase = container.memoryDatabase
    private val repository = container.memoryRepository
    private val memoryOrganRepository = container.memoryOrganRepository
    private val memoryLinkRepository = container.memoryLinkRepository
    private val migrationRecordRepository = container.migrationRecordRepository
    private val recallScheduleRepository = container.recallScheduleRepository
    private val appendLivingMemoryUseCase = container.appendLivingMemoryUseCase
    private val runRestCycleUseCase = container.runRestCycleUseCase
    private val proposeCognitiveMigrationUseCase = container.proposeCognitiveMigrationUseCase
    private val projectVaultRepository = container.projectVaultRepository
    private val agentOrchestrationRepository = container.agentOrchestrationRepository
    private val genesisReader = container.genesisReader
    private val secretVault = container.secretVault
    private val reasoningConfigStore = container.reasoningConfigStore
    private val reasoningKernel = container.reasoningKernel

    val chatViewModel: ChatViewModel by lazy { ChatViewModel(this) }
    val memoryViewModel: MemoryViewModel by lazy { MemoryViewModel(this) }
    val healthViewModel: HealthViewModel by lazy { HealthViewModel(this) }
    val motorViewModel: MotorViewModel by lazy { MotorViewModel(this) }
    val pcHandoffViewModel: PcHandoffViewModel by lazy { PcHandoffViewModel(this) }

    val messages: StateFlow<List<MemoryMessageEntity>> = repository.messages.stateIn(
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
    val recentMemoryLinks: StateFlow<List<MemoryLinkEntity>> = memoryLinkRepository.recentMemoryLinks.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private val _selectedMemoryEventHash = MutableStateFlow<String?>(null)
    val selectedMemoryEventHash: StateFlow<String?> = _selectedMemoryEventHash.asStateFlow()

    private val _selectedMemoryLinks = MutableStateFlow<List<MemoryLinkEntity>>(emptyList())
    val selectedMemoryLinks: StateFlow<List<MemoryLinkEntity>> = _selectedMemoryLinks.asStateFlow()

    private val _selectedGraphEvents = MutableStateFlow<List<MemoryEventEntity>>(emptyList())
    val selectedGraphEvents: StateFlow<List<MemoryEventEntity>> = _selectedGraphEvents.asStateFlow()

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

    private var selectedMemoryLinksJob: Job? = null

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
    private val _genesisResult = MutableStateFlow<Result<GenesisIdentitySource>?>(null)
    val genesisResult: StateFlow<Result<GenesisIdentitySource>?> = _genesisResult.asStateFlow()

    private var cachedDoctrineText: String? = null
    private var cachedPolicyText: String? = null

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _chatError = MutableStateFlow<String?>(null)
    val chatError: StateFlow<String?> = _chatError.asStateFlow()

    init {
        RestCycleScheduler.schedule(application)
        refreshRestCycleScheduleStatus()
        viewModelScope.launch(Dispatchers.IO) {
            MemorySigningRuntimeIssues.latestIssue.collect { issue ->
                if (issue == null) {
                    clearInternalRuntimeIssue(MemorySigningRuntimeIssues.KEYSTORE_FALLBACK_COMPONENT)
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
        viewModelScope.launch {
            repository.seedInitialStateIfNeeded()
            agentOrchestrationRepository.seedDefaultOrchestrationIfNeeded()
            runObservedInternalTask("rest_cycle.startup") { runRestCycleUseCase() }
            runObservedInternalTask("recall.startup") { recallScheduleRepository.seedFromRecentMemoryIfNeeded() }
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

    fun refreshGenesis() {
        viewModelScope.launch {
            val result = genesisReader.readGenesisIdentity()
            _genesisResult.value = result
            result.getOrNull()?.identity?.doctrineRef?.let { ref ->
                cachedDoctrineText = genesisReader.readDoctrineText(ref).getOrNull()
            }
            result.getOrNull()?.identity?.policyRef?.let { ref ->
                cachedPolicyText = genesisReader.readPolicyText(ref).getOrNull()
            }
        }
    }

    fun approveMemoryEvent(event: MemoryEventEntity) {
        recordMemoryReview(
            event = event,
            action = "aprobado",
            note = "Recuerdo aprobado por el usuario como memoria util."
        )
    }

    fun degradeMemoryEvent(event: MemoryEventEntity) {
        recordMemoryReview(
            event = event,
            action = "ruido_degradado",
            note = "Recuerdo marcado por el usuario como ruido o baja prioridad."
        )
    }

    fun requestMemoryCorrection(event: MemoryEventEntity) {
        recordMemoryReview(
            event = event,
            action = "correccion_requerida",
            note = "El usuario marco este recuerdo para correccion futura."
        )
    }

    fun selectMemoryEvent(eventHash: String) {
        if (_selectedMemoryEventHash.value == eventHash) return
        _selectedMemoryEventHash.value = eventHash
        selectedMemoryLinksJob?.cancel()
        selectedMemoryLinksJob = viewModelScope.launch {
            memoryLinkRepository.observeMemoryLinksForEvent(eventHash).collect { links ->
                _selectedMemoryLinks.value = links
                _selectedGraphEvents.value = loadConnectedGraphEvents(
                    selectedEventHash = eventHash,
                    links = links
                )
            }
        }
    }

    fun clearSelectedMemoryEvent() {
        _selectedMemoryEventHash.value = null
        selectedMemoryLinksJob?.cancel()
        selectedMemoryLinksJob = null
        _selectedMemoryLinks.value = emptyList()
        _selectedGraphEvents.value = emptyList()
    }

    private suspend fun loadConnectedGraphEvents(
        selectedEventHash: String,
        links: List<MemoryLinkEntity>
    ): List<MemoryEventEntity> {
        val hashes = buildList {
            add(selectedEventHash)
            links.forEach { link ->
                if (link.sourceType == MEMORY_EVENT_NODE_TYPE) add(link.sourceId)
                if (link.targetType == MEMORY_EVENT_NODE_TYPE) add(link.targetId)
            }
        }
            .filter { hash -> hash.isNotBlank() }
            .distinct()
            .take(MAX_GRAPH_EVENT_LOOKUP)

        if (hashes.isEmpty()) return emptyList()
        return withContext(Dispatchers.IO) {
            memoryDatabase.memoryDao().loadMemoryEventsByHashes(hashes)
        }
    }

    private fun recordMemoryReview(
        event: MemoryEventEntity,
        action: String,
        note: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runObservedInternalTask("memory_review.$action") {
                appendLivingMemoryUseCase.appendMemoryReview(
                    targetEvent = event,
                    action = action,
                    note = note
                )
            }
        }
    }

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
        val activeSlot = reasoningConfigStore.loadActiveSlot()
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
        val activeSlot = reasoningConfigStore.loadActiveSlot()
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
    suspend fun bornInstance(alias: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val genesisSource = _genesisResult.value?.getOrNull()
                ?: error("Genesis identity not loaded yet.")
            val genesis = genesisSource.identity
            require(!repository.hasExistingBirth()) { "This Morimil instance has already been born." }
            val installedBundle = genesisReader.installGenesisBundle().getOrThrow()
            val sourceOrigin = "${genesisSource.origin.label}:${installedBundle.installPath}"
            val genesisCoreHash = installedBundle.verification.genesisCoreHash
            require(genesisCoreHash == genesisSource.manifest.genesisCoreHash) {
                "Installed Genesis bundle does not match loaded Genesis manifest."
            }

            try {
                repository.birthLocalIdentity(alias, genesis, sourceOrigin, genesisCoreHash, cachedDoctrineText, cachedPolicyText)
                repository.seedInitialStateIfNeeded()
            agentOrchestrationRepository.seedDefaultOrchestrationIfNeeded()
                runObservedInternalTask("rest_cycle.birth") { runRestCycleUseCase() }
                runObservedInternalTask("recall.birth") { recallScheduleRepository.seedFromRecentMemoryIfNeeded() }
                Unit
            } catch (error: Exception) {
                genesisReader.clearInstalledGenesisBundle()
                throw error
            }
        }
    }

    fun sendMessage(body: String) {
        val cleanBody = body.trim()
        if (cleanBody.isEmpty() || _isSending.value) return

        viewModelScope.launch {
            _chatError.value = null

            val genesis = _genesisResult.value?.getOrNull()?.identity
            if (genesis == null) {
                _chatError.value = "Genesis no esta cargado todavia. Intenta de nuevo en un momento."
                return@launch
            }

            val runtimeSlot = reasoningConfigStore.loadActiveSlot()
            val runtimeConfig = runtimeSlot.config
            refreshChatOrganismStatus()
            refreshOrganismHealth()
            val runtimeAccess = secretVault.readReasoningKey(runtimeSlot.id).getOrNull().orEmpty()
            val alias = localIdentity.value?.alias ?: genesis.alias

            _isSending.value = true
            try {
                val priorHistory = messages.value
                    .takeLast(ReasoningClient.MAX_HISTORY_MESSAGES - 1)
                    .map { ChatTurn(role = if (it.author == "user") "user" else "assistant", content = it.body) }

                val result = withContext(Dispatchers.IO) {
                    reasoningKernel.reason(
                        ReasoningKernelRequest(
                            input = cleanBody,
                            genesis = genesis,
                            alias = alias,
                            doctrineText = cachedDoctrineText,
                            policyText = cachedPolicyText,
                            priorHistory = priorHistory,
                            fallbackGenesisCoreId = genesisCore.value?.coreId,
                            runtimeLabel = runtimeSlot.displayName,
                            runtimeConfig = runtimeConfig,
                            runtimeAccess = runtimeAccess
                        )
                    )
                }

                if (result.errorMessage != null) {
                    _chatError.value = result.errorMessage ?: "Error con el motor de razonamiento."
                }
            } finally {
                _isSending.value = false
            }
        }
    }

    suspend fun renameWorkspace(displayName: String): List<String> {
        return repository.renameWorkspace(displayName)
    }

    companion object {
        private const val MEMORY_EVENT_NODE_TYPE = "memory_event"
        private const val MEMORY_INTEGRITY_QUARANTINE_EVENT_TYPE = "memory_integrity.quarantine"
        private const val MAX_GRAPH_EVENT_LOOKUP = 60
    }
}
