package com.morimil.app.ai

import android.content.Context
import org.json.JSONObject

data class ReasoningApiUsage(
    val inputTokens: Int? = null,
    val cachedInputTokens: Int? = null,
    val outputTokens: Int? = null,
    val reasoningTokens: Int? = null,
    val totalTokens: Int? = null
)

data class ReasoningUsageRecord(
    val model: String,
    val wireFormat: String,
    val phase: String,
    val maxOutputTokens: Int,
    val requestTokensEstimated: Int,
    val responseTextTokensEstimated: Int,
    val apiUsage: ReasoningApiUsage? = null,
    val success: Boolean,
    val truncated: Boolean = false,
    val finishReason: String? = null,
    val errorCategory: String? = null,
    val statusCode: Int? = null,
    val durationMillis: Long,
    val createdAtMillis: Long = System.currentTimeMillis()
)

data class ReasoningUsageTotals(
    val requestCount: Long = 0,
    val successCount: Long = 0,
    val failureCount: Long = 0,
    val truncatedCount: Long = 0,
    val continuationCount: Long = 0,
    val estimatedRequestTokens: Long = 0,
    val estimatedResponseTokens: Long = 0,
    val apiInputTokens: Long = 0,
    val apiCachedInputTokens: Long = 0,
    val apiOutputTokens: Long = 0,
    val apiReasoningTokens: Long = 0,
    val apiTotalTokens: Long = 0
)

data class ReasoningUsageSummary(
    val totals: ReasoningUsageTotals,
    val latestRecordJson: String? = null
)

object ReasoningUsageParser {
    fun parseApiUsage(responseBody: String): ReasoningApiUsage? {
        return runCatching {
            val root = JSONObject(responseBody)
            val usage = root.optJSONObject("usage") ?: root.takeIf { objectLooksLikeUsage(it) } ?: return@runCatching null
            parseUsageObject(usage)
        }.getOrNull()
    }

    fun parseUsageObject(usage: JSONObject): ReasoningApiUsage? {
        val inputTokens = usage.intOrNull("input_tokens")
            ?: usage.intOrNull("prompt_tokens")
        val outputTokens = usage.intOrNull("output_tokens")
            ?: usage.intOrNull("completion_tokens")
        val cachedInputTokens = usage.intOrNull("cached_input_tokens")
            ?: usage.optJSONObject("input_tokens_details")?.intOrNull("cached_tokens")
            ?: usage.optJSONObject("prompt_tokens_details")?.intOrNull("cached_tokens")
            ?: usage.intOrNull("cache_read_input_tokens")
        val reasoningTokens = usage.intOrNull("reasoning_tokens")
            ?: usage.optJSONObject("output_tokens_details")?.intOrNull("reasoning_tokens")
            ?: usage.optJSONObject("completion_tokens_details")?.intOrNull("reasoning_tokens")
        val totalTokens = usage.intOrNull("total_tokens")
            ?: listOfNotNull(inputTokens, outputTokens).takeIf { it.isNotEmpty() }?.sum()

        if (listOf(inputTokens, cachedInputTokens, outputTokens, reasoningTokens, totalTokens).all { it == null }) {
            return null
        }
        return ReasoningApiUsage(
            inputTokens = inputTokens,
            cachedInputTokens = cachedInputTokens,
            outputTokens = outputTokens,
            reasoningTokens = reasoningTokens,
            totalTokens = totalTokens
        )
    }

    private fun objectLooksLikeUsage(root: JSONObject): Boolean {
        return root.has("input_tokens") || root.has("prompt_tokens") || root.has("output_tokens") ||
            root.has("completion_tokens") || root.has("total_tokens")
    }

    private fun JSONObject.intOrNull(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        val value = opt(key)
        return when (value) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }
    }
}

object ReasoningUsageLedgerMath {
    fun add(totals: ReasoningUsageTotals, record: ReasoningUsageRecord): ReasoningUsageTotals {
        val api = record.apiUsage
        return totals.copy(
            requestCount = totals.requestCount + 1,
            successCount = totals.successCount + if (record.success) 1 else 0,
            failureCount = totals.failureCount + if (!record.success) 1 else 0,
            truncatedCount = totals.truncatedCount + if (record.truncated) 1 else 0,
            continuationCount = totals.continuationCount + if (record.phase == ReasoningUsageLedger.PHASE_CONTINUATION) 1 else 0,
            estimatedRequestTokens = totals.estimatedRequestTokens + record.requestTokensEstimated,
            estimatedResponseTokens = totals.estimatedResponseTokens + record.responseTextTokensEstimated,
            apiInputTokens = totals.apiInputTokens + (api?.inputTokens ?: 0),
            apiCachedInputTokens = totals.apiCachedInputTokens + (api?.cachedInputTokens ?: 0),
            apiOutputTokens = totals.apiOutputTokens + (api?.outputTokens ?: 0),
            apiReasoningTokens = totals.apiReasoningTokens + (api?.reasoningTokens ?: 0),
            apiTotalTokens = totals.apiTotalTokens + (api?.totalTokens ?: 0)
        )
    }
}

class ReasoningUsageLedger(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun record(record: ReasoningUsageRecord) {
        runCatching {
            val nextTotals = ReasoningUsageLedgerMath.add(summary().totals, record)
            preferences.edit()
                .putLong(KEY_REQUEST_COUNT, nextTotals.requestCount)
                .putLong(KEY_SUCCESS_COUNT, nextTotals.successCount)
                .putLong(KEY_FAILURE_COUNT, nextTotals.failureCount)
                .putLong(KEY_TRUNCATED_COUNT, nextTotals.truncatedCount)
                .putLong(KEY_CONTINUATION_COUNT, nextTotals.continuationCount)
                .putLong(KEY_ESTIMATED_REQUEST_TOKENS, nextTotals.estimatedRequestTokens)
                .putLong(KEY_ESTIMATED_RESPONSE_TOKENS, nextTotals.estimatedResponseTokens)
                .putLong(KEY_API_INPUT_TOKENS, nextTotals.apiInputTokens)
                .putLong(KEY_API_CACHED_INPUT_TOKENS, nextTotals.apiCachedInputTokens)
                .putLong(KEY_API_OUTPUT_TOKENS, nextTotals.apiOutputTokens)
                .putLong(KEY_API_REASONING_TOKENS, nextTotals.apiReasoningTokens)
                .putLong(KEY_API_TOTAL_TOKENS, nextTotals.apiTotalTokens)
                .putString(KEY_LAST_RECORD_JSON, record.toJson().toString())
                .apply()
        }
    }

    fun summary(): ReasoningUsageSummary {
        return ReasoningUsageSummary(
            totals = ReasoningUsageTotals(
                requestCount = preferences.getLong(KEY_REQUEST_COUNT, 0),
                successCount = preferences.getLong(KEY_SUCCESS_COUNT, 0),
                failureCount = preferences.getLong(KEY_FAILURE_COUNT, 0),
                truncatedCount = preferences.getLong(KEY_TRUNCATED_COUNT, 0),
                continuationCount = preferences.getLong(KEY_CONTINUATION_COUNT, 0),
                estimatedRequestTokens = preferences.getLong(KEY_ESTIMATED_REQUEST_TOKENS, 0),
                estimatedResponseTokens = preferences.getLong(KEY_ESTIMATED_RESPONSE_TOKENS, 0),
                apiInputTokens = preferences.getLong(KEY_API_INPUT_TOKENS, 0),
                apiCachedInputTokens = preferences.getLong(KEY_API_CACHED_INPUT_TOKENS, 0),
                apiOutputTokens = preferences.getLong(KEY_API_OUTPUT_TOKENS, 0),
                apiReasoningTokens = preferences.getLong(KEY_API_REASONING_TOKENS, 0),
                apiTotalTokens = preferences.getLong(KEY_API_TOTAL_TOKENS, 0)
            ),
            latestRecordJson = preferences.getString(KEY_LAST_RECORD_JSON, null)
        )
    }

    private fun ReasoningUsageRecord.toJson(): JSONObject {
        val usage = apiUsage
        return JSONObject()
            .put("model", model)
            .put("wire_format", wireFormat)
            .put("phase", phase)
            .put("max_output_tokens", maxOutputTokens)
            .put("request_tokens_estimated", requestTokensEstimated)
            .put("response_text_tokens_estimated", responseTextTokensEstimated)
            .put("api_input_tokens", usage?.inputTokens)
            .put("api_cached_input_tokens", usage?.cachedInputTokens)
            .put("api_output_tokens", usage?.outputTokens)
            .put("api_reasoning_tokens", usage?.reasoningTokens)
            .put("api_total_tokens", usage?.totalTokens)
            .put("success", success)
            .put("truncated", truncated)
            .put("finish_reason", finishReason)
            .put("error_category", errorCategory)
            .put("status_code", statusCode)
            .put("duration_millis", durationMillis)
            .put("created_at_millis", createdAtMillis)
    }

    companion object {
        const val PHASE_INITIAL = "initial"
        const val PHASE_CONTINUATION = "continuation"

        private const val PREFERENCES_NAME = "morimil_reasoning_usage_ledger"
        private const val KEY_REQUEST_COUNT = "request_count"
        private const val KEY_SUCCESS_COUNT = "success_count"
        private const val KEY_FAILURE_COUNT = "failure_count"
        private const val KEY_TRUNCATED_COUNT = "truncated_count"
        private const val KEY_CONTINUATION_COUNT = "continuation_count"
        private const val KEY_ESTIMATED_REQUEST_TOKENS = "estimated_request_tokens"
        private const val KEY_ESTIMATED_RESPONSE_TOKENS = "estimated_response_tokens"
        private const val KEY_API_INPUT_TOKENS = "api_input_tokens"
        private const val KEY_API_CACHED_INPUT_TOKENS = "api_cached_input_tokens"
        private const val KEY_API_OUTPUT_TOKENS = "api_output_tokens"
        private const val KEY_API_REASONING_TOKENS = "api_reasoning_tokens"
        private const val KEY_API_TOTAL_TOKENS = "api_total_tokens"
        private const val KEY_LAST_RECORD_JSON = "last_record_json"
    }
}
