package com.example.finlern.calls

/**
 * Data shapes for the Firestore-backed WebRTC call signaling channel.
 *
 * Document layout in Firestore:
 *
 *   calls/{callId}
 *     callerId       String   email of the user starting the call
 *     receiverId     String   email of the user being called
 *     type           String   "audio" | "video"
 *     status         String   "ringing" | "active" | "ended" | "rejected" | "missed"
 *     createdAt      Long     millis since epoch
 *     acceptedAt     Long?    set when receiver picks up
 *     endedAt        Long?    set when either side hangs up / cancels / rejects
 *     offer          Map?     { type: "offer", sdp: String }     written by caller
 *     answer         Map?     { type: "answer", sdp: String }    written by receiver
 *
 *   calls/{callId}/callerCandidates/{auto}     ICE from the caller side
 *   calls/{callId}/receiverCandidates/{auto}   ICE from the receiver side
 *     each doc: { sdpMid: String, sdpMLineIndex: Int, sdp: String }
 */

enum class CallType(val raw: String) {
    AUDIO("audio"),
    VIDEO("video");

    companion object {
        fun fromRaw(raw: String?): CallType = when (raw) {
            "video" -> VIDEO
            else -> AUDIO
        }
    }
}

enum class CallStatus(val raw: String) {
    RINGING("ringing"),
    ACTIVE("active"),
    ENDED("ended"),
    REJECTED("rejected"),
    MISSED("missed");

    val isTerminal: Boolean
        get() = when (this) {
            RINGING, ACTIVE -> false
            ENDED, REJECTED, MISSED -> true
        }

    companion object {
        fun fromRaw(raw: String?): CallStatus = when (raw) {
            "active" -> ACTIVE
            "ended" -> ENDED
            "rejected" -> REJECTED
            "missed" -> MISSED
            else -> RINGING
        }
    }
}

/** In-memory representation of a call doc; mirrors Firestore shape. */
data class CallRecord(
    val callId: String,
    val callerId: String,
    val receiverId: String,
    val type: CallType,
    val status: CallStatus,
    val createdAt: Long,
    val acceptedAt: Long? = null,
    val endedAt: Long? = null
) {
    /** Who is the "other" person from the current user's perspective. */
    fun otherParty(self: String): String = if (callerId == self) receiverId else callerId

    /** True when we are the caller, false when we are the receiver. */
    fun isOutgoing(self: String): Boolean = callerId == self
}
