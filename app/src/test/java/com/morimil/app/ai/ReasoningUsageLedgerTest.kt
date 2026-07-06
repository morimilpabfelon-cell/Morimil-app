package com.morimil.app.ai

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReasoningUsageLedgerTest {
    @Test
    fun parsesChatCompletionUsageShape() {
        val usage = ReasoningUsageParser.parseApiUsage(
            """
                {
                  "choices": [],
                  "usage": {
                    "prompt_tokens": 1200,
                    "completion_tokens": 300,
                    "total_tokens": 1500,
                    "prompt_tokens_details": {"cached_tokens": 800},
                    "completion_tokens_details": {"reasoning_tokens": 64}
                  }
                }
            """.trimIndent()
        )

        assertEquals(1200, usage?.inputTokens)
        assertEquals(800, usage?.cachedInputTokens)
        assertEquals(300, usage?.outputTokens)
        assertEquals(64, usage?.reasoningTokens)
        assertEquals(1500, usage?.totalTokens)
    }

    @Test
    fun parsesResponsesUsageShape() {
        val usage = ReasoningUsageParser.parseApiUsage(
            """
                {
                  "output_text": "ok",
                  "usage": {
                    "input_tokens": 4000,
                    "output_tokens": 600,
                    "total_tokens": 4600,
                    "input_tokens_details": {"cached_tokens": 3000},
                    "output_tokens_details": {"reasoning_tokens": 128}
                  }
                }
            """.trimIndent()
        )

        assertEquals(4000, usage?.inputTokens)
        assertEquals(3000, usage?.cachedInputTokens)
        assertEquals(600, usage?.outputTokens)
        assertEquals(128, usage?.reasoningTokens)
        assertEquals(4600, usage?.totalTokens)
    }

    @Test
    fun parsesMessagesUsageShapeAndComputesTotalWhenMissing() {
        val usage = ReasoningUsageParser.parseApiUsage(
            """
                {
                  "content": [{"type":"text","text":"ok"}],
                  "usage": {
                    "input_tokens": 900,
                    "output_tokens": 120,
                    "cache_read_input_tokens": 600
                  }
                }
            """.trimIndent()
        )

        assertEquals(900, usage?.inputTokens)
        assertEquals(600, usage?.cachedInputTokens)
        assertEquals(120, usage?.outputTokens)
        assertEquals(1020, usage?.totalTokens)
    }

    @Test
    fun returnsNullWhenUsageIsAbsent() {
        val usage = ReasoningUsageParser.parseApiUsage("{\"output_text\":\"ok\"}")

        assertNull(usage)
    }

    @Test
    fun parsesUsageObjectDirectlyForUnitLevelCoverage() {
        val usage = ReasoningUsageParser.parseUsageObject(
            JSONObject(
                """
                    {
                      "input_tokens": "10",
                      "output_tokens": "5",
                      "cached_input_tokens": "7",
                      "reasoning_tokens": "2"
                    }
                """.trimIndent()
            )
        )

        assertEquals(10, usage?.inputTokens)
        assertEquals(7, usage?.cachedInputTokens)
        assertEquals(5, usage?.outputTokens)
        assertEquals(2, usage?.reasoningTokens)
        assertEquals(15, usage?.totalTokens)
    }

    @Test
    fun ledgerMathAccumulatesSuccessFailureAndTokenTotals() {
        val first = ReasoningUsageRecord(
            model = "cheap-model",
            wireFormat = "CHAT",
            phase = ReasoningUsageLedger.PHASE_INITIAL,
            maxOutputTokens = 1536,
            requestTokensEstimated = 100,
            responseTextTokensEstimated = 25,
            apiUsage = ReasoningApiUsage(
                inputTokens = 90,
                cachedInputTokens = 40,
                outputTokens = 20,
                reasoningTokens = 5,
                totalTokens = 110
            ),
            success = true,
            truncated = false,
            durationMillis = 10
        )
        val second = first.copy(
            phase = ReasoningUsageLedger.PHASE_CONTINUATION,
            requestTokensEstimated = 80,
            responseTextTokensEstimated = 0,
            apiUsage = null,
            success = false,
            truncated = true,
            errorCategory = "http_status"
        )

        val totals = ReasoningUsageLedgerMath.add(
            ReasoningUsageLedgerMath.add(ReasoningUsageTotals(), first),
            second
        )

        assertEquals(2L, totals.requestCount)
        assertEquals(1L, totals.successCount)
        assertEquals(1L, totals.failureCount)
        assertEquals(1L, totals.truncatedCount)
        assertEquals(1L, totals.continuationCount)
        assertEquals(180L, totals.estimatedRequestTokens)
        assertEquals(25L, totals.estimatedResponseTokens)
        assertEquals(90L, totals.apiInputTokens)
        assertEquals(40L, totals.apiCachedInputTokens)
        assertEquals(20L, totals.apiOutputTokens)
        assertEquals(5L, totals.apiReasoningTokens)
        assertEquals(110L, totals.apiTotalTokens)
    }
}
