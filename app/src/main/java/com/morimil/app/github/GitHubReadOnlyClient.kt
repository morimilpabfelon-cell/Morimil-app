package com.morimil.app.github

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class GitHubReadOnlyClient {
    fun getRepositoryStatus(
        owner: String,
        repo: String,
        token: String
    ): Result<GitHubRepoStatus> = runCatching {
        require(owner.matches(SAFE_NAME)) { "Invalid owner." }
        require(repo.matches(SAFE_NAME)) { "Invalid repo." }
        require(token.isNotBlank()) { "Missing GitHub token." }

        val url = URL("https://api.github.com/repos/$owner/$repo")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("Authorization", "Bearer $token")
        }

        try {
            val statusCode = connection.responseCode
            val response = if (statusCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IllegalStateException("GitHub read failed: HTTP $statusCode $errorBody")
            }

            val root = JSONObject(response)
            GitHubRepoStatus(
                fullName = root.getString("full_name"),
                privateRepo = root.getBoolean("private"),
                defaultBranch = root.getString("default_branch"),
                visibility = root.optString("visibility", "unknown"),
                htmlUrl = root.getString("html_url")
            )
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private val SAFE_NAME = Regex("^[A-Za-z0-9_.-]+$")
        private const val TIMEOUT_MS = 12_000
    }
}
