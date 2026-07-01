package com.morimil.app.data.genesis

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Reads the Genesis identity contract. Tries a live GitHub read first (the
 * real source of truth); only falls back to the bundled local snapshot if
 * the network read fails (e.g. first install offline, no connectivity).
 *
 * This does real network I/O -- always call readGenesisIdentity() from a
 * coroutine, never from the main thread directly.
 */
class GenesisReader(private val context: Context) {

    suspend fun readGenesisIdentity(): Result<GenesisIdentitySource> = withContext(Dispatchers.IO) {
        runCatching {
            val remote = runCatching { fetchRemote() }
            if (remote.isSuccess) {
                GenesisIdentitySource(identity = remote.getOrThrow(), origin = GenesisOrigin.GITHUB_LIVE)
            } else {
                GenesisIdentitySource(identity = readBundled(), origin = GenesisOrigin.BUNDLED_FALLBACK)
            }
        }
    }

    /**
     * Fetches the full doctrine text referenced by the identity's own
     * doctrine_ref field (e.g. "doctrine/doctrine.md"), built dynamically so
     * this never goes stale if the ref ever changes. No bundled fallback: a
     * guessed or stale doctrine copy is worse than none, so a failed fetch
     * here is reported as failure and the caller degrades gracefully rather
     * than risking incorrect doctrine text in a system prompt.
     */
    suspend fun readDoctrineText(doctrineRef: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("$GENESIS_RAW_BASE/$doctrineRef")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }
            try {
                val statusCode = connection.responseCode
                require(statusCode in 200..299) { "Doctrine fetch failed: HTTP $statusCode" }
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun fetchRemote(): GenesisIdentity {
        val url = URL(GENESIS_RAW_URL)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
        }
        try {
            val statusCode = connection.responseCode
            require(statusCode in 200..299) { "Genesis fetch failed: HTTP $statusCode" }
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            return parseIdentity(JSONObject(text))
        } finally {
            connection.disconnect()
        }
    }

    private fun readBundled(): GenesisIdentity {
        val rawJson = context.assets
            .open(GENESIS_ASSET)
            .bufferedReader()
            .use { it.readText() }
        return parseIdentity(JSONObject(rawJson))
    }

    private fun parseIdentity(root: JSONObject): GenesisIdentity {
        return GenesisIdentity(
            schemaVersion = root.getString("schema_version"),
            agentId = root.getString("agent_id"),
            alias = root.getString("alias"),
            role = root.getString("role"),
            owner = root.getString("owner"),
            riskTier = root.getString("risk_tier"),
            allowedActions = root.getJSONArray("allowed_actions").toStringList(),
            disallowedActions = root.getJSONArray("disallowed_actions").toStringList(),
            doctrineRef = root.getString("doctrine_ref"),
            policyRef = root.getString("policy_ref")
        )
    }

    private fun JSONArray.toStringList(): List<String> {
        return List(length()) { index -> getString(index) }
    }

    companion object {
        private const val GENESIS_ASSET = "morimil_genesis_identity.json"
        private const val GENESIS_RAW_BASE = "https://raw.githubusercontent.com/morimilpabfelon-cell/Morimil/main"
        private const val GENESIS_RAW_URL = "$GENESIS_RAW_BASE/identity/orchestrator.identity.json"
        private const val TIMEOUT_MS = 12_000
    }
}
