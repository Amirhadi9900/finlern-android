package com.example.finlern.calls

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.finlern.ui.theme.FinLernTheme
import kotlinx.coroutines.launch

/**
 * Hosts [CallScreen] for both outgoing and active calls. Started by:
 *   - ChatScreen call buttons (outgoing) — pass EXTRA_RECEIVER_ID + EXTRA_CALL_TYPE.
 *   - IncomingCallActivity once the user taps Accept.
 *   - The foreground service notification tap (no extras; just resume).
 */
class CallActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val mic = granted[Manifest.permission.RECORD_AUDIO] == true
        val cam = granted[Manifest.permission.CAMERA] == true
        Log.d(TAG, "Permissions: mic=$mic cam=$cam")
        if (!mic) {
            Log.e(TAG, "Microphone permission denied; cannot place call.")
            finish()
            return@registerForActivityResult
        }
        proceed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            FinLernTheme {
                CallScreen(onFinished = {
                    CallForegroundService.stop(this)
                    finish()
                })
            }
        }

        if (hasRequiredPermissions()) {
            proceed()
        } else {
            requestRequiredPermissions()
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val needsCamera = intent.getStringExtra(EXTRA_CALL_TYPE) == CallType.VIDEO.raw
        val mic = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val cam = if (needsCamera) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        } else true
        return mic && cam
    }

    private fun requestRequiredPermissions() {
        val needsCamera = intent.getStringExtra(EXTRA_CALL_TYPE) == CallType.VIDEO.raw
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (needsCamera) perms.add(Manifest.permission.CAMERA)
        permissionLauncher.launch(perms.toTypedArray())
    }

    private fun proceed() {
        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_OUTGOING
        when (mode) {
            MODE_OUTGOING -> startOutgoingCall()
            MODE_ACCEPT_INCOMING -> acceptIncoming()
            MODE_RESUME -> Unit
            else -> Log.w(TAG, "Unknown mode: $mode")
        }
    }

    private fun startOutgoingCall() {
        val receiverId = intent.getStringExtra(EXTRA_RECEIVER_ID) ?: run {
            Log.e(TAG, "EXTRA_RECEIVER_ID missing"); finish(); return
        }
        val type = CallType.fromRaw(intent.getStringExtra(EXTRA_CALL_TYPE))
        val peerName = intent.getStringExtra(EXTRA_PEER_NAME) ?: receiverId
        // Route audio to the speaker for video calls / earpiece otherwise.
        configureAudioForCall(type)
        CallForegroundService.start(this, type, peerName)
        lifecycleScope.launch {
            CallManager.getInstance(applicationContext).startCall(receiverId, type)
        }
    }

    private fun acceptIncoming() {
        val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: run {
            Log.e(TAG, "EXTRA_CALL_ID missing"); finish(); return
        }
        val type = CallType.fromRaw(intent.getStringExtra(EXTRA_CALL_TYPE))
        val peerName = intent.getStringExtra(EXTRA_PEER_NAME) ?: callId
        configureAudioForCall(type)
        CallForegroundService.start(this, type, peerName)
        lifecycleScope.launch {
            CallManager.getInstance(applicationContext).acceptIncomingCall(callId)
        }
    }

    private fun configureAudioForCall(type: CallType) {
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        // Video calls default to speaker; voice calls to the earpiece. The
        // user can switch via the system audio chooser later.
        val useSpeaker = type == CallType.VIDEO
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // API 31+ replaces isSpeakerphoneOn with the
            // communication-device API, which also handles Bluetooth /
            // wired headset routing correctly.
            val wantedType = if (useSpeaker) {
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            } else {
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
            }
            val target = am.availableCommunicationDevices.firstOrNull { it.type == wantedType }
            if (target != null) {
                am.setCommunicationDevice(target)
            } else {
                Log.w(TAG, "No communication device of type $wantedType available")
            }
        } else {
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = useSpeaker
        }
    }

    private fun resetAudioMode() {
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        am.mode = AudioManager.MODE_NORMAL
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = false
        }
    }

    override fun onDestroy() {
        resetAudioMode()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    companion object {
        private const val TAG = "CallActivity"

        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_RECEIVER_ID = "extra_receiver_id"
        const val EXTRA_CALL_ID = "extra_call_id"
        const val EXTRA_CALL_TYPE = "extra_call_type"
        const val EXTRA_PEER_NAME = "extra_peer_name"

        const val MODE_OUTGOING = "outgoing"
        const val MODE_ACCEPT_INCOMING = "accept_incoming"
        const val MODE_RESUME = "resume"
    }
}
