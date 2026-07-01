package com.morimil.app.data.genesis

import android.content.Context
import org.json.JSONObject

class GenesisReader(private val context: Context) {
    fun readGenesisBlock(): Result<GenesisBlock> = runCatching {
        val rawJson = context.assets
            .open(GENESIS_ASSET)
            .bufferedReader()
            .use { it.readText() }

        val root = JSONObject(rawJson)
        val identity = root.getJSONObject("identity")
        val boundary = root.getJSONObject("mobile_boundary")

        GenesisBlock(
            schemaVersion = root.getString("schema_version"),
            sourceRepo = root.getString("source_repo"),
            sourceMode = root.getString("source_mode"),
            agentId = identity.getString("agent_id"),
            alias = identity.getString("alias"),
            role = identity.getString("role"),
            owner = identity.getString("owner"),
            riskTier = identity.getString("risk_tier"),
            allowedActions = root.getJSONArray("allowed_actions").toStringList(),
            blockedActions = root.getJSONArray("blocked_actions").toStringList(),
            localMemory = boundary.getBoolean("local_memory"),
            voicePushToTalk = boundary.getBoolean("voice_push_to_talk"),
            genesisReader = boundary.getBoolean("genesis_reader"),
            githubSync = boundary.getBoolean("github_sync"),
            pcExecution = boundary.getBoolean("pc_execution"),
            productionRelease = boundary.getBoolean("production_release"),
            currentAppPhase = root.getString("current_app_phase")
        )
    }

    private fun org.json.JSONArray.toStringList(): List<String> {
        return List(length()) { index -> getString(index) }
    }

    companion object {
        private const val GENESIS_ASSET = "morimil_genesis_block.json"
    }
}
