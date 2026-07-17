package com.morimil.app.data.genesis

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Reads the bundled legacy Genesis material for migration analysis.
 *
 * The embedded bundle is non-authoritative and cannot be installed for a new
 * birth. A future Genesis Ultra adapter will replace the installation path.
 */
class GenesisReader(private val context: Context) {
    private val manifestVerifier = GenesisManifestVerifier(context)

    suspend fun readGenesisIdentity(): Result<GenesisIdentitySource> = withContext(Dispatchers.IO) {
        runCatching {
            val verification = manifestVerifier.verify()
            GenesisIdentitySource(
                identity = readBundled(),
                origin = GenesisOrigin.BUNDLED_SEED,
                manifest = verification
            )
        }
    }

    suspend fun readDoctrineText(doctrineRef: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching { readBundledText(doctrineRef) }
    }

    suspend fun readPolicyText(policyRef: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching { readBundledText(policyRef) }
    }

    suspend fun installGenesisBundle(): Result<GenesisInstalledBundle> = withContext(Dispatchers.IO) {
        runCatching {
            GenesisUltraIntegrationGate.requireBirthReady()

            val verification = manifestVerifier.verify()
            val manifest = JSONObject(readBundledText("genesis_manifest.json"))
            val files = manifest.getJSONArray("files")
            val stagingDir = File(context.filesDir, "genesis_staging")
            val finalDir = File(context.filesDir, "genesis")

            stagingDir.deleteRecursively()
            require(stagingDir.mkdirs()) { "Could not create Genesis staging directory." }

            copyBundledAsset("genesis_manifest.json", File(stagingDir, "genesis_manifest.json"))
            repeat(files.length()) { index ->
                val relativePath = files.getJSONObject(index).getString("path")
                copyBundledAsset(relativePath, File(stagingDir, relativePath))
            }

            finalDir.deleteRecursively()
            require(stagingDir.renameTo(finalDir)) { "Could not install Genesis bundle into private storage." }

            GenesisInstalledBundle(
                verification = verification,
                installPath = finalDir.absolutePath
            )
        }
    }

    suspend fun clearInstalledGenesisBundle(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            File(context.filesDir, "genesis_staging").deleteRecursively()
            File(context.filesDir, "genesis").deleteRecursively()
            Unit
        }
    }

    private fun readBundled(): GenesisIdentity {
        val rawJson = context.assets
            .open(GENESIS_ASSET)
            .bufferedReader()
            .use { it.readText() }
        return parseIdentity(JSONObject(rawJson))
    }

    private fun readBundledText(ref: String): String {
        val cleanRef = ref.trim().removePrefix("/")
        require(!cleanRef.contains("..")) { "Genesis bundle ref cannot escape assets/genesis." }
        return context.assets
            .open("$GENESIS_ROOT/$cleanRef")
            .bufferedReader()
            .use { it.readText() }
    }

    private fun copyBundledAsset(ref: String, target: File) {
        val cleanRef = ref.trim().removePrefix("/")
        require(!cleanRef.contains("..")) { "Genesis bundle ref cannot escape assets/genesis." }
        target.parentFile?.mkdirs()
        context.assets.open("$GENESIS_ROOT/$cleanRef").use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
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
        private const val GENESIS_ROOT = "genesis"
        private const val GENESIS_ASSET = "$GENESIS_ROOT/identity/orchestrator.identity.json"
    }
}
