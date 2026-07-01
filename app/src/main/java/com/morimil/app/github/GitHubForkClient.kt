package com.morimil.app.github

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ForkResult(
    val forkOwner: String,
    val forkRepo: String,
    val htmlUrl: String
)

/**
 * Creates a fork of the Genesis repo under the token owner's own account.
 * This is the app's only GitHub write execution, and it is scoped as
 * tightly as the read-only and write-proposal clients:
 *   - The source (owner/repo being forked) is hardcoded. No caller can
 *     redirect this to fork anything else.
 *   - The destination account is derived FROM THE TOKEN via GET /user,
 *     never typed by the user, so there is no way to point a fork at an
 *     account that isn't the token's own.
 *   - Only one action exists: fork Genesis. Nothing else is forkable
 *     through this client.
 */
class GitHubForkClient {

    fun getAuthenticatedLogin(token: String): Result<String> = runCatching {
        require(token.isNotBlank()) { "Missing GitHub token." }

        val connection = openConnection("https://api.github.com/user", "GET", token)
        try {
            val statusCode = connection.responseCode
            val body = readBody(connection, statusCode)
            require(statusCode in 200..299) { "Could not read GitHub identity: HTTP $statusCode $body" }
            JSONObject(body).getString("login")
        } finally {
            connection.disconnect()
        }
    }

    fun forkGenesisRepo(token: String): Result<ForkResult> = runCatching {
        require(token.isNotBlank()) { "Missing GitHub token." }

        val login = getAuthenticatedLogin(token).getOrThrow()

        val connection = openConnection(
            "https://api.github.com/repos/$GENESIS_OWNER/$GENESIS_REPO/forks",
            "POST",
            token
        )
        try {
            val statusCode = connection.responseCode
            val body = readBody(connection, statusCode)
            // GitHub returns 202 Accepted for a new fork, or 200/202 again if
            // the fork already exists -- both are treated as success here.
            require(statusCode in 200..299) { "Fork request failed: HTTP $statusCode $body" }

            val root = JSONObject(body)
            ForkResult(
                forkOwner = root.optJSONObject("owner")?.optString("login") ?: login,
                forkRepo = root.optString("name", GENESIS_REPO),
                htmlUrl = root.optString("html_url", "https://github.com/$login/$GENESIS_REPO")
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String, method: String, token: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("Authorization", "Bearer $token")
            if (method == "POST") {
                doOutput = true
                setRequestProperty("Content-Length", "0")
            }
        }
    }

    private fun readBody(connection: HttpURLConnection, statusCode: Int): String {
        return if (statusCode in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        }
    }

    companion object {
        const val GENESIS_OWNER = "morimilpabfelon-cell"
        const val GENESIS_REPO = "Morimil"
        private const val TIMEOUT_MS = 15_000
    }
}
