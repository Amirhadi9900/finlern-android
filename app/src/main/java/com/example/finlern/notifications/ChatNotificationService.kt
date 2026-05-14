package com.example.finlern.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.finlern.MainActivity
import com.example.finlern.R
import com.example.finlern.calls.CallType
import com.example.finlern.calls.IncomingCallActivity
import com.example.finlern.data.NotificationPreferencesManager
import com.example.finlern.data.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ChatNotificationService : FirebaseMessagingService() {
    private val channelId = "chat_messages"
    private val groupKey = "chat_notification_group"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Log.d("ChatNotificationService", "Service created")
        createNotificationChannel()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d("ChatNotificationService", "Message received from: ${remoteMessage.from}")
        Log.d("ChatNotificationService", "Message data payload: ${remoteMessage.data}")
        Log.d("ChatNotificationService", "Message notification payload: ${remoteMessage.notification?.body}")

        // Branch on the `type` data field set by the Cloud Function.
        // incoming_call: launch the full-screen ringer immediately and skip
        // the normal chat-notification path.
        val pushType = remoteMessage.data["type"]
        if (pushType == "incoming_call") {
            handleIncomingCall(remoteMessage.data)
            return
        }

        val isTopicMessage = remoteMessage.from?.contains("/topics/") == true

        scope.launch {
            try {
                val notificationPrefs = NotificationPreferencesManager(applicationContext)
                val shouldShowNotification = if (isTopicMessage) {
                    notificationPrefs.topicNotificationsEnabled.first()
                } else {
                    notificationPrefs.directMessagesEnabled.first()
                }
                
                Log.d("ChatNotificationService", "Should show notification: $shouldShowNotification")
                
                if (shouldShowNotification) {
                    // Handle both direct messages and topic messages
                    val senderId = remoteMessage.data["senderId"] ?: remoteMessage.notification?.title
                    val message = remoteMessage.data["message"] ?: remoteMessage.notification?.body
                    
                    Log.d("ChatNotificationService", "Sender ID: $senderId")
                    Log.d("ChatNotificationService", "Message: $message")
                    
                    if (senderId != null && message != null) {
                        val senderProfile = if (remoteMessage.from?.contains("/topics/") == true) {
                            null // It's a topic message
                        } else {
                            getSenderProfile(senderId)
                        }
                        showNotification(senderProfile?.name ?: senderId, message)
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatNotificationService", "Error processing message: ${e.message}")
                e.printStackTrace() // Add stack trace for better debugging
            }
        }
    }

    private fun handleIncomingCall(data: Map<String, String>) {
        val callId = data["callId"]
        val callerId = data["callerId"]
        val callerName = data["callerName"] ?: callerId
        val callType = CallType.fromRaw(data["callType"])
        if (callId.isNullOrBlank() || callerId.isNullOrBlank()) {
            Log.w("ChatNotificationService", "incoming_call push missing callId/callerId, dropping")
            return
        }
        Log.d(
            "ChatNotificationService",
            "Incoming call: callId=$callId callerId=$callerId type=${callType.raw}"
        )

        // Android 12+ blocks raw startActivity() from a service when the
        // app is in the background or the screen is locked. The supported
        // path is to post a CATEGORY_CALL notification with a
        // full-screen-intent; the OS shows our IncomingCallActivity even
        // from a locked screen, exactly like WhatsApp/Telegram do.
        ensureCallChannel()
        val intent = Intent(applicationContext, IncomingCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(IncomingCallActivity.EXTRA_CALL_ID, callId)
            putExtra(IncomingCallActivity.EXTRA_CALLER_ID, callerId)
            putExtra(IncomingCallActivity.EXTRA_CALLER_NAME, callerName)
            putExtra(IncomingCallActivity.EXTRA_CALL_TYPE, callType.raw)
        }
        val fullScreenPi = PendingIntent.getActivity(
            applicationContext,
            callId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val title = if (callType == CallType.VIDEO) {
            "Incoming video call"
        } else {
            "Incoming voice call"
        }
        val notification = NotificationCompat.Builder(this, CALL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(callerName)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPi, true)
            .setContentIntent(fullScreenPi)
            .build()
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(INCOMING_CALL_NOTIF_ID, notification)
            // Also try to start the activity directly. On Android 11- this
            // succeeds and is faster; on Android 12+ this is a best-effort
            // — the full-screen intent above is the reliable path.
            try {
                applicationContext.startActivity(intent)
            } catch (_: Exception) { /* background-launch denied, FSI handles it */ }
        } catch (e: Exception) {
            Log.e("ChatNotificationService", "Failed to post incoming-call notification", e)
        }
    }

    private fun ensureCallChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CALL_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CALL_CHANNEL_ID,
            "Incoming calls",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Plays the ringer for incoming voice and video calls"
            setBypassDnd(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            enableVibration(true)
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }


    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("ChatNotificationService", "New FCM token: $token")
        val currentUser = FirebaseAuth.getInstance().currentUser?.email
        if (currentUser != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    // set() with merge so this also creates the document the first time
                    // around. The email field guarantees the user is searchable in the
                    // inbox immediately.
                    FirebaseFirestore.getInstance()
                        .collection("userProfiles")
                        .document(currentUser)
                        .set(
                            mapOf(
                                "email" to currentUser,
                                "fcmToken" to token
                            ),
                            SetOptions.merge()
                        )
                    Log.d("ChatNotificationService", "Token stored in Firestore for $currentUser")
                } catch (e: Exception) {
                    Log.e("ChatNotificationService", "Error updating token: ${e.message}")
                }
            }
        }
    }

    private suspend fun getSenderProfile(email: String): UserProfile? {
        return try {
            FirebaseFirestore.getInstance()
                .collection("userProfiles")
                .document(email)
                .get()
                .await()
                .toObject(UserProfile::class.java)
        } catch (e: Exception) {
            null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(
                    channelId,
                    "Chat Messages",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Receive chat message notifications"
                    enableVibration(true)
                    enableLights(true)
                }
                
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
                Log.d("ChatNotificationService", "Notification channel created successfully")
            } catch (e: Exception) {
                Log.e("ChatNotificationService", "Error creating notification channel: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun showNotification(senderName: String, message: String) {
        try {
            Log.d("ChatNotificationService", "Showing notification for sender: $senderName")
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("senderId", senderName)
                putExtra("message", message)
            }
            
            val pendingIntent = PendingIntent.getActivity(
                this, 
                0, 
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("New message from $senderName")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setGroup(groupKey)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
                .setContentIntent(pendingIntent)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setVibrate(longArrayOf(0, 500, 200, 500))
                .build()

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notificationId = System.currentTimeMillis().toInt()
            notificationManager.notify(notificationId, notification)
            Log.d("ChatNotificationService", "Notification shown with ID: $notificationId")
        } catch (e: Exception) {
            Log.e("ChatNotificationService", "Error showing notification: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        scope.cancel() // Clean up coroutines
        super.onDestroy()
    }

    companion object {
        const val CALL_CHANNEL_ID = "incoming_calls"
        const val INCOMING_CALL_NOTIF_ID = 4243

        /**
         * Dismiss the ringer notification posted by [handleIncomingCall].
         * Called from [IncomingCallActivity] once the user has chosen to
         * accept or reject, or when the activity is dismissed.
         */
        fun cancelIncomingCallNotification(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(INCOMING_CALL_NOTIF_ID)
        }
    }
}
