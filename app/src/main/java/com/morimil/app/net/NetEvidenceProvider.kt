package com.morimil.app.net

class NetEvidenceProvider {
    fun build(message: String): String {
        return NetUrlExtractor.extract(message).joinToString("\n")
    }
}
