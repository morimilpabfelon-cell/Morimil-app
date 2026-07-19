package com.morimil.app.net

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Reads an HTTP body without allowing a peer to allocate unbounded app memory. */
internal object BoundedHttpBodyReader {
    fun read(
        stream: InputStream?,
        declaredLength: Long,
        maxBytes: Int
    ): String {
        if (stream == null) return ""
        require(maxBytes > 0) { "maxBytes debe ser positivo." }
        require(declaredLength < 0L || declaredLength <= maxBytes.toLong()) {
            "La respuesta HTTP excede el limite permitido de $maxBytes bytes."
        }

        val initialCapacity = when {
            declaredLength in 0..maxBytes.toLong() -> declaredLength.toInt()
            else -> minOf(DEFAULT_INITIAL_CAPACITY, maxBytes)
        }
        val output = ByteArrayOutputStream(initialCapacity)
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0

        stream.use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                require(total <= maxBytes) {
                    "La respuesta HTTP excede el limite permitido de $maxBytes bytes."
                }
                output.write(buffer, 0, read)
            }
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private const val BUFFER_SIZE = 8 * 1024
    private const val DEFAULT_INITIAL_CAPACITY = 16 * 1024
}
