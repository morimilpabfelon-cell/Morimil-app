package com.morimil.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.morimil.app.ai.ChatTurn
import com.morimil.app.ai.ReasoningClient
import com.morimil.app.ai.ReasoningConfigStore
import com.morimil.app.ai.ReasoningRuntimeState
import com.morimil.app.ai.SystemPromptBuilder
import com.morimil.app.data.genesis.GenesisIdentitySource
import com.morimil.app.data.genesis.GenesisReader
import com.morimil.app.data.local.DecisionLogEntity
import com.morimil.app.data.local.GenesisCoreEntity
import com.morimil.app.data.local.LocalInstanceIdentityEntity
import com.morimil.app.data.local.MemoryEventEntity
import com.morimil.app.data.local.MemoryLinkEntity
import com.morimil.app.data.local.MemoryMessageEntity
import com.morimil.app.data.local.MemorySnapshotEntity
import com.morimil.app.data.local.MorimilDatabase
import com.morimil.app.data.local.MemoryOrganDatabase
import com.morimil.app.data.local.MigrationRecordEntity
import com.morimil.app.data.local.ProjectStateEntity
import com.morimil.app.data.local.RecallScheduleEntity
import com.morimil.app.data.local.UserWorkspaceEntity
import com.morimil.app.data.repository.MemoryLinkRepository
import com.morimil.app.data.repository.MemoryRepository
import com.morimil.app.data.repository.RestCycleRepository
import com.morimil.app.data.repository.MemoryOrganRepository
import com.morimil.app.data.repository.MigrationRecordRepository
import com.morimil.app.data.repository.RecallScheduleRepository
import com.morimil.app.security.SecretVault
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

class MorimilViewModel(application: Application) : AndroidViewModel(application) {
    private val memoryDatabase = MorimilDatabase.getInstance(application)
    private val organDatabase = MemoryOrganDatabase.getInstance(application)
    private val repository = MemoryRepository(memoryDatabase)
    private val restCycleRepository = RestCycleRepository(
        database = memoryDatabase,
        organDatabase = organDatabase
    )
    private val memoryOrganRepository = MemoryOrganRepository(organDatabase)
    private val memoryLinkRepository = MemoryLinkRepository(organDatabase)
    private val migrationRecordRepository = MigrationRecordRepository(organDatabase)
    private val recallScheduleRepository = RecallScheduleRepository(
        organDatabase = organDatabase,
        memoryDatabase = memoryDatabase
    )
    private val genesisReader = GenesisReader(application)
    private val secretVault = SecretVault(application)
    private val reasoningConfigStore = ReasoningConfigStore(application)
    private val reasoningClient = ReasoningClient()

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

    private var selectedMemoryLinksJob: Job? = null

    val recentMigrationRecords: StateFlow<List<MigrationRecordEntity>> = migrationRecordRepository.recentMigrationRecords.stateIn(
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
        viewModelScope.launch {
            repository.seedInitialStateIfNeeded()
            runCatching { restCycleRepository.runLocalRestCycleIfDue() }
            runCatching { recallScheduleRepository.seedFromRecentMemoryIfNeeded() }
        }
        refreshGenesis()
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
            }
        }
    }

    fun clearSelectedMemoryEvent() {
        _selectedMemoryEventHash.value = null
        selectedMemoryLinksJob?.cancel()
        selectedMemoryLinksJob = null
        _selectedMemoryLinks.value = emptyList()
    }

    private fun recordMemoryReview(
        event: MemoryEventEntity,
        action: String,
        note: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.recordMemoryReview(
                    targetEvent = event,
                    action = action,
                    note = note
                )
            }
        }
    }

    fun seedRecallScheduleIfNeeded() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                recallScheduleRepository.seedFromRecentMemoryIfNeeded()
            }
        }
    }

    fun reinforceRecall(recallId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                recallScheduleRepository.reinforceRecall(recallId)
            }
        }
    }

    fun postponeRecall(recallId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                recallScheduleRepository.postponeRecall(recallId)
            }
        }
    }

    fun degradeRecall(recallId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                recallScheduleRepository.degradeRecall(recallId)
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
                runCatching { restCycleRepository.runLocalRestCycleIfDue() }
            runCatching { recallScheduleRepository.seedFromRecentMemoryIfNeeded() }
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
            ReasoningRuntimeState.set(runtimeConfig)
            val runtimeAccess = secretVault.readReasoningKey(runtimeSlot.id).getOrNull().orEmpty()
            if (runtimeConfig.requiresRuntimeKey && runtimeAccess.isBlank()) {
                _chatError.value = "Falta la llave del motor activo: ${runtimeSlot.displayName}."
                return@launch
            }

            val alias = localIdentity.value?.alias ?: genesis.alias

            _isSending.value = true
            try {
                val priorHistory = messages.value
                    .takeLast(ReasoningClient.MAX_HISTORY_MESSAGES - 1)
                    .map { ChatTurn(role = if (it.author == "user") "user" else "assistant", content = it.body) }
                val recent = priorHistory + ChatTurn(role = "user", content = cleanBody)

                repository.addUserMessage(cleanBody)
                val activeGenesisCoreId = genesisCore.value?.coreId ?: "primary_genesis"
                memoryOrganRepository.captureKnowledgeCapsuleFromText(activeGenesisCoreId, cleanBody)

                val memoryContext = repository.buildLivingMemoryContext()
                val knowledgeCapsuleContext = memoryOrganRepository.buildKnowledgeCapsuleContext()
                val systemPrompt = SystemPromptBuilder.build(
                    genesis = genesis,
                    alias = alias,
                    doctrineText = cachedDoctrineText,
                    policyText = cachedPolicyText,
                    livingMemoryContext = memoryContext,
                    knowledgeCapsuleContext = knowledgeCapsuleContext
                )

                val response = withContext(Dispatchers.IO) {
                    reasoningClient.sendMessage(
                        config = runtimeConfig,
                        runtimeKey = runtimeAccess,
                        systemPrompt = systemPrompt,
                        history = recent
                    )
                }

                response
                    .onSuccess { reply -> repository.addAssistantMessage(reply)
                    runCatching { restCycleRepository.runLocalRestCycleIfDue() }
            runCatching { recallScheduleRepository.seedFromRecentMemoryIfNeeded() } }
                    .onFailure { error -> _chatError.value = error.message ?: "Error con el motor de razonamiento." }
            } finally {
                _isSending.value = false
            }
        }
    }

    suspend fun renameWorkspace(displayName: String): List<String> {
        return repository.renameWorkspace(displayName)
    }
}
