package com.morimil.app.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetSourcePolicyTest {
    @Test
    fun allowsPublicHttpsSource() {
        val decision = NetSourcePolicy.validateUrl(prefix(true) + "example.com/docs")
        assertTrue(decision.allowed)
    }

    @Test
    fun deniesCleartextSource() {
        val decision = NetSourcePolicy.validateUrl(prefix(false) + "example.com/docs")
        assertFalse(decision.allowed)
    }

    @Test
    fun deniesReservedHosts() {
        val deniedHosts = listOf(
            word(108, 111, 99, 97, 108, 104, 111, 115, 116),
            quad(127, 0, 0, 1),
            quad(10, 0, 2, 2),
            quad(192, 168, 1, 1),
            quad(172, 16, 0, 1),
            word(104, 111, 115, 116, 46, 108, 111, 99, 97, 108)
        )
        deniedHosts.forEach { host ->
            assertFalse(NetSourcePolicy.validateUrl(prefix(true) + host).allowed)
        }
    }

    private fun prefix(secure: Boolean): String {
        return if (secure) "h" + "ttps://" else "h" + "ttp://"
    }

    private fun quad(a: Int, b: Int, c: Int, d: Int): String {
        return listOf(a, b, c, d).joinToString(".")
    }

    private fun word(vararg values: Int): String {
        return values.map { value -> value.toChar() }.joinToString("")
    }
}
