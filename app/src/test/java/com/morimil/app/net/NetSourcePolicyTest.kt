package com.morimil.app.net

import java.net.InetAddress
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NetSourcePolicyTest {
    @Test
    fun rejectsNonHttpsAndCredentialedUrls() {
        assertFalse(NetSourcePolicy.validateUrl("http://example.com").allowed)
        assertFalse(NetSourcePolicy.validateUrl("https://user:pass@example.com").allowed)
        assertTrue(NetSourcePolicy.validateUrl("https://example.com/path").allowed)
    }

    @Test
    fun rejectsIpv4PrivateReservedAndDocumentationRanges() {
        listOf(
            "0.0.0.0",
            "10.1.2.3",
            "100.64.0.1",
            "127.0.0.1",
            "169.254.1.1",
            "172.16.0.1",
            "172.31.255.255",
            "192.0.0.1",
            "192.0.2.1",
            "192.88.99.1",
            "192.168.1.1",
            "198.18.0.1",
            "198.51.100.1",
            "203.0.113.1",
            "224.0.0.1",
            "255.255.255.255"
        ).forEach { host ->
            assertFalse("Expected $host to be denied", NetSourcePolicy.validateHost(host).allowed)
        }
        assertTrue(NetSourcePolicy.validateHost("8.8.8.8").allowed)
    }

    @Test
    fun rejectsIpv6LocalDocumentationAndTransitionRanges() {
        listOf(
            "::",
            "::1",
            "fe80::1",
            "fc00::1",
            "fd12:3456::1",
            "ff02::1",
            "2001:db8::1",
            "2001:0000::1",
            "2002:7f00:1::"
        ).forEach { host ->
            assertFalse("Expected $host to be denied", NetSourcePolicy.validateHost(host).allowed)
        }
        assertTrue(NetSourcePolicy.validateHost("2606:4700:4700::1111").allowed)
    }

    @Test
    fun rejectsMixedDnsAnswerIfAnyAddressIsNonPublic() {
        val decision = NetSourcePolicy.validateResolvedHost("example.com") {
            listOf(
                InetAddress.getByName("93.184.216.34"),
                InetAddress.getByName("127.0.0.1")
            )
        }

        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("loopback"))
    }

    @Test
    fun publicOnlyDnsReturnsTheExactValidatedAnswer() {
        val expected = listOf(InetAddress.getByName("93.184.216.34"))
        val dns = PublicOnlyDns { expected }

        assertEquals(expected, dns.lookup("example.com"))
    }

    @Test
    fun publicOnlyDnsRejectsAReboundPrivateAnswer() {
        var lookupCount = 0
        val dns = PublicOnlyDns {
            lookupCount += 1
            if (lookupCount == 1) {
                listOf(InetAddress.getByName("93.184.216.34"))
            } else {
                listOf(InetAddress.getByName("192.168.1.10"))
            }
        }

        assertEquals("93.184.216.34", dns.lookup("example.com").single().hostAddress)
        assertThrows(UnknownHostException::class.java) {
            dns.lookup("example.com")
        }
    }
}
