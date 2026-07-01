package com.morimil.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.morimil.app.data.local.DecisionLogEntity
import com.morimil.app.data.local.MemoryMessageEntity
import com.morimil.app.data.local.MorimilDatabase
import com.morimil.app.data.local.ProjectStateEntity
import com.morimil.app.data.local.UserWorkspaceEntity
import com.morimil.app.data.repository.MemoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MorimilViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MemoryRepository(
        MorimilDatabase.getInstance(application).memoryDao()
    )

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

    init {
        viewModelScope.launch {
            repository.seedInitialStateIfNeeded()
        }
    }

    fun sendMessage(body: String) {
        val cleanBody = body.trim()
        if (cleanBody.isEmpty()) return

        viewModelScope.launch {
            repository.addUserMessage(cleanBody)
            repository.addAssistantMessage(
                "Recibido y guardado localmente. Genesis queda read-only; el celular es el workspace primario."
            )
        }
    }

    fun saveWorkspaceProposal(
        displayName: String,
        repoOwner: String?,
        repoName: String?,
        repoPrivate: Boolean,
        approved: Boolean
    ) {
        val cleanName = displayName.trim().ifBlank { "Local Morimil Workspace" }
        viewModelScope.launch {
            repository.saveWorkspaceProposal(
                displayName = cleanName,
                repoOwner = repoOwner?.trim()?.ifBlank { null },
                repoName = repoName?.trim()?.ifBlank { null },
                repoPrivate = repoPrivate,
                approved = approved
            )
        }
    }
}
