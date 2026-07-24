package com.morimil.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.morimil.app.data.local.MorimilDatabase
import com.morimil.app.data.local.ReasoningTurnAuthor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReasoningTranscriptRepositoryTest {
    private lateinit var database: MorimilDatabase

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, MorimilDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun transcriptTurnsNeverBecomeLegacyOrCanonicalMemory() = runBlocking {
        var now = 1000L
        val repository = ReasoningTranscriptRepository(database) { now++ }

        repository.appendUserTurn("pregunta")
        repository.appendMorimilTurn("respuesta intrinseca")
        repository.appendAuxiliaryAdvisoryTurn("calculo externo")

        val turns = repository.turns.first()
        assertEquals(3, turns.size)
        assertEquals(ReasoningTurnAuthor.USER, turns[0].author)
        assertEquals(ReasoningTurnAuthor.MORIMIL, turns[1].author)
        assertEquals(ReasoningTurnAuthor.AUXILIARY_ADVISORY, turns[2].author)
        assertTrue(
            turns[2].body.startsWith(
                ReasoningTranscriptRepository.AUXILIARY_ADVISORY_LABEL
            )
        )
        assertFalse(ReasoningTurnAuthor.isTrustedConversationAuthor(turns[2].author))
        assertEquals(0, database.memoryDao().countMemoryEvents())
        assertEquals(0, database.genesisUltraMemoryDao().countAll())
        assertEquals(0, database.genesisUltraBirthDao().countBirthCommits())
    }
}
