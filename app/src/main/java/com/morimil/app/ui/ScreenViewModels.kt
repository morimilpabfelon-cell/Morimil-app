package com.morimil.app.ui

import com.morimil.app.data.local.AutobiographicalSnapshotEntity
import com.morimil.app.data.local.DecisionLogEntity
import com.morimil.app.data.local.KnowledgeCapsuleEntity
import com.morimil.app.data.local.MemoryEventEntity
import com.morimil.app.data.local.MemoryLinkEntity
import com.morimil.app.data.local.MemoryMessageEntity
import com.morimil.app.data.local.MemorySnapshotEntity
import com.morimil.app.data.local.MigrationRecordEntity
import com.morimil.app.data.local.ProjectStateEntity
import com.morimil.app.data.local.RecallScheduleEntity
import com.morimil.app.runtime.RestCycleScheduleStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope

data class ChatUiState(
    val messages: List<MemoryMessageEntity> = emptyList(),
    val isSending: Boolean = false,
    val error: String? = null,
    val organismStatus: ChatOrganismStatusUiState = ChatOrganismStatusUiState(),
    val organismHealth: OrganismHealthUiState = OrganismHealthUiState()
)

data class MemoryUiState(
    val decisions: List<DecisionLogEntity> = emptyList(),
    val messages: List<MemoryMessageEntity> = emptyList(),
    val projects: List<ProjectStateEntity> = emptyList(),
    val snapshot: MemorySnapshotEntity? = null,
    val events: List<MemoryEventEntity> = emptyList(),
    val recalls: List<RecallScheduleEntity> = emptyList(),
    val migrations: List<MigrationRecordEntity> = emptyList(),
    val selfSnapshot: AutobiographicalSnapshotEntity? = null,
    val knowledgeCapsules: List<KnowledgeCapsuleEntity> = emptyList(),
    val recentLinks: List<MemoryLinkEntity> = emptyList(),
    val integrityAudit: MemoryIntegrityAuditUiState = MemoryIntegrityAuditUiState(),
    val restCycleScheduleStatus: RestCycleScheduleStatus = RestCycleScheduleStatus(),
    val organismHealth: OrganismHealthUiState = OrganismHealthUiState()
)

data class HealthUiState(
    val health: OrganismHealthUiState = OrganismHealthUiState(),
    val audit: MemoryIntegrityAuditUiState = MemoryIntegrityAuditUiState(),
    val scheduleStatus: RestCycleScheduleStatus = RestCycleScheduleStatus(),
    val internalIssue: InternalRuntimeIssueUiState? = null
)

data class MotorUiState(
    val title: String = "Motor/API",
    val boundary: String = "La API razona con contexto temporal; identidad y memoria viven localmente."
)

class ChatViewModel internal constructor(private val owner: MorimilViewModel) {
    val messages: StateFlow<List<MemoryMessageEntity>> = owner.messages
    val isSending: StateFlow<Boolean> = owner.isSending
    val chatError: StateFlow<String?> = owner.chatError
    val chatOrganismStatus: StateFlow<ChatOrganismStatusUiState> = owner.chatOrganismStatus
    val organismHealth: StateFlow<OrganismHealthUiState> = owner.organismHealth

    val uiState: StateFlow<ChatUiState> = combine(
        messages,
        isSending,
        chatError,
        chatOrganismStatus,
        organismHealth
    ) { messages, isSending, error, status, health ->
        ChatUiState(
            messages = messages,
            isSending = isSending,
            error = error,
            organismStatus = status,
            organismHealth = health
        )
    }.stateIn(owner.viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    fun refreshChatOrganismStatus() = owner.refreshChatOrganismStatus()
    fun refreshOrganismHealth() = owner.refreshOrganismHealth()
    fun sendMessage(body: String) = owner.sendMessage(body)
}

class MemoryViewModel internal constructor(private val owner: MorimilViewModel) {
    val decisions: StateFlow<List<DecisionLogEntity>> = owner.decisions
    val messages: StateFlow<List<MemoryMessageEntity>> = owner.messages
    val projects: StateFlow<List<ProjectStateEntity>> = owner.projects
    val livingMemorySnapshot: StateFlow<MemorySnapshotEntity?> = owner.livingMemorySnapshot
    val recentMemoryEvents: StateFlow<List<MemoryEventEntity>> = owner.recentMemoryEvents
    val activeRecallSchedules: StateFlow<List<RecallScheduleEntity>> = owner.activeRecallSchedules
    val recentMigrationRecords: StateFlow<List<MigrationRecordEntity>> = owner.recentMigrationRecords
    val selfSnapshot: StateFlow<AutobiographicalSnapshotEntity?> = owner.selfSnapshot
    val knowledgeCapsules: StateFlow<List<KnowledgeCapsuleEntity>> = owner.knowledgeCapsules
    val recentMemoryLinks: StateFlow<List<MemoryLinkEntity>> = owner.recentMemoryLinks
    val memoryIntegrityAudit: StateFlow<MemoryIntegrityAuditUiState> = owner.memoryIntegrityAudit
    val restCycleScheduleStatus: StateFlow<RestCycleScheduleStatus> = owner.restCycleScheduleStatus
    val organismHealth: StateFlow<OrganismHealthUiState> = owner.organismHealth
    val selectedMemoryEventHash: StateFlow<String?> = owner.selectedMemoryEventHash
    val selectedMemoryLinks: StateFlow<List<MemoryLinkEntity>> = owner.selectedMemoryLinks
    val selectedGraphEvents: StateFlow<List<MemoryEventEntity>> = owner.selectedGraphEvents

    private val memoryStateInputs: List<Flow<Any?>> = listOf(
        decisions,
        messages,
        projects,
        livingMemorySnapshot,
        recentMemoryEvents,
        activeRecallSchedules,
        recentMigrationRecords,
        selfSnapshot,
        knowledgeCapsules,
        recentMemoryLinks,
        memoryIntegrityAudit,
        restCycleScheduleStatus,
        organismHealth
    )

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<MemoryUiState> = combine(memoryStateInputs) { values ->
        MemoryUiState(
            decisions = values[0] as List<DecisionLogEntity>,
            messages = values[1] as List<MemoryMessageEntity>,
            projects = values[2] as List<ProjectStateEntity>,
            snapshot = values[3] as MemorySnapshotEntity?,
            events = values[4] as List<MemoryEventEntity>,
            recalls = values[5] as List<RecallScheduleEntity>,
            migrations = values[6] as List<MigrationRecordEntity>,
            selfSnapshot = values[7] as AutobiographicalSnapshotEntity?,
            knowledgeCapsules = values[8] as List<KnowledgeCapsuleEntity>,
            recentLinks = values[9] as List<MemoryLinkEntity>,
            integrityAudit = values[10] as MemoryIntegrityAuditUiState,
            restCycleScheduleStatus = values[11] as RestCycleScheduleStatus,
            organismHealth = values[12] as OrganismHealthUiState
        )
    }.stateIn(owner.viewModelScope, SharingStarted.WhileSubscribed(5_000), MemoryUiState())

    fun approveMemoryEvent(event: MemoryEventEntity) = owner.approveMemoryEvent(event)
    fun degradeMemoryEvent(event: MemoryEventEntity) = owner.degradeMemoryEvent(event)
    fun requestMemoryCorrection(event: MemoryEventEntity) = owner.requestMemoryCorrection(event)
    fun selectMemoryEvent(eventHash: String) = owner.selectMemoryEvent(eventHash)
    fun clearSelectedMemoryEvent() = owner.clearSelectedMemoryEvent()
    fun runMemoryIntegrityAudit() = owner.runMemoryIntegrityAudit()
    fun refreshOrganismHealth() = owner.refreshOrganismHealth()
    fun seedRecallScheduleIfNeeded() = owner.seedRecallScheduleIfNeeded()
    fun reinforceRecall(recallId: Long) = owner.reinforceRecall(recallId)
    fun postponeRecall(recallId: Long) = owner.postponeRecall(recallId)
    fun degradeRecall(recallId: Long) = owner.degradeRecall(recallId)
    fun runRestCycleNow() = owner.runRestCycleNow()
    fun enableRestCycleSchedule() = owner.enableRestCycleSchedule()
    fun cancelRestCycleSchedule() = owner.cancelRestCycleSchedule()
    fun refreshRestCycleScheduleStatus() = owner.refreshRestCycleScheduleStatus()
    fun approveRestCycleConsolidation(migrationId: String) = owner.approveRestCycleConsolidation(migrationId)
    fun proposeCognitiveMigration() = owner.proposeCognitiveMigration()
    fun approveCognitiveMigration(migrationId: String) = owner.approveCognitiveMigration(migrationId)
    fun executeCognitiveMigration(migrationId: String) = owner.executeCognitiveMigration(migrationId)
    fun rollbackCognitiveMigration(migrationId: String) = owner.rollbackCognitiveMigration(migrationId)
}

class HealthViewModel internal constructor(private val owner: MorimilViewModel) {
    val uiState: StateFlow<HealthUiState> = combine(
        owner.organismHealth,
        owner.memoryIntegrityAudit,
        owner.restCycleScheduleStatus,
        owner.internalRuntimeIssue
    ) { health, audit, scheduleStatus, internalIssue ->
        HealthUiState(
            health = health,
            audit = audit,
            scheduleStatus = scheduleStatus,
            internalIssue = internalIssue
        )
    }.stateIn(owner.viewModelScope, SharingStarted.WhileSubscribed(5_000), HealthUiState())

    fun refreshOrganismHealth() = owner.refreshOrganismHealth()
    fun runMemoryIntegrityAudit() = owner.runMemoryIntegrityAudit()
}

class MotorViewModel internal constructor(private val owner: MorimilViewModel) {
    private val _uiState = MutableStateFlow(MotorUiState())
    val uiState: StateFlow<MotorUiState> = _uiState.asStateFlow()

    fun refreshHealth() = owner.refreshOrganismHealth()
}
