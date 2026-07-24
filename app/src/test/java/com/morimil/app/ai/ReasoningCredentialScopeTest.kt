package com.morimil.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReasoningCredentialScopeTest {
    @Test
    fun pathsQueriesAndDefaultHttpsPortShareOneOriginScope() {
        val first = ReasoningCredentialScopePolicy.fromRemoteEndpoint(
            "https://API.Example.com/v1/responses?mode=deep"
        )
        val second = ReasoningCredentialScopePolicy.fromRemoteEndpoint(
            "https://api.example.com:443/v1/models"
        )

        assertEquals("https://api.example.com:443", first.canonicalOrigin)
        assertEquals(first, second)
    }

    @Test
    fun differentHostsCannotShareOneCredential() {
        val first = ReasoningCredentialScopePolicy.fromRemoteEndpoint(
            "https://api-a.example.com/v1/responses"
        )
        val second = ReasoningCredentialScopePolicy.fromRemoteEndpoint(
            "https://api-b.example.com/v1/responses"
        )

        assertNotEquals(first.storageId, second.storageId)
        assertNotEquals(first.canonicalOrigin, second.canonicalOrigin)
    }

    @Test
    fun differentPortsCannotShareOneCredential() {
        val defaultPort = ReasoningCredentialScopePolicy.fromRemoteEndpoint(
            "https://api.example.com/v1/responses"
        )
        val alternatePort = ReasoningCredentialScopePolicy.fromRemoteEndpoint(
            "https://api.example.com:8443/v1/responses"
        )

        assertNotEquals(defaultPort.storageId, alternatePort.storageId)
    }

    @Test
    fun cleartextRemoteAndLocalEndpointsCannotReceiveStoredApiKeys() {
        listOf(
            "http://api.example.com/v1/chat/completions",
            "http://127.0.0.1:11434/v1/chat/completions",
            "https://127.0.0.1:11434/v1/chat/completions"
        ).forEach { endpoint ->
            assertTrue(
                runCatching {
                    ReasoningCredentialScopePolicy.fromRemoteEndpoint(endpoint)
                }.isFailure
            )
        }
    }
}
