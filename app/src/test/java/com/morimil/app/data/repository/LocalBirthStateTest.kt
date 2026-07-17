package com.morimil.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LocalBirthStateTest {
    @Test
    fun noIdentityAndNoCoreIsAbsent() {
        assertEquals(LocalBirthState.ABSENT, LocalBirthState.fromCounts(0, 0))
    }

    @Test
    fun exactlyOneIdentityAndOneCoreIsComplete() {
        assertEquals(LocalBirthState.COMPLETE, LocalBirthState.fromCounts(1, 1))
    }

    @Test
    fun partialOrDuplicatedStateIsInconsistent() {
        listOf(
            1 to 0,
            0 to 1,
            2 to 1,
            1 to 2,
            2 to 2
        ).forEach { (identityCount, coreCount) ->
            assertEquals(
                LocalBirthState.INCONSISTENT,
                LocalBirthState.fromCounts(identityCount, coreCount)
            )
        }
    }

    @Test
    fun negativeCountsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            LocalBirthState.fromCounts(-1, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LocalBirthState.fromCounts(0, -1)
        }
    }
}
