package com.example.data.model

enum class SignalsExecutionStatus {
    NOT_RUN,
    RUNNING,
    SUCCESS,
    FAILED,
}

data class SignalsHistoryEntry(
    val id: String,
    val executedAt: String,
    val status: SignalsExecutionStatus,
    val exitCode: Int?,
    val durationMs: Long?,
    val output: String,
    val error: String,
)
