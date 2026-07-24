package com.morimil.app.data.repository

import com.morimil.app.data.local.MorimilDatabase
import com.morimil.app.data.local.ReasoningTurnAuthor
import com.morimil.app.data.local.ReasoningTurnEntity
import kotlinx.coroutines.flow.Flow

/**
 * Stores the UI transcript outside living and canonical memory. This repository
 * has no memory-event DAO and therefore cannot promote any displayed reply into
 * a durable memory event.
 */
class ReasoningTranscriptRepository(
    database: MorimilDatabase,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private val dao = database.reasoningTranscriptDao()

    val turns: Flow<List<ReasoningTurnEntity>> = dao.observeTurns()

    suspend fun appendUserTurn(body: String) {
        append(author = ReasoningTurnAuthor.USER, body = body)
    }

    suspend fun appendMorimilTurn(body: String) {
        append(author = ReasoningTurnAuthor.MORIMIL, body = body)
    }

    suspend fun appendAuxiliaryAdvisoryTurn(body: String) {
        append(author = ReasoningTurnAuthor.AUXILIARY_ADVISORY, body = body)
    }

    suspend fun seedIntroTurnsIfNeeded() {
        if (dao.countTurns() != 0) return
        appendMorimilTurn("Genesis movil v1 activo. Historial de conversacion separado de memoria viva.")
        appendMorimilTurn("Voz manual activa. Sin sincronizacion externa ni ejecucion de PC.")
    }

    private suspend fun append(author: String, body: String) {
        val clean = body.trim()
        require(clean.isNotEmpty()) { "reasoning_transcript_turn_empty" }
        require(
            author == ReasoningTurnAuthor.USER ||
                author == ReasoningTurnAuthor.MORIMIL ||
                author == ReasoningTurnAuthor.AUXILIARY_ADVISORY
        ) { "reasoning_transcript_author_not_allowed" }
        dao.insertTurn(
            ReasoningTurnEntity(
                author = author,
                body = clean,
                createdAtMillis = nowMillis()
            )
        )
    }
}
