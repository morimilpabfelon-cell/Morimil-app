package com.morimil.app.ai

import com.morimil.app.net.NetSourceDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReasoningTransportSecurityTest {
    @Test
    fun localUsbEndpointSkipsPublicDnsValidation() {
        var resolverCalled = false
        val target = ReasoningTransportSecurity.validateTarget(
            rawUrl = "http://127.0.0.1:11434/v1/chat/completions",
            resolveHost = {
                resolverCalled = true
                NetSourceDecision(false, "loopback_denied")
            }
        )

        assertEquals("127.0.0.1", target.host)
        assertFalse(resolverCalled)
    }

    @Test
    fun remoteEndpointRequiresPublicDnsResolution() {
        var resolvedHost: String? = null
        val target = ReasoningTransportSecurity.validateTarget(
            rawUrl = "https://api.example.com/v1/responses",
            resolveHost = { host ->
                resolvedHost = host
                NetSourceDecision(true)
            }
        )

        assertEquals("api.example.com", resolvedHost)
        assertEquals("https", target.protocol)
    }

    @Test
    fun remoteEndpointRejectsPrivateOrReboundDns() {
        val result = runCatching {
            ReasoningTransportSecurity.validateTarget(
                rawUrl = "https://api.example.com/v1/responses",
                resolveHost = { NetSourceDecision(false, "site_local_denied") }
            )
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("site_local_denied") == true)
    }

    @Test
    fun cleartextRemoteEndpointIsRejectedBeforeResolution() {
        var resolverCalled = false
        val result = runCatching {
            ReasoningTransportSecurity.validateTarget(
                rawUrl = "http://api.example.com/v1/chat/completions",
                resolveHost = {
                    resolverCalled = true
                    NetSourceDecision(true)
                }
            )
        }

        assertTrue(result.isFailure)
        assertFalse(resolverCalled)
    }

    @Test
    fun everyHttpRedirectStatusIsDenied() {
        listOf(300, 301, 302, 303, 307, 308, 399).forEach { status ->
            assertTrue(
                runCatching {
                    ReasoningTransportSecurity.requireNoRedirect(status)
                }.isFailure
            )
        }
        listOf(200, 204, 400, 500).forEach { status ->
            assertTrue(
                runCatching {
                    ReasoningTransportSecurity.requireNoRedirect(status)
                }.isSuccess
            )
        }
    }

    @Test
    fun openedConnectionsNeverFollowRedirectsAutomatically() {
        val connection = ReasoningTransportSecurity.openConnection(
            rawUrl = "http://127.0.0.1:9/v1/chat/completions",
            resolveHost = { NetSourceDecision(false, "must_not_be_called") }
        )
        try {
            assertFalse(connection.instanceFollowRedirects)
        } finally {
            connection.disconnect()
        }
    }
}