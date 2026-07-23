package com.morimil.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemorySigningRuntimeIssuesTest {
    @Test
    fun keystoreFailureIssueIsCountedAndClearable() {
        MemorySigningRuntimeIssues.clearKeystoreSigningFailure("test")

        MemorySigningRuntimeIssues.reportKeystoreSigningFailure(
            keyAlias = "test_alias",
            error = IllegalStateException("hardware unavailable")
        )
        MemorySigningRuntimeIssues.reportKeystoreSigningFailure(
            keyAlias = "test_alias",
            error = IllegalStateException("hardware unavailable")
        )

        val issue = requireNotNull(MemorySigningRuntimeIssues.latestIssue.value)
        assertEquals(MemorySigningRuntimeIssues.KEYSTORE_FAILURE_COMPONENT, issue.component)
        assertEquals(2, issue.failureCount)
        assertTrue(issue.message.contains("memory append was blocked"))

        MemorySigningRuntimeIssues.clearKeystoreSigningFailure("test_alias")

        assertNull(MemorySigningRuntimeIssues.latestIssue.value)
    }
}
