package com.morimil.app.data.genesis.ultra

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.parseToJsonElement
import org.json.JSONException
import org.json.JSONObject

/**
 * Validates RFC-style JSON syntax before using Android's JSONObject accessors.
 * JSONObject remains useful for the existing field extraction code, but its
 * parser is intentionally permissive; Genesis protocol artifacts must not be.
 */
object GenesisUltraStrictJson {
    private val parser = Json {
        isLenient = false
        ignoreUnknownKeys = false
        allowTrailingComma = false
        allowSpecialFloatingPointValues = false
        explicitNulls = true
    }

    fun parseObject(jsonText: String): JSONObject {
        val element = try {
            parser.parseToJsonElement(jsonText)
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("invalid_strict_json", error)
        }
        require(element is JsonObject) { "json_root_not_object" }

        return try {
            // Parsing the original text a second time preserves JSON-java's
            // duplicate-key rejection at every nested object.
            JSONObject(jsonText)
        } catch (error: JSONException) {
            throw IllegalArgumentException("invalid_or_duplicate_json", error)
        }
    }
}
