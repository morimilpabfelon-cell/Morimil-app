package com.morimil.app.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetTextExtractorTest {
    @Test
    fun removesMarkupBeforePromptContext() {
        val raw = "<html><head><style>.x{}</style></head><body><h1>Titulo</h1><script>bad()</script><p>Texto util &amp; limpio</p></body></html>"
        val text = NetTextExtractor.readable(raw)

        assertTrue(text.contains("Titulo"))
        assertTrue(text.contains("Texto util & limpio"))
        assertFalse(text.contains("script"))
        assertFalse(text.contains("style"))
        assertFalse(text.contains("<p>"))
    }
}
