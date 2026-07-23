package com.morimil.app.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeWebDocumentHardenerTest {
    @Test
    fun stripsActiveNetworkCapableMarkupAndInjectsRestrictivePolicy() {
        val hardened = SafeWebDocumentHardener.hardenHtml(
            """
                <html><head>
                  <base href="https://private.example/">
                  <meta http-equiv="refresh" content="0;url=https://127.0.0.1/">
                  <script>fetch('https://127.0.0.1/')</script>
                </head><body onload="alert(1)"><h1>Public text</h1></body></html>
            """.trimIndent()
        )

        assertTrue(hardened.contains("Content-Security-Policy"))
        assertTrue(hardened.contains("connect-src 'none'"))
        assertTrue(hardened.contains("Public text"))
        assertFalse(hardened.contains("http-equiv=\"refresh\"", ignoreCase = true))
        assertFalse(hardened.contains("<base", ignoreCase = true))
        assertFalse(hardened.contains("<script", ignoreCase = true))
        assertFalse(hardened.contains("onload=", ignoreCase = true))
    }

    @Test
    fun plainTextIsEscapedBeforeRendering() {
        val wrapped = SafeWebDocumentHardener.wrapPlainText("<script>bad()</script> & text")

        assertTrue(wrapped.contains("&lt;script&gt;bad()&lt;/script&gt;"))
        assertTrue(wrapped.contains("&amp; text"))
        assertFalse(wrapped.contains("<script>bad()"))
    }
}
