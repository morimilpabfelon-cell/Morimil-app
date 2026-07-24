package com.morimil.app.reasoning.intrinsic

import android.util.Log
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Instrumentation-only bridge from the exact v0.2 LiteRT-LM engine to DeliberativeMotorV0.
 * One request owns one conversation and release closes that conversation only after every
 * synchronous native call has returned.
 */
internal class Arm64TriMotorDeliberativeCoreV0(
    private val engine: Engine
) : MorimilDeliberativeCore {
    override val artifactVersion: String =
        MorimilDeliberativeArtifactContractV02Candidate.ARTIFACT_VERSION
    override val artifactSha256: String =
        MorimilDeliberativeArtifactContractV02Candidate.ARTIFACT_SHA256

    private val opened = AtomicInteger(0)
    private val closed = AtomicInteger(0)
    private val nativeCallGate = Arm64NativeCallGateV0()

    val openedConversationCount: Int
        get() = opened.get()

    val closedConversationCount: Int
        get() = closed.get()

    val activeNativeCallCount: Int
        get() = nativeCallGate.activeCallCount

    override suspend fun initialize(
        input: DeliberativeCoreInput
    ): Result<DeliberativeLatentState> = runCatching {
        val systemInstruction = input.systemPrompt.trim()
        require(systemInstruction.isNotEmpty()) { "trimotor_deliberative_system_prompt_empty" }
        require(input.history.isNotEmpty()) { "trimotor_deliberative_history_empty" }

        val conversation = engine.createConversation(
            ConversationConfig(systemInstruction = Contents.of(systemInstruction))
        )
        val conversationOrdinal = opened.incrementAndGet()
        Log.i(LOG_TAG, "conversation_opened:$conversationOrdinal")
        Arm64TriMotorDeliberativeStateV0(
            owner = this,
            conversation = conversation,
            conversationOrdinal = conversationOrdinal,
            releaseGuard = AtomicBoolean(false),
            initialPrompt = renderHistory(input),
            pass = 0,
            latestDraft = null,
            normalizedDraft = null
        )
    }

    override suspend fun refine(
        state: DeliberativeLatentState,
        pass: Int
    ): Result<DeliberativePassOutcome> = runCatching {
        val current = requireOwned(state)
        check(!current.releaseGuard.get()) { "trimotor_deliberative_state_released" }
        require(pass == current.pass + 1) { "trimotor_deliberative_pass_out_of_sequence" }

        val prompt = if (pass == 1) {
            current.initialPrompt
        } else {
            "Deliberative pass $pass. Recheck the answer already in this local conversation " +
                "for correctness and compliance. Return only the revised final answer."
        }
        Log.i(LOG_TAG, "native_call_start:${current.conversationOrdinal}:$pass")
        val response = nativeCallGate.run {
            current.conversation.sendMessage(prompt)
        }
        Log.i(LOG_TAG, "native_call_complete:${current.conversationOrdinal}:$pass")
        val draft = response.contents.contents
            .filterIsInstance<Content.Text>()
            .joinToString(separator = "") { content -> content.text }
            .trim()
        require(draft.isNotEmpty()) { "trimotor_deliberative_empty_response" }
        require(draft.length <= MorimilDeliberativeBenchmarkDatasetV0.MAX_RESPONSE_CHARS) {
            "trimotor_deliberative_response_too_large:${draft.length}"
        }

        val normalized = draft.replace(WHITESPACE, " ").trim()
        val exactlyStable = current.normalizedDraft != null &&
            current.normalizedDraft == normalized
        val next = current.copy(
            pass = pass,
            latestDraft = draft,
            normalizedDraft = normalized
        )
        DeliberativePassOutcome(
            state = next,
            certaintyPermille = if (exactlyStable) 1_000 else 900,
            stabilityPermille = if (exactlyStable) 1_000 else 900
        )
    }

    override suspend fun decode(
        state: DeliberativeLatentState
    ): Result<String> = runCatching {
        val current = requireOwned(state)
        check(!current.releaseGuard.get()) { "trimotor_deliberative_state_released" }
        requireNotNull(current.latestDraft) { "trimotor_deliberative_draft_missing" }
    }

    override suspend fun release(state: DeliberativeLatentState) {
        val current = requireOwned(state)
        if (!current.releaseGuard.compareAndSet(false, true)) return
        nativeCallGate.closeWhenIdle {
            current.conversation.close()
        }
        val closedCount = closed.incrementAndGet()
        Log.i(LOG_TAG, "conversation_closed:${current.conversationOrdinal}:$closedCount")
    }

    private fun requireOwned(state: DeliberativeLatentState): Arm64TriMotorDeliberativeStateV0 {
        val current = state as? Arm64TriMotorDeliberativeStateV0
            ?: error("trimotor_deliberative_state_type_invalid")
        check(current.owner === this) { "trimotor_deliberative_state_owner_mismatch" }
        return current
    }

    private fun renderHistory(input: DeliberativeCoreInput): String {
        return buildString {
            append("Work only from this request-scoped transcript. Return only the final answer. ")
            append("Do not expose hidden reasoning or claim external tools.\n\n")
            input.history.forEach { turn ->
                val content = turn.content.trim()
                require(content.isNotEmpty()) { "trimotor_deliberative_history_turn_empty" }
                append(roleLabel(turn.role)).append(": ").append(content).append('\n')
            }
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

    private data class Arm64TriMotorDeliberativeStateV0(
        val owner: Arm64TriMotorDeliberativeCoreV0,
        val conversation: Conversation,
        val conversationOrdinal: Int,
        val releaseGuard: AtomicBoolean,
        val initialPrompt: String,
        val pass: Int,
        val latestDraft: String?,
        val normalizedDraft: String?
    ) : DeliberativeLatentState

    private companion object {
        const val LOG_TAG = "MorimilTriMotor"
        val WHITESPACE = Regex("\\s+")
    }
}
