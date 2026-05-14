package com.example.finlern.calls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.finlern.data.FirebaseAuthManager
import com.example.finlern.ui.theme.AuroraBlue1
import com.example.finlern.ui.theme.AuroraGreen2
import com.example.finlern.ui.theme.AuroraPink
import com.example.finlern.ui.theme.AuroraViolet
import com.example.finlern.ui.theme.FinLernBackground
import com.example.finlern.ui.theme.MyRed
import io.getstream.webrtc.android.ui.VideoTextureViewRenderer
import kotlinx.coroutines.delay
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.VideoTrack
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Active-call screen. Used both for outgoing (still ringing) and active
 * (peer connection up) states.
 *
 * Functionality first; visuals are intentionally restrained. The aurora
 * background carries the FinLern theme.
 */
@Composable
fun CallScreen(
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val manager = remember { CallManager.getInstance(context) }
    val state by manager.state.collectAsState()

    var isMuted by remember { mutableStateOf(false) }
    var isCameraOff by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableLongStateOf(0L) }

    // Bail out only once the call has actually ended. We DON'T close on
    // CallState.Idle, because Idle is also the brief initial state before
    // startCall finishes wiring everything up — closing then would race
    // the activity launch and we'd never see the call.
    LaunchedEffect(state) {
        if (state is CallState.Ended) {
            delay(800)
            onFinished()
        }
    }

    // Tick the timer while active.
    LaunchedEffect(state) {
        val s = state
        if (s is CallState.Active) {
            val start = s.record.acceptedAt ?: System.currentTimeMillis()
            while (true) {
                elapsedMs = System.currentTimeMillis() - start
                delay(1000)
            }
        }
    }

    FinLernBackground {
        Box(Modifier.fillMaxSize()) {
            val activeRemoteVideo = (state as? CallState.Active)?.remoteVideoTrack
            val outgoingRemoteVideo = (state as? CallState.Outgoing)?.remoteVideoTrack
            val incomingRemoteVideo = (state as? CallState.Incoming)?.remoteVideoTrack
            val remoteVideo = activeRemoteVideo ?: outgoingRemoteVideo ?: incomingRemoteVideo
            val localVideo = (state as? CallState.Active)?.localVideoTrack
                ?: (state as? CallState.Outgoing)?.localVideoTrack
                ?: (state as? CallState.Incoming)?.localVideoTrack
            val callType = when (val s = state) {
                is CallState.Outgoing -> s.record.type
                is CallState.Active -> s.record.type
                is CallState.Incoming -> s.record.type
                else -> CallType.AUDIO
            }

            // Full-screen remote video if present, else a centered avatar
            // placeholder with the peer's name.
            if (remoteVideo != null) {
                RemoteVideo(track = remoteVideo)
            } else {
                AudioCallPlaceholder(state = state, elapsedMs = elapsedMs)
            }

            // Local preview overlay (only in video calls)
            if (callType == CallType.VIDEO && localVideo != null) {
                Box(
                    modifier = Modifier
                        .padding(16.dp)
                        .size(width = 120.dp, height = 160.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .align(Alignment.TopEnd)
                        .background(Color.Black)
                ) {
                    VideoRenderer(
                        videoTrack = localVideo,
                        eglBaseContext = manager.eglBaseContext,
                        mirror = true,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Status overlay when remote video is shown
            if (remoteVideo != null) {
                CallStatusOverlay(state = state, elapsedMs = elapsedMs)
            }

            CallControls(
                state = state,
                callType = callType,
                isMuted = isMuted,
                isCameraOff = isCameraOff,
                onToggleMute = {
                    isMuted = manager.toggleMute()
                },
                onToggleCamera = {
                    isCameraOff = manager.toggleCamera()
                },
                onSwitchCamera = { manager.switchCamera() },
                onHangup = {
                    manager.hangup()
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
            )
        }
    }
}

@Composable
private fun RemoteVideo(track: VideoTrack) {
    val context = LocalContext.current
    val manager = remember { CallManager.getInstance(context) }
    VideoRenderer(
        videoTrack = track,
        eglBaseContext = manager.eglBaseContext,
        mirror = false,
        modifier = Modifier.fillMaxSize()
    )
}

/**
 * Compose wrapper around Stream's [VideoTextureViewRenderer]. Attaches and
 * detaches the renderer as a [org.webrtc.VideoSink] on the track for the
 * lifetime of the composition.
 */
@Composable
private fun VideoRenderer(
    videoTrack: VideoTrack,
    eglBaseContext: EglBase.Context,
    mirror: Boolean,
    modifier: Modifier = Modifier
) {
    val renderHolder = remember { mutableStateOf<VideoTextureViewRenderer?>(null) }
    val attachedTrack = remember { mutableStateOf<VideoTrack?>(null) }

    DisposableEffect(videoTrack) {
        onDispose {
            attachedTrack.value?.let { track ->
                renderHolder.value?.let { renderer -> track.removeSink(renderer) }
            }
            attachedTrack.value = null
        }
    }

    AndroidView(
        factory = { ctx ->
            VideoTextureViewRenderer(ctx).apply {
                init(
                    eglBaseContext,
                    object : RendererCommon.RendererEvents {
                        override fun onFirstFrameRendered() {}
                        override fun onFrameResolutionChanged(p0: Int, p1: Int, p2: Int) {}
                    }
                )
                setMirror(mirror)
                renderHolder.value = this
                videoTrack.addSink(this)
                attachedTrack.value = videoTrack
            }
        },
        update = { renderer ->
            // If the track reference changes (e.g. remote track replaced),
            // detach from the old one and attach to the new.
            if (attachedTrack.value !== videoTrack) {
                attachedTrack.value?.removeSink(renderer)
                videoTrack.addSink(renderer)
                attachedTrack.value = videoTrack
            }
            renderer.setMirror(mirror)
        },
        modifier = modifier
    )
}

@Composable
private fun AudioCallPlaceholder(
    state: CallState,
    elapsedMs: Long
) {
    val (peer, statusText) = when (val s = state) {
        is CallState.Outgoing -> s.record.receiverId to (
            if (s.record.status == CallStatus.RINGING) "Ringing…" else "Connecting…"
            )
        is CallState.Active -> {
            val self = FirebaseAuthManager.getCurrentUser()?.email ?: ""
            val other = when {
                self.isEmpty() -> s.record.receiverId
                self == s.record.callerId -> s.record.receiverId
                else -> s.record.callerId
            }
            other to formatElapsed(elapsedMs)
        }
        is CallState.Incoming -> (s.callerName ?: s.record.callerId) to "Connecting…"
        is CallState.Ended -> null to when (s.reason) {
            CallStatus.REJECTED -> "Call declined"
            CallStatus.MISSED -> "Missed call"
            else -> "Call ended"
        }
        CallState.Idle -> null to ""
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 96.dp)
            .wrapContentSize(Alignment.TopCenter),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(AuroraViolet, AuroraBlue1, AuroraGreen2, AuroraPink)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = (peer?.take(1) ?: "?").uppercase(),
                color = Color.White,
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = peer ?: "Call",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(text = statusText, color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp)
    }
}

@Composable
private fun CallStatusOverlay(state: CallState, elapsedMs: Long) {
    val text = when (val s = state) {
        is CallState.Outgoing ->
            if (s.record.status == CallStatus.RINGING) "Ringing…" else "Connecting…"
        is CallState.Active -> formatElapsed(elapsedMs)
        is CallState.Incoming -> "Connecting…"
        is CallState.Ended -> "Call ended"
        CallState.Idle -> ""
    }
    if (text.isEmpty()) return
    Surface(
        color = Color.Black.copy(alpha = 0.45f),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .padding(top = 32.dp)
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 16.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun CallControls(
    state: CallState,
    callType: CallType,
    isMuted: Boolean,
    isCameraOff: Boolean,
    onToggleMute: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onHangup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val showControls = when (state) {
        is CallState.Outgoing, is CallState.Active, is CallState.Incoming -> true
        else -> false
    }
    AnimatedVisibility(
        visible = showControls,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ControlButton(
                background = if (isMuted) Color.White else Color.White.copy(alpha = 0.18f),
                iconTint = if (isMuted) Color.Black else Color.White,
                onClick = onToggleMute
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = if (isMuted) "Unmute" else "Mute"
                )
            }
            if (callType == CallType.VIDEO) {
                ControlButton(
                    background = if (isCameraOff) Color.White else Color.White.copy(alpha = 0.18f),
                    iconTint = if (isCameraOff) Color.Black else Color.White,
                    onClick = onToggleCamera
                ) {
                    Icon(
                        imageVector = if (isCameraOff) Icons.Default.VideocamOff else Icons.Default.Videocam,
                        contentDescription = if (isCameraOff) "Turn camera on" else "Turn camera off"
                    )
                }
                ControlButton(
                    background = Color.White.copy(alpha = 0.18f),
                    iconTint = Color.White,
                    onClick = onSwitchCamera
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Switch camera"
                    )
                }
            }
            ControlButton(
                background = MyRed,
                iconTint = Color.White,
                onClick = onHangup
            ) {
                Icon(imageVector = Icons.Default.CallEnd, contentDescription = "End call")
            }
        }
    }
}

@Composable
private fun ControlButton(
    background: Color,
    iconTint: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(background)
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides iconTint
        ) { content() }
    }
}

private fun formatElapsed(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}



/* ------------------------------------------------------------------------ */
/* Incoming-call UI                                                         */
/* ------------------------------------------------------------------------ */

@Composable
fun IncomingCallScreen(
    callerName: String,
    callType: CallType,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    FinLernBackground {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 96.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(CircleShape)
                        .background(
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                listOf(AuroraViolet, AuroraBlue1, AuroraGreen2, AuroraPink)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = callerName.take(1).uppercase(),
                        color = Color.White,
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    text = callerName,
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (callType == CallType.VIDEO) "Incoming video call…" else "Incoming voice call…",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 16.sp
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 64.dp, start = 24.dp, end = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ControlButton(background = MyRed, iconTint = Color.White, onClick = onReject) {
                    Icon(imageVector = Icons.Default.CallEnd, contentDescription = "Reject")
                }
                ControlButton(background = AuroraGreen2, iconTint = Color.Black, onClick = onAccept) {
                    Icon(imageVector = Icons.Default.Call, contentDescription = "Accept")
                }
            }
        }
    }
}

