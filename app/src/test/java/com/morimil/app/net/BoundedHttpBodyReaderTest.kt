package com.morimil.app.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class BoundedHttpBodyReaderTest {
    @Test
    fun readsBodyWithinLimit() {
        val body = "respuesta temporal"

        val result = BoundedHttpBodyReader.read(
            stream = ByteArrayInputStream(body.toByteArray(Charsets.UTF_8)),
            declaredLength = body.toByteArray(Charsets.UTF_8).size.toLong(),
            maxBytes = 128
        )

        assertEquals(body, result)
    }

    @Test
    fun rejectsDeclaredBodyLargerThanLimit() {
        val result = runCatching {
            BoundedHttpBodyReader.read(
                stream = ByteArrayInputStream(byteArrayOf()),
                declaredLength = 129,
                maxBytes = 128
            )
        }

        assertTrue(result.isFailure)
    }

    @Test
    fun rejectsStreamThatExceedsLimitWithoutDeclaredLength() {
        val result = runCatching {
            BoundedHttpBodyReader.read(
                stream = ByteArrayInputStream(ByteArray(129)),
                declaredLength = -1,
                maxBytes = 128
            )
        }

        assertTrue(result.isFailure)
    }
}
