package com.morimil.app.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReasoningEndpointPolicyTest {
    @Test
    fun exactUsbAndEmulatorLoopbackEndpointsAreTrusted() {
        assertTrue(ReasoningEndpointPolicy.isLocalTrustedEndpoint("http://127.0.0.1:11434/v1/chat/completions"))
        assertTrue(ReasoningEndpointPolicy.isLocalTrustedEndpoint("http://localhost:11434/v1/chat/completions"))
        assertTrue(ReasoningEndpointPolicy.isLocalTrustedEndpoint("http://[::1]:11434/v1/chat/completions"))
        assertTrue(ReasoningEndpointPolicy.isLocalTrustedEndpoint("http://10.0.2.2:11434/v1/chat/completions"))
    }

    @Test
    fun privateLanAddressesAreNotPartOfUsbTrustBoundary() {
        assertFalse(ReasoningEndpointPolicy.isLocalTrustedEndpoint("http://192.168.1.28:11434/v1/chat/completions"))
        assertFalse(ReasoningEndpointPolicy.isLocalTrustedEndpoint("http://10.1.2.3:11434/v1/chat/completions"))
        assertFalse(ReasoningEndpointPolicy.isLocalTrustedEndpoint("http://172.16.1.3:11434/v1/chat/completions"))
    }

    @Test
    fun ipLookingPublicHostnamesAreNeverTrusted() {
        assertFalse(ReasoningEndpointPolicy.isLocalTrustedEndpoint("https://192.168.evil.example/v1/chat/completions"))
        assertFalse(ReasoningEndpointPolicy.isLocalTrustedEndpoint("https://10.1.evil.example/v1/chat/completions"))
        assertFalse(ReasoningEndpointPolicy.isLocalTrustedEndpoint("https://127.0.0.1.evil.example/v1/chat/completions"))
    }

    @Test
    fun remoteTemporaryProvidersRequireHttps() {
        val insecure = ReasoningProviderConfig(
            preset = ReasoningPreset.CHAT_COMPATIBLE,
            baseUrl = "http://api.example.com/v1/chat/completions",
            model = "reasoning-model"
        )
        val secure = insecure.copy(baseUrl = "https://api.example.com/v1/chat/completions")

        assertTrue(runCatching { insecure.validated() }.isFailure)
        assertTrue(runCatching { secure.validated() }.isSuccess)
        assertTrue(secure.requiresRuntimeKey)
    }

    @Test
    fun usbPresetCannotBeRedirectedToARemoteProvider() {
        val config = ReasoningProviderConfig(
            preset = ReasoningPreset.LOCAL_USB_HELPER,
            baseUrl = "https://api.example.com/v1/chat/completions",
            model = "reasoning-model"
        )

        assertTrue(runCatching { config.validated() }.isFailure)
    }
}
