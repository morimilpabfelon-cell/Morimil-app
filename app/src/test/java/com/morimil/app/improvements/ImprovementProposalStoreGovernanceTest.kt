package com.morimil.app.improvements

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class ImprovementProposalStoreGovernanceTest {
    @Test
    fun approvalDecisionsCannotBypassAudit() {
        val source = String(Files.readAllBytes(locateSourceFile()), StandardCharsets.UTF_8)

        assertTrue(
            "Improvement approvals must keep the audited approval entrypoint.",
            Regex("""suspend\s+fun\s+approveWithAudit\s*\(""").containsMatchIn(source)
        )
        assertTrue(
            "Improvement denials must keep the audited denial entrypoint.",
            Regex("""suspend\s+fun\s+denyWithAudit\s*\(""").containsMatchIn(source)
        )

        assertFalse(
            "Do not reintroduce public approve(id: String); it bypasses decision audit history.",
            Regex("""(?m)^\s*fun\s+approve\s*\(\s*id\s*:\s*String\s*\)""").containsMatchIn(source)
        )
        assertFalse(
            "Do not reintroduce public deny(id: String); it bypasses decision audit history.",
            Regex("""(?m)^\s*fun\s+deny\s*\(\s*id\s*:\s*String\s*\)""").containsMatchIn(source)
        )
    }

    private fun locateSourceFile(): Path {
        val withModule = Path.of("app", "src", "main", "java", "com", "morimil", "app", "improvements", "ImprovementProposalStore.kt")
        val insideModule = Path.of("src", "main", "java", "com", "morimil", "app", "improvements", "ImprovementProposalStore.kt")
        var current: Path? = Path.of("").toAbsolutePath()

        while (current != null) {
            val candidates = listOf(
                current.resolve(withModule),
                current.resolve(insideModule)
            )

            candidates.firstOrNull { candidate -> Files.exists(candidate) }?.let { candidate ->
                return candidate
            }

            current = current.parent
        }

        error("Could not locate ImprovementProposalStore.kt from ${Path.of("").toAbsolutePath()}.")
    }
}
