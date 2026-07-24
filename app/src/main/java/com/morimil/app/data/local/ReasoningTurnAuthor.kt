package com.morimil.app.data.local

object ReasoningTurnAuthor {
    const val USER = "user"
    const val MORIMIL = "morimil"
    const val AUXILIARY_ADVISORY = "auxiliary_advisory"

    fun isTrustedConversationAuthor(author: String): Boolean {
        return author == USER || author == MORIMIL
    }
}
