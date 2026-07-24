package com.morimil.app.ai

import com.morimil.app.net.NetAddressResolver
import com.morimil.app.net.PublicOnlyDns
import java.net.InetAddress
import java.net.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReasoningTransportSecurityTest {
    @Test
    fun remoteClientUsesTheValidatedAddressListForItsActualDns() {
        val publicAddress = InetAddress.getByName("93.184.216.34")
        val client = ReasoningTransportSecurity.clientFor(
            rawUrl = "https://api.example.com/v1/responses",
            resolver = NetAddressResolver { listOf(publicAddress) }
        )

        assertTrue(client.dns is PublicOnlyDns)
        assertEquals(listOf(publicAddress), client.dns.lookup("api.example.com"))
        assertEquals(Proxy.NO_PROXY, client.proxy)
        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
        assertFalse(client.retryOnConnectionFailure)
    }

    @Test
    fun remoteClientRejectsPrivateOrReboundDnsInsideConnectionDns() {
        val client = ReasoningTransportSecurity.clientFor(
            rawUrl = "https://api.example.com/v1/responses",
            resolver = NetAddressResolver {
                listOf(InetAddress.getByName("192.168.1.20"))
            }
        )

        val result = runCatching { client.dns.lookup("api.example.com") }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("site_local_denied") == true)
    }

    @Test
    fun localUsbClientDoesNotApplyRemotePublicDnsPolicy() {
        val client = ReasoningTransportSecurity.clientFor(
            rawUrl = "http://127.0.0.1:11434/v1/chat/completions",
            resolver = NetAddressResolver {
                error("local resolver must not be installed")
            }
        )

        assertFalse(client.dns is PublicOnlyDns)
        assertEquals(Proxy.NO_PROXY, client.proxy)
    }

    @Test
    fun cleartextRemoteEndpointIsRejected() {
        val result = runCatching {
            ReasoningTransportSecurity.validateTarget(
                "http://api.example.com/v1/chat/completions"
            )
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("reasoning_endpoint_not_allowed") == true)
    }

    @Test
    fun endpointFragmentsAreRejectedBeforeConnection() {
        val result = runCatching {
            ReasoningTransportSecurity.validateTarget(
                "https://api.example.com/v1/responses#secret"
            )
        }

        assertTrue(result.isFailure)
        assertEquals("reasoning_endpoint_fragment_denied", result.exceptionOrNull()?.message)
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
            rawUrl = "http://127.0.0.1:9/v1/chat/completions"
        )
        try {
            assertTrue(connection is OkHttpReasoningConnection)
            assertFalse(connection.instanceFollowRedirects)
            assertFalse(connection.usingProxy())
        } finally {
            connection.disconnect()
        }
    }
}
