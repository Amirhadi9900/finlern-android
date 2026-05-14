package com.example.finlern.calls

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.finlern.R

/**
 * Foreground service that runs for the duration of an active call.
 *
 * Android 14+ requires apps holding the microphone or camera while in the
 * background to be a foreground service of type `microphone` and/or
 * `camera`. Without this the OS revokes the mic the moment the user
 * navigates away from the call screen.
 *
 * The service itself is mostly bookkeeping — the actual WebRTC work
 * happens inside [CallManager]. We just need a live foreground notification
 * so the OS knows audio/video is in use.
 */
class CallForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val callType = CallType.fromRaw(intent?.getStringExtra(EXTRA_CALL_TYPE))
        val peerName = intent?.getStringExtra(EXTRA_PEER_NAME) ?: "call"
        startInForeground(callType, peerName)
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "CallForegroundService stopped")
        super.onDestroy()
    }

    private fun startInForeground(callType: CallType, peerName: String) {
        ensureChannel()
        val openCallIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, CallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val title = if (callType == CallType.VIDEO) "Video call with $peerName" else "Voice call with $peerName"
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("Tap to return to the call")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openCallIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val type = if (callType == CallType.VIDEO) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            startForeground(NOTIF_ID, notification, type)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Calls in progress",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shown while a voice or video call is active"
                setShowBadge(false)
            }
        )
    }

    companion object {
        private const val TAG = "CallFgService"
        private const val CHANNEL_ID = "calls_in_progress"
        private const val NOTIF_ID = 4242
        const val EXTRA_CALL_TYPE = "call_type"
        const val EXTRA_PEER_NAME = "peer_name"

        fun start(context: Context, callType: CallType, peerName: String) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                putExtra(EXTRA_CALL_TYPE, callType.raw)
                putExtra(EXTRA_PEER_NAME, peerName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CallForegroundService::class.java))
        }
    }
}
