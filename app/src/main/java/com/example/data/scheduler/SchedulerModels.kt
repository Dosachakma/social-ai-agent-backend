package com.example.data.scheduler

import com.example.data.model.AgentError

enum class SchedulingErrorType {
    TRANSIENT_ERROR,
    PERMANENT_ERROR,
    APPROVAL_REQUIRED,
    ALREADY_COMPLETED,
    POST_NOT_FOUND,
    CANCELLED
}

data class RetryPolicy(
    val maxAttempts: Int = 3,
    val initialBackoffMs: Long = 1000L,
    val backoffMultiplier: Double = 2.0
) {
    fun isPermanentError(message: String?, code: String? = null): Boolean {
        if (message == null) return false
        val lowerMsg = message.lowercase()
        val lowerCode = code?.lowercase() ?: ""
        return lowerMsg.contains("auth") ||
               lowerMsg.contains("permission") ||
               lowerMsg.contains("invalid content") ||
               lowerMsg.contains("missing account") ||
               lowerMsg.contains("forbidden") ||
               lowerMsg.contains("unauthorized") ||
               lowerCode.contains("permanent") ||
               lowerCode.contains("permission_denied")
    }

    fun classifyError(message: String?, code: String? = null): SchedulingErrorType {
        return if (isPermanentError(message, code)) {
            SchedulingErrorType.PERMANENT_ERROR
        } else {
            SchedulingErrorType.TRANSIENT_ERROR
        }
    }

    fun calculateDelayMs(attempt: Int): Long {
        var delay = initialBackoffMs
        for (i in 1 until attempt) {
            delay = (delay * backoffMultiplier).toLong()
        }
        return delay
    }
}
