package com.morimil.app.data.repository

import com.morimil.app.data.local.MorimilDatabase
import com.morimil.app.data.local.ReasoningTurnEntity
import kotlinx.coroutines.flow.Flow

/**
 * Stores the UI transcript outside living and canonical memory. This repository
 * has no memory-event DAO and therefore cannot promote a motor reply into a
 * durable memory event.
 */
class ReasoningTranscriptRepository(
    database: MorimilDatabase,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private val dao = database.reasoningTranscriptDao()

    val turns: Flow<List<ReasoningTurnEntity>> = dao.observeTurns()

    suspend fun appendUserTurn(body: String) {
        append(author = "user", body = body)
    }

    suspend fun appendMorimilTurn(body: String) {
        append(author = "morimil", body = body)
    }

    suspend fun seedIntroTurnsIfNeeded() {
        if (dao.countTurns() != 0) return
        appendMorimilTurn("Genesis movil v1 activo. Historial de conversacion separado de memoria viva.")
        appendMorimilTurn("Voz manual activa. Sin sincronizacion externa ni ejecucion de PC.")
    }

    private suspend fun append(author: String, body: String) {
        val clean = body.trim()
        require(clean.isNotEmpty()) { "reasoning_transcript_turn_empty" }
        dao.insertTurn(
            ReasoningTurnEntity(
                author = author,
                body = clean,
                createdAtMillis = nowMillis()
            )
        )
    }
}
