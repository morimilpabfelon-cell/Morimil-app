package com.morimil.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.morimil.app.data.local.MorimilDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
        repository.appendMorimilTurn("respuesta calculada")

        assertEquals(2, database.reasoningTranscriptDao().countTurns())
        assertEquals(0, database.memoryDao().countMemoryEvents())
        assertEquals(0, database.genesisUltraMemoryDao().countAll())
        assertEquals(0, database.genesisUltraBirthDao().countBirthCommits())
    }
}
