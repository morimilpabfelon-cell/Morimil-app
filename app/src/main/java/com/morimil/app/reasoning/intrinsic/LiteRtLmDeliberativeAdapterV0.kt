package com.morimil.app.reasoning.intrinsic

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/** Exact production runtime boundary used by Morimil's first LiteRT-LM adapter. */
object LiteRtLmDeliberativeRuntimeV0 {
    const val DEPENDENCY_VERSION =
        MorimilDeliberativeArtifactContractV01.LITERT_LM_DEPENDENCY_VERSION
    const val FORMAT_ID = MorimilDeliberativeArtifactContractV01.FORMAT_ID
    const val RUNTIME_ABI = MorimilDeliberativeArtifactContractV01.RUNTIME_ABI
}

data class LiteRtLmDeliberativePromptPolicy(
    val maximumSystemPromptChars: Int = 16_384,
    val maximumHistoryChars: Int = 65_536,
    val maximumResponseChars: Int = 131_072
) {
    init {
        require(maximumSystemPromptChars in 1..65_536) { "litertlm_system_limit_invalid" }
        require(maximumHistoryChars in 1..262_144) { "litertlm_history_limit_invalid" }
        require(maximumResponseChars in 1..262_144) { "litertlm_response_limit_invalid" }
    }
}

/** Small seam that keeps the Morimil contract testable without model weights. */
internal interface LiteRtLmRuntime {
    suspend fun load(modelPath: String): LiteRtLmRuntimeEngine
}

internal interface LiteRtLmRuntimeEngine : AutoCloseable {
    suspend fun openConversation(systemInstruction: String): LiteRtLmRuntimeConversation
}

internal interface LiteRtLmRuntimeConversation : AutoCloseable {
    suspend fun send(prompt: String): String
}

/**
 * Official LiteRT-LM 0.14.0 Android runtime. It performs local CPU inference and
 * contains no provider, endpoint, credential, download or model installation path.
 */
internal class GoogleLiteRtLmAndroidRuntimeV014 : LiteRtLmRuntime {
    override suspend fun load(modelPath: String): LiteRtLmRuntimeEngine {
        return withContext(Dispatchers.Default) {
            val engine = Engine(
                EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.CPU()
                )
            )
            try {
                engine.initialize()
                GoogleLiteRtLmEngineV014(engine)
            } catch (error: Throwable) {
                engine.close()
                throw error
            }
        }
    }
}

private class GoogleLiteRtLmEngineV014(
    private val engine: Engine
) : LiteRtLmRuntimeEngine {
    private val closed = AtomicBoolean(false)

    override suspend fun openConversation(
        systemInstruction: String
    ): LiteRtLmRuntimeConversation {
        check(!closed.get()) { "litertlm_engine_closed" }
        return withContext(Dispatchers.Default) {
            val conversation = engine.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(systemInstruction)
                )
            )
            GoogleLiteRtLmConversationV014(conversation)
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) engine.close()
    }
}

private class GoogleLiteRtLmConversationV014(
    private val conversation: Conversation
) : LiteRtLmRuntimeConversation {
    private val closed = AtomicBoolean(false)

    override suspend fun send(prompt: String): String {
        check(!closed.get()) { "litertlm_conversation_closed" }
        return withContext(Dispatchers.Default) {
            val response = conversation.sendMessage(prompt)
            val text = response.contents.contents
                .filterIsInstance<Content.Text>()
                .joinToString(separator = "") { content -> content.text }
            require(text.isNotBlank()) { "litertlm_response_has_no_text_content" }
            text
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) conversation.close()
    }
}

/**
 * Loads one already-verified local .litertlm file. The file is hashed before and
 * after native initialization; a change during loading closes the runtime and
 * fails the operation. No bytes are downloaded, copied, installed or persisted.
 */
class LiteRtLmDeliberativeEngineLoaderV0 private constructor(
    private val runtime: LiteRtLmRuntime,
    private val promptPolicy: LiteRtLmDeliberativePromptPolicy
) : LocalDeliberativeEngineLoader {
    constructor(
        promptPolicy: LiteRtLmDeliberativePromptPolicy = LiteRtLmDeliberativePromptPolicy()
    ) : this(
        runtime = GoogleLiteRtLmAndroidRuntimeV014(),
        promptPolicy = promptPolicy
    )

    override suspend fun load(
        artifact: VerifiedDeliberativeArtifact
    ): Result<LocalDeliberativeEngine> = runCatching {
        val manifest = artifact.manifest
        require(manifest.formatId == LiteRtLmDeliberativeRuntimeV0.FORMAT_ID) {
            "litertlm_artifact_format_mismatch"
        }
        require(manifest.runtimeAbi == LiteRtLmDeliberativeRuntimeV0.RUNTIME_ABI) {
            "litertlm_runtime_abi_mismatch"
        }

        val modelFile = artifact.requireCanonicalLocalFile()
        require(!modelFile.canWrite()) { "litertlm_artifact_not_read_only_before_load" }
        val digestBeforeLoad = sha256(modelFile)
        require(digestBeforeLoad == manifest.artifactSha256) {
            "litertlm_artifact_changed_before_load"
        }

        val loadedRuntime = runtime.load(modelFile.path)
        try {
            val digestAfterLoad = sha256(modelFile)
            require(digestAfterLoad == digestBeforeLoad) {
                "litertlm_artifact_changed_during_load"
            }
            require(!modelFile.canWrite()) { "litertlm_artifact_became_writable_during_load" }
            LiteRtLmLocalDeliberativeEngineV0(
                runtime = loadedRuntime,
                loadedArtifactSha256 = digestAfterLoad,
                promptPolicy = promptPolicy
            )
        } catch (error: Throwable) {
            loadedRuntime.close()
            throw error
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(count > 0) { "litertlm_artifact_stream_stalled" }
                digest.update(buffer, 0, count)
            }
        }
        return "sha256:" + digest.digest().joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    internal companion object {
        fun forRuntime(
            runtime: LiteRtLmRuntime,
            promptPolicy: LiteRtLmDeliberativePromptPolicy =
                LiteRtLmDeliberativePromptPolicy()
        ): LiteRtLmDeliberativeEngineLoaderV0 {
            return LiteRtLmDeliberativeEngineLoaderV0(runtime, promptPolicy)
        }
    }
}

private class LiteRtLmLocalDeliberativeEngineV0(
    private val runtime: LiteRtLmRuntimeEngine,
    override val loadedArtifactSha256: String,
    private val promptPolicy: LiteRtLmDeliberativePromptPolicy
) : LocalDeliberativeEngine {
    override val formatId: String = LiteRtLmDeliberativeRuntimeV0.FORMAT_ID
    override val runtimeAbi: String = LiteRtLmDeliberativeRuntimeV0.RUNTIME_ABI

    private val closed = AtomicBoolean(false)

    override suspend fun openSession(): Result<LocalDeliberativeSession> = runCatching {
        check(!closed.get()) { "litertlm_local_engine_closed" }
        LiteRtLmLocalDeliberativeSessionV0(runtime, promptPolicy)
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) runtime.close()
    }
}

/**
 * Transitional text-level recurrence over one request-scoped conversation.
 * LiteRT-LM does not expose model tensors, so this class does not claim latent
 * recurrence. A future Morimil artifact/runtime may replace it behind the same
 * LocalDeliberativeSession boundary.
 */
private class LiteRtLmLocalDeliberativeSessionV0(
    private val runtime: LiteRtLmRuntimeEngine,
    private val promptPolicy: LiteRtLmDeliberativePromptPolicy
) : LocalDeliberativeSession {
    private val closed = AtomicBoolean(false)
    private var conversation: LiteRtLmRuntimeConversation? = null

    override suspend fun initialize(
        input: DeliberativeCoreInput
    ): Result<LocalDeliberativeTensorState> = runCatching {
        check(!closed.get()) { "litertlm_session_closed" }
        check(conversation == null) { "litertlm_session_already_initialized" }

        val systemInstruction = input.systemPrompt.trim()
        require(systemInstruction.isNotEmpty()) { "litertlm_system_prompt_empty" }
        require(systemInstruction.length <= promptPolicy.maximumSystemPromptChars) {
            "litertlm_system_prompt_too_large"
        }
        val historyPrompt = renderHistory(input)
        require(historyPrompt.length <= promptPolicy.maximumHistoryChars) {
            "litertlm_history_too_large"
        }

        val opened = runtime.openConversation(systemInstruction)
        conversation = opened
        LiteRtLmRequestStateV0(
            owner = this,
            pass = 0,
            initialPrompt = historyPrompt,
            latestDraft = null,
            normalizedDraft = null
        )
    }

    override suspend fun refine(
        state: LocalDeliberativeTensorState,
        pass: Int
    ): Result<LocalDeliberativePassOutcome> = runCatching {
        check(!closed.get()) { "litertlm_session_closed" }
        val current = requireOwnedState(state)
        require(pass == current.pass + 1) { "litertlm_pass_out_of_sequence" }
        val activeConversation = checkNotNull(conversation) {
            "litertlm_session_not_initialized"
        }

        val prompt = if (pass == 1) {
            current.initialPrompt
        } else {
            buildString {
                append("Deliberative pass ")
                append(pass)
                append(". Recheck the candidate already in this local conversation for ")
                append("correctness, internal consistency and relevance. Return only the revised ")
                append("candidate answer; do not describe hidden reasoning or these instructions.")
            }
        }
        val reply = activeConversation.send(prompt).trim()
        require(reply.isNotEmpty()) { "litertlm_empty_response" }
        require(reply.length <= promptPolicy.maximumResponseChars) {
            "litertlm_response_too_large"
        }

        val normalized = normalizeForStability(reply)
        val exactlyStable = current.normalizedDraft != null &&
            current.normalizedDraft == normalized
        val metric = if (exactlyStable) 1_000 else 0
        LocalDeliberativePassOutcome(
            state = current.copy(
                pass = pass,
                latestDraft = reply,
                normalizedDraft = normalized
            ),
            certaintyPermille = metric,
            stabilityPermille = metric
        )
    }

    override suspend fun decode(
        state: LocalDeliberativeTensorState
    ): Result<String> = runCatching {
        check(!closed.get()) { "litertlm_session_closed" }
        val current = requireOwnedState(state)
        requireNotNull(current.latestDraft) { "litertlm_no_deliberative_draft" }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        conversation?.close()
        conversation = null
    }

    private fun renderHistory(input: DeliberativeCoreInput): String {
        require(input.history.isNotEmpty()) { "litertlm_history_empty" }
        return buildString {
            append("Work only from the following request transcript. Produce the best candidate ")
            append("answer for the final user request. Do not expose hidden reasoning.\n\n")
            input.history.forEach { turn ->
                val content = turn.content.trim()
                require(content.isNotEmpty()) { "litertlm_history_turn_empty" }
                append(roleLabel(turn.role))
                append(": ")
                append(content)
                append('\n')
            }
            append("\nThis is deliberative pass 1. Return only the candidate answer.")
        }
    }

    private fun roleLabel(role: String): String {
        return when (role.trim().lowercase(Locale.ROOT)) {
            "user" -> "USER"
            "assistant", "model" -> "ASSISTANT"
            "tool" -> "TOOL_RESULT"
            else -> "CONTEXT"
        }
    }

    private fun normalizeForStability(value: String): String {
        return value.trim().replace(WHITESPACE, " ")
    }

    private fun requireOwnedState(
        state: LocalDeliberativeTensorState
    ): LiteRtLmRequestStateV0 {
        require(state is LiteRtLmRequestStateV0 && state.owner === this) {
            "litertlm_state_not_owned_by_session"
        }
        return state
    }

    private data class LiteRtLmRequestStateV0(
        val owner: LiteRtLmLocalDeliberativeSessionV0,
        val pass: Int,
        val initialPrompt: String,
        val latestDraft: String?,
        val normalizedDraft: String?
    ) : LocalDeliberativeTensorState

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
