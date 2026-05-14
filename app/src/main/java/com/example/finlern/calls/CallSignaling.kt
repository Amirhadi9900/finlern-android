package com.example.finlern.calls

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

/**
 * Firestore-backed signaling. Each helper here maps directly onto a piece
 * of the WebRTC handshake.
 *
 * The flow is:
 *   1. Caller writes calls/{callId} with offer.
 *   2. Receiver picks up, writes answer to the same doc.
 *   3. Both sides stream ICE candidates into the sub-collections.
 *   4. Both sides listen to the *other* side's candidates and the call doc
 *      status. Status -> "ended"/"rejected" closes the connection.
 */
class CallSignaling(private val callId: String) {

    private val db = FirebaseFirestore.getInstance()
    private val callDoc = db.collection(CALLS).document(callId)
    private val callerCandidatesRef = callDoc.collection(CALLER_CANDIDATES)
    private val receiverCandidatesRef = callDoc.collection(RECEIVER_CANDIDATES)

    suspend fun createCallDoc(
        callerId: String,
        receiverId: String,
        type: CallType
    ) {
        val data = mapOf(
            "callId" to callId,
            "callerId" to callerId,
            "receiverId" to receiverId,
            "type" to type.raw,
            "status" to CallStatus.RINGING.raw,
            "createdAt" to System.currentTimeMillis()
        )
        callDoc.set(data).await()
        Log.d(TAG, "Call doc $callId created (status=ringing)")
    }

    suspend fun writeOffer(sdp: SessionDescription) {
        callDoc.update(
            mapOf(
                "offer" to mapOf("type" to sdp.type.canonicalForm(), "sdp" to sdp.description)
            )
        ).await()
        Log.d(TAG, "Offer SDP written for $callId")
    }

    suspend fun writeAnswer(sdp: SessionDescription) {
        callDoc.update(
            mapOf(
                "answer" to mapOf("type" to sdp.type.canonicalForm(), "sdp" to sdp.description),
                "status" to CallStatus.ACTIVE.raw,
                "acceptedAt" to System.currentTimeMillis()
            )
        ).await()
        Log.d(TAG, "Answer SDP written for $callId, status->active")
    }

    suspend fun writeCallerCandidate(candidate: IceCandidate) {
        callerCandidatesRef.add(candidate.toMap()).await()
    }

    suspend fun writeReceiverCandidate(candidate: IceCandidate) {
        receiverCandidatesRef.add(candidate.toMap()).await()
    }

    /**
     * Mark the call as terminated. Idempotent — both sides may call this.
     */
    suspend fun endCall(reason: CallStatus = CallStatus.ENDED) {
        try {
            // Don't overwrite a more meaningful terminal state. e.g. if the
            // receiver already wrote "rejected", we shouldn't downgrade to
            // "ended" when our timeout/cancel fires a millisecond later.
            val current = readCall()
            if (current?.status?.isTerminal == true) return
            callDoc.update(
                mapOf(
                    "status" to reason.raw,
                    "endedAt" to System.currentTimeMillis()
                )
            ).await()
            Log.d(TAG, "Call $callId ended (reason=${reason.raw})")
        } catch (e: Exception) {
            Log.w(TAG, "endCall failed for $callId", e)
        }
    }

    suspend fun readCall(): CallRecord? = try {
        callDoc.get().await().toCallRecord()
    } catch (e: Exception) {
        Log.e(TAG, "readCall failed for $callId", e)
        null
    }

    /** Stream updates to the call document. */
    fun observeCall(): Flow<CallEvent> = callbackFlow {
        val registration: ListenerRegistration = callDoc.addSnapshotListener { snap, error ->
            if (error != null) {
                Log.e(TAG, "observeCall error for $callId", error)
                trySend(CallEvent.Error(error.message ?: "unknown"))
                return@addSnapshotListener
            }
            if (snap == null || !snap.exists()) {
                trySend(CallEvent.Deleted)
                return@addSnapshotListener
            }
            val record = snap.toCallRecord() ?: return@addSnapshotListener
            trySend(CallEvent.Update(record, snap.offerSdp(), snap.answerSdp()))
        }
        awaitClose { registration.remove() }
    }

    /** Stream the *other* side's ICE candidates as they arrive. */
    fun observeRemoteCandidates(weAreCaller: Boolean): Flow<IceCandidate> = callbackFlow {
        val source = if (weAreCaller) receiverCandidatesRef else callerCandidatesRef
        val registration = source.addSnapshotListener { snap, error ->
            if (error != null) {
                Log.e(TAG, "observeRemoteCandidates error for $callId", error)
                return@addSnapshotListener
            }
            snap?.documentChanges?.forEach { change ->
                if (change.type ==
                    com.google.firebase.firestore.DocumentChange.Type.ADDED
                ) {
                    change.document.toIceCandidate()?.let { trySend(it) }
                }
            }
        }
        awaitClose { registration.remove() }
    }

    sealed class CallEvent {
        data class Update(
            val record: CallRecord,
            val offerSdp: SessionDescription?,
            val answerSdp: SessionDescription?
        ) : CallEvent()
        data object Deleted : CallEvent()
        data class Error(val message: String) : CallEvent()
    }

    companion object {
        private const val TAG = "CallSignaling"
        const val CALLS = "calls"
        const val CALLER_CANDIDATES = "callerCandidates"
        const val RECEIVER_CANDIDATES = "receiverCandidates"
    }
}

// ---------- Firestore <-> WebRTC conversion helpers ------------------------

private fun IceCandidate.toMap(): Map<String, Any> = mapOf(
    "sdpMid" to sdpMid,
    "sdpMLineIndex" to sdpMLineIndex,
    "sdp" to sdp
)

private fun DocumentSnapshot.toIceCandidate(): IceCandidate? {
    val sdpMid = getString("sdpMid") ?: return null
    val sdpMLineIndex = getLong("sdpMLineIndex")?.toInt() ?: return null
    val sdp = getString("sdp") ?: return null
    return IceCandidate(sdpMid, sdpMLineIndex, sdp)
}

private fun DocumentSnapshot.toCallRecord(): CallRecord? {
    val callId = getString("callId") ?: id
    val callerId = getString("callerId") ?: return null
    val receiverId = getString("receiverId") ?: return null
    val type = CallType.fromRaw(getString("type"))
    val status = CallStatus.fromRaw(getString("status"))
    val createdAt = getLong("createdAt") ?: 0L
    val acceptedAt = getLong("acceptedAt")
    val endedAt = getLong("endedAt")
    return CallRecord(
        callId = callId,
        callerId = callerId,
        receiverId = receiverId,
        type = type,
        status = status,
        createdAt = createdAt,
        acceptedAt = acceptedAt,
        endedAt = endedAt
    )
}

@Suppress("UNCHECKED_CAST")
private fun DocumentSnapshot.offerSdp(): SessionDescription? {
    val offer = get("offer") as? Map<String, Any> ?: return null
    val sdp = offer["sdp"] as? String ?: return null
    return SessionDescription(SessionDescription.Type.OFFER, sdp)
}

@Suppress("UNCHECKED_CAST")
private fun DocumentSnapshot.answerSdp(): SessionDescription? {
    val answer = get("answer") as? Map<String, Any> ?: return null
    val sdp = answer["sdp"] as? String ?: return null
    return SessionDescription(SessionDescription.Type.ANSWER, sdp)
}
