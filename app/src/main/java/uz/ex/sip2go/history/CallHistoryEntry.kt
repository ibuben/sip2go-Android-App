package uz.ex.sip2go.history

enum class CallDirection {
    INCOMING,
    OUTGOING,
}

enum class CallStatus {
    COMPLETED,
    MISSED,
    REJECTED,
    BUSY,
    FAILED,
    CANCELLED,
}

data class CallHistoryEntry(
    val id: String,
    val number: String,
    val localEndpoint: String,
    val contactName: String? = null,
    val contactId: Long? = null,
    val direction: CallDirection,
    val status: CallStatus,
    val startedAt: Long,
    val durationSec: Int = 0,
    val recordingPath: String? = null,
    val recordingDurationSec: Int = 0,
)
