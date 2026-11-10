package com.example.pleoschallenge

enum class ProbeStatus {
    CHECKING,
    SUPPORTED,
    PARTIAL,
    UNAVAILABLE,
    ERROR,
}

data class ProbeResult(
    val id: String,
    val title: String,
    val status: ProbeStatus,
    val detail: String,
)

data class SignalEvent(
    val sequence: Long,
    val timestampMillis: Long,
    val signalId: String,
    val title: String,
    val value: String,
    val detail: String,
    val source: String,
)
