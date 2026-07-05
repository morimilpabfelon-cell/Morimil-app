package com.morimil.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemorySigningRuntimeIssuesTest {
    @Test
    fun keystoreFallbackIssueIsCountedAndClearable() {
        MemorySigningRuntimeIssues.clearKeystoreSigningFallback("test")

        MemorySigningRuntimeIssues.reportKeystoreSigningFallback(
            keyAlias = "test_alias",
            error = IllegalStateException("hardware unavailable")
        )
        MemorySigningRuntimeIssues.reportKeystoreSigningFallback(
            keyAlias = "test_alias",
            error = IllegalStateException("hardware unavailable")
        )

        val issue = requireNotNull(MemorySigningRuntimeIssues.latestIssue.value)
        assertEquals(MemorySigningRuntimeIssues.KEYSTORE_FALLBACK_COMPONENT, issue.component)
        assertEquals(2, issue.failureCount)
        assertTrue(issue.message.contains("unsigned fallback"))

        MemorySigningRuntimeIssues.clearKeystoreSigningFallback("test_alias")

        assertNull(MemorySigningRuntimeIssues.latestIssue.value)
    }
}
