package com.morimil.app.data.repository

object RecallSchedulePolicy {
    const val ONE_DAY_MILLIS: Long = 24L * 60L * 60L * 1000L

    private val schedulableKinds = setOf(
        "decision",
        "correction",
        "preference",
        "learning",
        "identity",
        "approval",
        "rejection",
        "error_detected",
        "rest_cycle"
    )

    fun shouldSchedule(
        memoryKind: String,
        importance: Int,
        confidence: Int,
        userConfirmed: Boolean
    ): Boolean {
        if (memoryKind == "chat_noise" || memoryKind == "conversation") return false
        if (memoryKind !in schedulableKinds) return false
        return userConfirmed || importance >= 70 || confidence >= 80
    }

    fun priority(
        memoryKind: String,
        importance: Int,
        confidence: Int,
        userConfirmed: Boolean
    ): Int {
        val confirmedBoost = if (userConfirmed) 18 else 0
        val kindBoost = when (memoryKind) {
            "identity", "decision", "correction" -> 10
            "approval", "rejection", "error_detected" -> 8
            "preference", "learning" -> 4
            else -> 0
        }
        return ((importance * 0.68f) + (confidence * 0.22f) + confirmedBoost + kindBoost)
            .toInt()
            .coerceIn(1, 100)
    }

    fun priority(
        importance: Int,
        confidence: Int,
        userConfirmed: Boolean
    ): Int {
        return priority(
            memoryKind = "",
            importance = importance,
            confidence = confidence,
            userConfirmed = userConfirmed
        )
    }

    fun initialIntervalDays(priority: Int): Int {
        return when {
            priority >= 90 -> 1
            priority >= 75 -> 2
            else -> 3
        }
    }

    fun nextIntervalDays(currentIntervalDays: Int): Int {
        return nextIntervalDays(currentIntervalDays, priority = 70)
    }

    fun nextIntervalDays(currentIntervalDays: Int, priority: Int): Int {
        val cap = if (priority >= 85) 21 else 30
        return when {
            currentIntervalDays <= 0 -> 1
            currentIntervalDays == 1 -> if (priority >= 85) 2 else 3
            currentIntervalDays <= 3 -> if (priority >= 85) 4 else 7
            currentIntervalDays < 14 -> currentIntervalDays + if (priority >= 85) 3 else 7
            else -> cap
        }.coerceAtMost(cap)
    }

    fun postponedIntervalDays(priority: Int): Int {
        return if (priority >= 85) 1 else 2
    }

    fun delayMillis(intervalDays: Int): Long {
        return intervalDays.coerceAtLeast(1) * ONE_DAY_MILLIS
    }
}
