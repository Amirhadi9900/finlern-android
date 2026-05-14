package com.example.finlern.calls

import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.example.finlern.notifications.ChatNotificationService
import com.example.finlern.ui.theme.FinLernTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Full-screen activity that rings when an incoming-call FCM data push
 * arrives. It puts [CallManager] into the Incoming state, plays the
 * default ringtone, vibrates, and shows accept/reject buttons.
 *
 * Pressing Accept hands off to [CallActivity] which actually places the
 * peer-connection setup.
 */
class IncomingCallActivity : ComponentActivity() {

    private var ringtone: android.media.Ringtone? = null
    private var vibrator: Vibrator? = null

    // Tracks whether the user explicitly chose accept/reject; on a back
    // press or stray dismiss we treat the absence of a decision as reject
    // so the caller stops ringing immediately.
    private var userDecided = false
    private var currentCallId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        showOverLockscreen()

        val callId = intent.getStringExtra(EXTRA_CALL_ID)
        val callerId = intent.getStringExtra(EXTRA_CALLER_ID)
        val callerName = intent.getStringExtra(EXTRA_CALLER_NAME) ?: callerId ?: "Unknown"
        val callType = CallType.fromRaw(intent.getStringExtra(EXTRA_CALL_TYPE))

        if (callId == null || callerId == null) {
            Log.e(TAG, "IncomingCallActivity missing extras; finishing.")
            finish()
            return
        }
        currentCallId = callId

        val prepared = CallManager.getInstance(applicationContext)
            .prepareIncomingCall(callId, callerId, callerName, callType)
        if (!prepared) {
            // Already busy in another call — the manager auto-rejected.
            Log.w(TAG, "Already busy, dismissing incoming-call UI for $callId")
            userDecided = true
            ChatNotificationService.cancelIncomingCallNotification(this)
            finish()
            return
        }

        startRingingFeedback()

        // If the caller cancels before the user reacts, we want to dismiss
        // this activity automatically. Watch CallManager state.
        lifecycleScope.launch {
            CallManager.getInstance(applicationContext).state.collectLatest { state ->
                when (state) {
                    is CallState.Ended, CallState.Idle -> {
                        Log.d(TAG, "Call ended before user answered, dismissing.")
                        finish()
                    }
                    else -> Unit
                }
            }
        }

        setContent {
            FinLernTheme {
                IncomingCallScreen(
                    callerName = callerName,
                    callType = callType,
                    onAccept = {
                        userDecided = true
                        stopRingingFeedback()
                        ChatNotificationService.cancelIncomingCallNotification(this)
                        val accept = Intent(this, CallActivity::class.java).apply {
                            putExtra(CallActivity.EXTRA_MODE, CallActivity.MODE_ACCEPT_INCOMING)
                            putExtra(CallActivity.EXTRA_CALL_ID, callId)
                            putExtra(CallActivity.EXTRA_CALL_TYPE, callType.raw)
                            putExtra(CallActivity.EXTRA_PEER_NAME, callerName)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        startActivity(accept)
                        finish()
                    },
                    onReject = {
                        userDecided = true
                        stopRingingFeedback()
                        ChatNotificationService.cancelIncomingCallNotification(this)
                        lifecycleScope.launch {
                            CallManager.getInstance(applicationContext).rejectIncomingCall(callId)
                            finish()
                        }
                    }
                )
            }
        }
    }

    private fun showOverLockscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun startRingingFeedback() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                }
                play()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to play ringtone", e)
        }
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            val pattern = longArrayOf(0, 1000, 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start vibration", e)
        }
    }

    private fun stopRingingFeedback() {
        try { ringtone?.stop() } catch (_: Exception) {}
        ringtone = null
        try { vibrator?.cancel() } catch (_: Exception) {}
        vibrator = null
    }

    override fun onDestroy() {
        stopRingingFeedback()
        ChatNotificationService.cancelIncomingCallNotification(this)
        // If the screen got dismissed without an explicit choice (back
        // button, system swipe-away, etc), treat that as a soft reject so
        // the caller stops ringing immediately. We hand the work off to
        // CallManager's own application-scoped CoroutineScope so the
        // network write survives this activity being torn down.
        if (!userDecided) {
            currentCallId?.let { id ->
                CallManager.getInstance(applicationContext).rejectIncomingCallAsync(id)
            }
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "IncomingCallActivity"
        const val EXTRA_CALL_ID = "extra_call_id"
        const val EXTRA_CALLER_ID = "extra_caller_id"
        const val EXTRA_CALLER_NAME = "extra_caller_name"
        const val EXTRA_CALL_TYPE = "extra_call_type"
    }
}
