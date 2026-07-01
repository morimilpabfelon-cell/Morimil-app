package com.morimil.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.morimil.app.ai.ChatTurn
import com.morimil.app.ai.ClaudeApiClient
import com.morimil.app.ai.SystemPromptBuilder
import com.morimil.app.data.genesis.GenesisIdentitySource
import com.morimil.app.data.genesis.GenesisReader
import com.morimil.app.data.local.DecisionLogEntity
import com.morimil.app.data.local.LocalInstanceIdentityEntity
import com.morimil.app.data.local.MemoryMessageEntity
import com.morimil.app.data.local.MorimilDatabase
import com.morimil.app.data.local.ProjectStateEntity
import com.morimil.app.data.local.UserWorkspaceEntity
import com.morimil.app.data.repository.MemoryRepository
import com.morimil.app.github.GitHubForkClient
import com.morimil.app.security.SecretVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MorimilViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MemoryRepository(
        MorimilDatabase.getInstance(application).memoryDao()
    )
    private val genesisReader = GenesisReader(application)
    private val forkClient = GitHubForkClient()
    private val secretVault = SecretVault(application)
    private val claudeClient = ClaudeApiClient()

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

    private val _genesisResult = MutableStateFlow<Result<GenesisIdentitySource>?>(null)
    val genesisResult: StateFlow<Result<GenesisIdentitySource>?> = _genesisResult.asStateFlow()

    // Doctrine text is fetched once and cached -- it does not change per
    // message, so re-fetching it on every send would be wasted network.
    private var cachedDoctrineText: String? = null

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _chatError = MutableStateFlow<String?>(null)
    val chatError: StateFlow<String?> = _chatError.asStateFlow()

    init {
        viewModelScope.launch {
            repository.seedInitialStateIfNeeded()
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
        }
    }

    fun hasGitHubToken(): Boolean = secretVault.hasGitHubToken()

    fun saveGitHubToken(token: String): Result<Unit> = secretVault.saveGitHubToken(token)

    fun hasAnthropicKey(): Boolean = secretVault.hasAnthropicKey()

    fun saveAnthropicKey(key: String): Result<Unit> = secretVault.saveAnthropicKey(key)

    /**
     * First-install gate: forks the Genesis repo under the token's own
     * account, then names this device's instance from the fetched Genesis
     * identity, tied to that fork. Only called once, from the onboarding
     * screen, which only shows while localIdentity is null. If forking
     * fails, no local identity is created -- onboarding can be retried.
     */
    suspend fun bornInstance(alias: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val genesis = _genesisResult.value?.getOrNull()?.identity
                ?: error("Genesis identity not loaded yet.")
            val token = secretVault.readGitHubToken().getOrNull()
                ?: error("No GitHub token stored yet.")

            val fork = forkClient.forkGenesisRepo(token).getOrThrow()
            repository.birthLocalIdentity(alias, genesis, fork)
        }
    }

    /**
     * Real conversation. The model itself has no memory of its own -- the
     * phone's own stored history (up to MAX_HISTORY_MESSAGES) is sent as
     * context every time, which is how "aprende poco a poco" actually
     * works: continuity lives in the phone's memory, not in the model.
     */
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

            val apiKey = secretVault.readAnthropicKey().getOrNull()
            if (apiKey.isNullOrBlank()) {
                _chatError.value = "Falta la llave de la API de Claude."
                return@launch
            }

            val alias = localIdentity.value?.alias ?: genesis.alias

            _isSending.value = true
            try {
                val priorHistory = messages.value
                    .takeLast(ClaudeApiClient.MAX_HISTORY_MESSAGES - 1)
                    .map { ChatTurn(role = if (it.author == "user") "user" else "assistant", content = it.body) }
                val recent = priorHistory + ChatTurn(role = "user", content = cleanBody)

                repository.addUserMessage(cleanBody)

                val systemPrompt = SystemPromptBuilder.build(genesis, alias, cachedDoctrineText)

                claudeClient.sendMessage(apiKey, systemPrompt, recent)
                    .onSuccess { reply -> repository.addAssistantMessage(reply) }
                    .onFailure { error -> _chatError.value = error.message ?: "Error al hablar con Claude." }
            } finally {
                _isSending.value = false
            }
        }
    }

    suspend fun renameWorkspace(displayName: String): List<String> {
        return repository.renameWorkspace(displayName)
    }
}
