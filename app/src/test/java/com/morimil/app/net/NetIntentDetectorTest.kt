package com.morimil.app.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetIntentDetectorTest {
    @Test
    fun detectsUrl() {
        val url = "h" + "ttps://example.com/docs"
        assertTrue(NetIntentDetector.shouldUseNet("revisa $url"))
    }

    @Test
    fun detectsLookupMarker() {
        assertTrue(NetIntentDetector.shouldUseNet("busca informacion actual"))
    }

    @Test
    fun ignoresPlainChat() {
        assertFalse(NetIntentDetector.shouldUseNet("hola como estas"))
    }
}
