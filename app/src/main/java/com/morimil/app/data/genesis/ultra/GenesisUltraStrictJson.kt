package com.morimil.app.data.genesis.ultra

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.core.StreamReadFeature
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/**
 * Validates strict JSON syntax and duplicate keys before Android JSONObject
 * extracts fields. This keeps JVM tests and device behavior identical.
 */
object GenesisUltraStrictJson {
    private val factory = JsonFactory.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .build()

    fun parseObject(jsonText: String): JSONObject {
        try {
            factory.createParser(jsonText).use { parser ->
                require(parser.nextToken() == JsonToken.START_OBJECT) { "json_root_not_object" }
                var depth = 1
                while (depth > 0) {
                    when (parser.nextToken() ?: throw IllegalArgumentException("incomplete_json")) {
                        JsonToken.START_OBJECT,
                        JsonToken.START_ARRAY -> depth += 1

                        JsonToken.END_OBJECT,
                        JsonToken.END_ARRAY -> depth -= 1

                        else -> Unit
                    }
                }
                require(parser.nextToken() == null) { "multiple_json_roots" }
            }
        } catch (error: IOException) {
            throw IllegalArgumentException("invalid_strict_json", error)
        }

        return try {
            JSONObject(jsonText)
        } catch (error: JSONException) {
            throw IllegalArgumentException("invalid_strict_json", error)
        }
    }
}
