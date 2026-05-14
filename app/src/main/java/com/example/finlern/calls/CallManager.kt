package com.example.finlern.calls

import android.content.Context
import android.util.Log
import com.example.finlern.data.FirebaseAuthManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The brains of voice/video calling.
 *
 * One active call at a time, exposed via [state]. The UI observes [state]
 * and updates accordingly. Background actors (e.g. the IncomingCallActivity)
 * call [acceptIncomingCall] / [rejectIncomingCall] / [hangup].
 *
 * The actual signaling lives in [CallSignaling]; this class wires the
 * PeerConnection events on one side to the signaling channel on the other.
 */
class CallManager private constructor(applicationContext: Context) {

    private val appContext = applicationContext.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // --- WebRTC singletons ------------------------------------------------
    private val eglBase: EglBase = EglBase.create()
    val eglBaseContext: EglBase.Context get() = eglBase.eglBaseContext

    private val factory: PeerConnectionFactory = run {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )
        PeerConnectionFactory.builder()
            .setVideoEncoderFactory(
                DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
            )
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .setAudioDeviceModule(JavaAudioDeviceModule.builder(appContext).createAudioDeviceModule())
            .createPeerConnectionFactory()
    }

    // --- Per-call state ---------------------------------------------------
    private val _state = MutableStateFlow<CallState>(CallState.Idle)
    val state: StateFlow<CallState> = _state.asStateFlow()

    private var signaling: CallSignaling? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var localVideoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var videoCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var observeJob: Job? = null
    private var candidatesJob: Job? = null
    private var ringTimeoutJob: Job? = null

    // Remote ICE candidates received before setRemoteDescription returns
    // must be queued; WebRTC throws if you call addIceCandidate() too
    // early. This list is touched from both the Firestore listener
    // (Dispatchers.Default) and WebRTC's signaling thread (inside the
    // setRemoteDescription callback), so guard mutations.
    private val pendingCandidatesLock = Any()
    private val pendingRemoteCandidates = mutableListOf<IceCandidate>()
    @Volatile private var remoteDescriptionApplied = false

    private var isFrontCamera = true

    /** True when [state] indicates we're free to start/accept another call. */
    private val isFree: Boolean
        get() = _state.value is CallState.Idle || _state.value is CallState.Ended

    // --- Public API -------------------------------------------------------

    /** Start an outgoing call. Returns the new callId. */
    suspend fun startCall(receiverId: String, type: CallType): String? {
        val self = FirebaseAuthManager.getCurrentUser()?.email
        if (self == null) {
            Log.e(TAG, "startCall: no signed-in user")
            return null
        }
        if (!isFree) {
            Log.w(TAG, "startCall ignored, already in a call: ${_state.value}")
            return null
        }
        val callId = UUID.randomUUID().toString()
        val sig = CallSignaling(callId).also { signaling = it }
        val record = CallRecord(
            callId = callId,
            callerId = self,
            receiverId = receiverId,
            type = type,
            status = CallStatus.RINGING,
            createdAt = System.currentTimeMillis()
        )
        _state.value = CallState.Outgoing(record, weAreCaller = true)

        var docCreated = false
        try {
            sig.createCallDoc(callerId = self, receiverId = receiverId, type = type)
            docCreated = true
            createPeerConnection(weAreCaller = true)
            attachLocalMedia(type)
            val offer = createOffer()
            peerConnection?.setLocalDescription(NoopSdpObserver, offer)
            sig.writeOffer(offer)
            startObserving(record, weAreCaller = true)
            startRingTimeout(sig)
        } catch (e: Exception) {
            Log.e(TAG, "startCall failed", e)
            // If we got as far as writing the call doc, mark it ended so
            // the receiver's app doesn't think it's still ringing.
            if (docCreated) {
                try { sig.endCall(CallStatus.ENDED) } catch (_: Exception) {}
            }
            tearDown(CallStatus.ENDED)
            return null
        }
        return callId
    }

    /**
     * Start a ring timeout. If the call is still in RINGING after [RING_TIMEOUT_MS]
     * we end it with status=MISSED so the caller's UI stops "Ringing…"
     * forever when the receiver never picks up.
     */
    private fun startRingTimeout(sig: CallSignaling) {
        ringTimeoutJob?.cancel()
        ringTimeoutJob = scope.launch {
            kotlinx.coroutines.delay(RING_TIMEOUT_MS)
            val current = _state.value
            if (current is CallState.Outgoing && current.record.status == CallStatus.RINGING) {
                Log.d(TAG, "Outgoing call timed out without answer")
                try { sig.endCall(CallStatus.MISSED) } catch (_: Exception) {}
                tearDown(CallStatus.MISSED)
            }
        }
    }

    /** Receiver accepts the incoming call. */
    suspend fun acceptIncomingCall(callId: String) {
        if (FirebaseAuthManager.getCurrentUser()?.email == null) {
            Log.e(TAG, "acceptIncomingCall: no signed-in user")
            return
        }
        // Allow accept only when we're already showing this incoming call
        // (set by prepareIncomingCall) or fresh idle. Any other state means
        // we're already in another call.
        val current = _state.value
        if (current !is CallState.Incoming &&
            current !is CallState.Idle &&
            current !is CallState.Ended
        ) {
            Log.w(TAG, "acceptIncomingCall ignored, busy in $current")
            return
        }
        val sig = CallSignaling(callId).also { signaling = it }
        val record = sig.readCall()
        if (record == null) {
            Log.e(TAG, "acceptIncomingCall: call $callId not found")
            _state.value = CallState.Idle
            return
        }
        if (record.status.isTerminal) {
            Log.w(TAG, "acceptIncomingCall: call $callId already ${record.status}")
            _state.value = CallState.Idle
            return
        }
        _state.value = CallState.Incoming(
            record,
            callerName = (current as? CallState.Incoming)?.callerName
        )
        try {
            createPeerConnection(weAreCaller = false)
            attachLocalMedia(record.type)
            // The caller may have written the offer just before this; poll
            // briefly to handle the race.
            val offerSdp = fetchOfferSdp(callId)
            if (offerSdp == null) {
                Log.e(TAG, "acceptIncomingCall: no offer SDP yet")
                try { sig.endCall(CallStatus.ENDED) } catch (_: Exception) {}
                tearDown(CallStatus.ENDED)
                return
            }
            setRemoteDescriptionSuspending(offerSdp)
            val answer = createAnswer()
            peerConnection?.setLocalDescription(NoopSdpObserver, answer)
            sig.writeAnswer(answer)
            val now = System.currentTimeMillis()
            // Preserve video tracks mirrored onto Incoming during negotiation —
            // callers used to overwrite Active without them, so the callee
            // showed a black screen even though PC had received Remote video.
            val inc = _state.value as? CallState.Incoming
            _state.value = CallState.Active(
                record.copy(status = CallStatus.ACTIVE, acceptedAt = now),
                localVideoTrack = inc?.localVideoTrack ?: localVideoTrack,
                remoteVideoTrack = inc?.remoteVideoTrack
            )
            startObserving(record, weAreCaller = false)
        } catch (e: Exception) {
            Log.e(TAG, "acceptIncomingCall failed", e)
            try { sig.endCall(CallStatus.ENDED) } catch (_: Exception) {}
            tearDown(CallStatus.ENDED)
        }
    }

    /** Receiver rejects the call before picking up. */
    suspend fun rejectIncomingCall(callId: String) {
        try {
            CallSignaling(callId).endCall(CallStatus.REJECTED)
        } finally {
            tearDown(CallStatus.REJECTED)
        }
    }

    /**
     * Fire-and-forget version of [rejectIncomingCall]. Useful from
     * callers that don't have their own coroutine scope (e.g. an Activity
     * that is being destroyed). We piggyback on this singleton's
     * application-scoped [scope] so the network write survives the
     * calling component going away.
     */
    fun rejectIncomingCallAsync(callId: String) {
        scope.launch {
            try {
                rejectIncomingCall(callId)
            } catch (e: Exception) {
                Log.w(TAG, "rejectIncomingCallAsync: $callId", e)
            }
        }
    }

    /**
     * Called by [IncomingCallActivity] before the user has chosen to accept
     * or reject. Puts the manager into the Incoming state and starts
     * watching the call doc so we can dismiss if the caller cancels first.
     */
    fun prepareIncomingCall(callId: String, callerId: String, callerName: String, type: CallType): Boolean {
        if (!isFree) {
            // We're already in another call. Auto-reject this one so the
            // caller's ringtone stops immediately — busy signal.
            Log.w(TAG, "prepareIncomingCall: busy in ${_state.value}, auto-rejecting $callId")
            scope.launch {
                try {
                    CallSignaling(callId).endCall(CallStatus.REJECTED)
                } catch (e: Exception) {
                    Log.w(TAG, "auto-reject failed", e)
                }
            }
            return false
        }
        val record = CallRecord(
            callId = callId,
            callerId = callerId,
            receiverId = FirebaseAuthManager.getCurrentUser()?.email ?: "",
            type = type,
            status = CallStatus.RINGING,
            createdAt = System.currentTimeMillis()
        )
        _state.value = CallState.Incoming(record, callerName = callerName)

        // Watch the call doc so if the caller cancels before we answer, we
        // dismiss the incoming UI automatically.
        val sig = CallSignaling(callId).also { signaling = it }
        observeJob?.cancel()
        observeJob = scope.launch {
            sig.observeCall().collect { event ->
                when (event) {
                    is CallSignaling.CallEvent.Update -> {
                        if (event.record.status.isTerminal &&
                            _state.value is CallState.Incoming
                        ) {
                            Log.d(TAG, "Caller terminated before answer: ${event.record.status}")
                            tearDown(CallStatus.MISSED)
                        }
                    }
                    is CallSignaling.CallEvent.Deleted -> {
                        if (_state.value is CallState.Incoming) tearDown(CallStatus.MISSED)
                    }
                    is CallSignaling.CallEvent.Error -> Unit
                }
            }
        }
        return true
    }

    /** Hang up the active or outgoing call. */
    fun hangup() {
        scope.launch {
            signaling?.endCall(CallStatus.ENDED)
            tearDown(CallStatus.ENDED)
        }
    }

    fun toggleMute(): Boolean {
        val track = localAudioTrack ?: return false
        track.setEnabled(!track.enabled())
        return !track.enabled()
    }

    fun toggleCamera(): Boolean {
        val track = localVideoTrack ?: return false
        track.setEnabled(!track.enabled())
        return !track.enabled()
    }

    fun switchCamera() {
        val capturer = videoCapturer as? CameraVideoCapturer ?: return
        capturer.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFront: Boolean) {
                isFrontCamera = isFront
            }
            override fun onCameraSwitchError(error: String?) {
                Log.e(TAG, "switchCamera error: $error")
            }
        })
    }

    fun localVideoTrack(): VideoTrack? = localVideoTrack
    fun remoteVideoTrack(): VideoTrack? = when (val s = state.value) {
        is CallState.Active -> s.remoteVideoTrack
        is CallState.Outgoing -> s.remoteVideoTrack
        is CallState.Incoming -> s.remoteVideoTrack
        else -> null
    }

    // --- Internals --------------------------------------------------------

    private fun createPeerConnection(weAreCaller: Boolean) {
        val pc = factory.createPeerConnection(
            RtcConfig.rtcConfig(),
            object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate?) {
                    candidate ?: return
                    scope.launch {
                        try {
                            if (weAreCaller) signaling?.writeCallerCandidate(candidate)
                            else signaling?.writeReceiverCandidate(candidate)
                        } catch (e: Exception) {
                            Log.w(TAG, "writing ICE candidate failed", e)
                        }
                    }
                }

                override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                    val track = receiver?.track() ?: return
                    if (track is VideoTrack) {
                        Log.d(TAG, "Remote video track received")
                        attachRemoteVideoTrack(track)
                    } else {
                        Log.d(TAG, "Remote audio track received")
                    }
                }

                override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                    Log.d(TAG, "PeerConnection state: $newState")
                    when (newState) {
                        PeerConnection.PeerConnectionState.CONNECTED -> {
                            // Once the transport is live, widen default encoder bitrate
                            // caps WebRTC picks otherwise (often visibly soft on Wi‑Fi/LTE).
                            applyPreferredVideoSenderSettings()
                            val current = _state.value
                            if (current is CallState.Outgoing) {
                                _state.value = CallState.Active(
                                    current.record.copy(status = CallStatus.ACTIVE),
                                    localVideoTrack = current.localVideoTrack,
                                    remoteVideoTrack = current.remoteVideoTrack
                                )
                            }
                        }
                        PeerConnection.PeerConnectionState.FAILED,
                        PeerConnection.PeerConnectionState.CLOSED,
                        PeerConnection.PeerConnectionState.DISCONNECTED -> {
                            // Don't auto-end on DISCONNECTED — it can recover.
                            if (newState == PeerConnection.PeerConnectionState.FAILED ||
                                newState == PeerConnection.PeerConnectionState.CLOSED
                            ) {
                                scope.launch {
                                    signaling?.endCall(CallStatus.ENDED)
                                    tearDown(CallStatus.ENDED)
                                }
                            }
                        }
                        else -> Unit
                    }
                }

                override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
                override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
                override fun onIceConnectionReceivingChange(p0: Boolean) {}
                override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
                override fun onAddStream(p0: MediaStream?) {}
                override fun onRemoveStream(p0: MediaStream?) {}
                override fun onDataChannel(p0: org.webrtc.DataChannel?) {}
                override fun onRenegotiationNeeded() {}
                override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            }
        )
        peerConnection = pc ?: throw IllegalStateException("Failed to create PeerConnection")
    }

    /**
     * Bump outbound VP8/H264 targets once the ICE connection is alive.
     * WebRTCʼs conservative defaults often look blurry on decent Wi‑Fi;
     * this is the main knob we control from app code. Throughput/latency
     * still depend on network + TURN.
     */
    private fun applyPreferredVideoSenderSettings() {
        val pc = peerConnection ?: return
        pc.senders.forEach { sender ->
            if (sender.track() !is VideoTrack) return@forEach
            val params = sender.parameters ?: return@forEach
            val encodings = params.encodings ?: return@forEach
            if (encodings.isEmpty()) return@forEach
            try {
                for (encoding in encodings) {
                    encoding.maxBitrateBps = VIDEO_SEND_MAX_BITRATE_BPS
                    encoding.minBitrateBps = VIDEO_SEND_MIN_BITRATE_BPS
                    encoding.maxFramerate = VIDEO_SEND_MAX_FRAMERATE_FPS
                }
                if (!sender.setParameters(params)) {
                    Log.w(TAG, "Encoder declined custom video sender parameters")
                }
            } catch (e: Exception) {
                Log.w(TAG, "applyPreferredVideoSenderSettings", e)
            }
        }
    }

    private fun attachLocalMedia(type: CallType) {
        val streamId = "local_stream"
        val audioConstraints = MediaConstraints()
        localAudioSource = factory.createAudioSource(audioConstraints).also { src ->
            localAudioTrack = factory.createAudioTrack("audio_track", src).also { track ->
                track.setEnabled(true)
                peerConnection?.addTrack(track, listOf(streamId))
            }
        }
        if (type == CallType.VIDEO) {
            val capturer = createVideoCapturer() ?: run {
                Log.e(TAG, "No camera available, falling back to audio-only")
                return
            }
            videoCapturer = capturer
            val helper = SurfaceTextureHelper.create("capture_thread", eglBase.eglBaseContext)
            surfaceTextureHelper = helper
            try {
                localVideoSource = factory.createVideoSource(capturer.isScreencast).also { src ->
                    capturer.initialize(helper, appContext, src.capturerObserver)
                    capturer.startCapture(1280, 720, 30)
                    localVideoTrack = factory.createVideoTrack("video_track", src).also { track ->
                        track.setEnabled(true)
                        peerConnection?.addTrack(track, listOf(streamId))
                    }
                }
            } catch (e: Exception) {
                // Camera in use by another app, missing permission, or a
                // codec init failure. Fall through to audio-only rather
                // than crashing the call.
                Log.e(TAG, "Failed to start video capture, falling back to audio-only", e)
                try { capturer.dispose() } catch (_: Exception) {}
                videoCapturer = null
                try { helper.dispose() } catch (_: Exception) {}
                surfaceTextureHelper = null
                try { localVideoSource?.dispose() } catch (_: Exception) {}
                localVideoSource = null
                try { localVideoTrack?.dispose() } catch (_: Exception) {}
                localVideoTrack = null
                return
            }
            // Reflect the local track in state so the UI can render the
            // preview before the remote track arrives. Outgoing callers
            // handled this forever; Incoming callees need the same.
            val currentState = _state.value
            if (currentState is CallState.Outgoing) {
                _state.value = currentState.copy(localVideoTrack = localVideoTrack)
            } else if (currentState is CallState.Incoming) {
                _state.value = currentState.copy(localVideoTrack = localVideoTrack)
            }
        }
    }

    private fun createVideoCapturer(): VideoCapturer? {
        val enumerator: CameraEnumerator = Camera2Enumerator(appContext)
        // Prefer front camera for video calling.
        for (name in enumerator.deviceNames) {
            if (enumerator.isFrontFacing(name)) {
                return enumerator.createCapturer(name, null)?.also { isFrontCamera = true }
            }
        }
        // Fallback: any back camera.
        for (name in enumerator.deviceNames) {
            if (enumerator.isBackFacing(name)) {
                return enumerator.createCapturer(name, null)?.also { isFrontCamera = false }
            }
        }
        return null
    }

    private suspend fun createOffer(): SessionDescription = suspendCancellableCoroutine { cont ->
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(
                MediaConstraints.KeyValuePair(
                    "OfferToReceiveVideo",
                    if (localVideoTrack != null) "true" else "false"
                )
            )
        }
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                if (cont.isActive) cont.resume(sdp)
            }
            override fun onCreateFailure(error: String?) {
                if (cont.isActive) cont.resumeWithException(IllegalStateException("createOffer: $error"))
            }
            override fun onSetSuccess() {}
            override fun onSetFailure(p0: String?) {}
        }, constraints) ?: cont.resumeWithException(IllegalStateException("No PeerConnection"))
    }

    private suspend fun createAnswer(): SessionDescription = suspendCancellableCoroutine { cont ->
        val recvVideo = when (val st = _state.value) {
            is CallState.Incoming -> st.record.type == CallType.VIDEO ||
                st.localVideoTrack != null
            else -> localVideoTrack != null
        }
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(
                MediaConstraints.KeyValuePair(
                    "OfferToReceiveVideo",
                    if (recvVideo) "true" else "false"
                )
            )
        }
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                if (cont.isActive) cont.resume(sdp)
            }
            override fun onCreateFailure(error: String?) {
                if (cont.isActive) cont.resumeWithException(IllegalStateException("createAnswer: $error"))
            }
            override fun onSetSuccess() {}
            override fun onSetFailure(p0: String?) {}
        }, constraints) ?: cont.resumeWithException(IllegalStateException("No PeerConnection"))
    }

    private suspend fun setRemoteDescriptionSuspending(sdp: SessionDescription) =
        suspendCancellableCoroutine { cont ->
            val pc = peerConnection
            if (pc == null) {
                cont.resumeWithException(IllegalStateException("No PeerConnection"))
                return@suspendCancellableCoroutine
            }
            pc.setRemoteDescription(object : SdpObserver {
                override fun onCreateSuccess(p0: SessionDescription?) {}
                override fun onCreateFailure(p0: String?) {}
                override fun onSetSuccess() {
                    // Flush any candidates queued before the remote desc was
                    // set. Drain under the lock so we can't race a concurrent
                    // observeRemoteCandidates emission.
                    val toFlush = synchronized(pendingCandidatesLock) {
                        remoteDescriptionApplied = true
                        val copy = pendingRemoteCandidates.toList()
                        pendingRemoteCandidates.clear()
                        copy
                    }
                    toFlush.forEach { pc.addIceCandidate(it) }
                    if (cont.isActive) cont.resume(Unit)
                }
                override fun onSetFailure(error: String?) {
                    if (cont.isActive) cont.resumeWithException(
                        IllegalStateException("setRemoteDescription: $error")
                    )
                }
            }, sdp)
        }

    private suspend fun fetchOfferSdp(callId: String): SessionDescription? {
        // Poll briefly in case the doc was created but the offer field
        // hasn't propagated yet. In practice the first read succeeds.
        val callRef = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection(CallSignaling.CALLS)
            .document(callId)
        repeat(10) {
            val doc = callRef.get().await()
            if (!doc.exists()) return null
            @Suppress("UNCHECKED_CAST")
            val offer = doc.get("offer") as? Map<String, Any>
            val sdp = offer?.get("sdp") as? String
            if (sdp != null) return SessionDescription(SessionDescription.Type.OFFER, sdp)
            kotlinx.coroutines.delay(200)
        }
        return null
    }

    private fun startObserving(initial: CallRecord, weAreCaller: Boolean) {
        val sig = signaling ?: return
        observeJob?.cancel()
        candidatesJob?.cancel()

        observeJob = scope.launch {
            sig.observeCall().collect { event ->
                when (event) {
                    is CallSignaling.CallEvent.Update -> handleCallUpdate(event, weAreCaller)
                    is CallSignaling.CallEvent.Deleted -> tearDown(CallStatus.ENDED)
                    is CallSignaling.CallEvent.Error -> Log.w(TAG, "call event error: ${event.message}")
                }
            }
        }
        candidatesJob = scope.launch {
            sig.observeRemoteCandidates(weAreCaller).collect { candidate ->
                val pc = peerConnection ?: return@collect
                val addNow = synchronized(pendingCandidatesLock) {
                    if (remoteDescriptionApplied) {
                        true
                    } else {
                        pendingRemoteCandidates.add(candidate)
                        false
                    }
                }
                if (addNow) pc.addIceCandidate(candidate)
            }
        }
    }

    private suspend fun handleCallUpdate(
        event: CallSignaling.CallEvent.Update,
        weAreCaller: Boolean
    ) {
        val record = event.record
        // Caller-side: when the answer arrives, apply it. Cancel the ring
        // timeout because the receiver has picked up.
        if (weAreCaller && event.answerSdp != null && !remoteDescriptionApplied) {
            ringTimeoutJob?.cancel(); ringTimeoutJob = null
            try {
                setRemoteDescriptionSuspending(event.answerSdp)
                _state.value = when (val current = _state.value) {
                    is CallState.Outgoing -> CallState.Active(
                        record.copy(status = CallStatus.ACTIVE),
                        localVideoTrack = current.localVideoTrack,
                        remoteVideoTrack = current.remoteVideoTrack
                    )
                    is CallState.Active -> current.copy(record = record)
                    else -> current
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply remote answer", e)
            }
        }
        // Either side: if the other side terminated, tear down.
        if (record.status.isTerminal) {
            tearDown(record.status)
        }
    }

    private fun attachRemoteVideoTrack(track: VideoTrack) {
        track.setEnabled(true)
        scope.launch(Dispatchers.Default) {
            _state.value = when (val current = _state.value) {
                is CallState.Outgoing -> current.copy(remoteVideoTrack = track)
                is CallState.Active -> current.copy(remoteVideoTrack = track)
                is CallState.Incoming -> current.copy(remoteVideoTrack = track)
                else -> current
            }
        }
    }

    private fun tearDown(reason: CallStatus) {
        observeJob?.cancel(); observeJob = null
        candidatesJob?.cancel(); candidatesJob = null
        ringTimeoutJob?.cancel(); ringTimeoutJob = null
        try { videoCapturer?.stopCapture() } catch (_: Exception) {}
        try { videoCapturer?.dispose() } catch (_: Exception) {}
        videoCapturer = null
        try { surfaceTextureHelper?.dispose() } catch (_: Exception) {}
        surfaceTextureHelper = null
        try { localVideoTrack?.dispose() } catch (_: Exception) {}
        localVideoTrack = null
        try { localVideoSource?.dispose() } catch (_: Exception) {}
        localVideoSource = null
        try { localAudioTrack?.dispose() } catch (_: Exception) {}
        localAudioTrack = null
        try { localAudioSource?.dispose() } catch (_: Exception) {}
        localAudioSource = null
        try { peerConnection?.close() } catch (_: Exception) {}
        peerConnection = null
        signaling = null
        synchronized(pendingCandidatesLock) {
            remoteDescriptionApplied = false
            pendingRemoteCandidates.clear()
        }
        _state.value = CallState.Ended(reason)
        // Settle back to Idle after a short delay so the UI can show the
        // "Call ended" message; new calls during this window are still
        // accepted (see isFree).
        scope.launch {
            kotlinx.coroutines.delay(1500)
            if (_state.value is CallState.Ended) _state.value = CallState.Idle
        }
    }

    companion object {
        private const val TAG = "CallManager"

        // How long the caller's UI keeps "Ringing…" before we give up and
        // mark the call as missed. Matches the FCM ttl on the Cloud
        // Function — anything longer would let stale calls sneak through.
        private const val RING_TIMEOUT_MS = 60_000L

        // Outbound video encoder hints applied after CONNECTED — WebRTCʼs
        // stock defaults skew low and look soft even on decent links.
        private const val VIDEO_SEND_MIN_BITRATE_BPS = 300_000
        private const val VIDEO_SEND_MAX_BITRATE_BPS = 3_000_000
        private const val VIDEO_SEND_MAX_FRAMERATE_FPS = 30

        @Volatile private var instance: CallManager? = null

        fun getInstance(context: Context): CallManager {
            return instance ?: synchronized(this) {
                instance ?: CallManager(context.applicationContext).also { instance = it }
            }
        }
    }
}

/** The single source of truth for what call (if any) is happening. */
sealed class CallState {
    data object Idle : CallState()

    data class Outgoing(
        val record: CallRecord,
        val weAreCaller: Boolean,
        val localVideoTrack: VideoTrack? = null,
        val remoteVideoTrack: VideoTrack? = null
    ) : CallState()

    data class Incoming(
        val record: CallRecord,
        val callerName: String? = null,
        val localVideoTrack: VideoTrack? = null,
        val remoteVideoTrack: VideoTrack? = null,
    ) : CallState()

    data class Active(
        val record: CallRecord,
        val localVideoTrack: VideoTrack? = null,
        val remoteVideoTrack: VideoTrack? = null
    ) : CallState()

    data class Ended(val reason: CallStatus) : CallState()
}

private object NoopSdpObserver : SdpObserver {
    override fun onCreateSuccess(p0: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(p0: String?) {}
    override fun onSetFailure(p0: String?) {}
}
