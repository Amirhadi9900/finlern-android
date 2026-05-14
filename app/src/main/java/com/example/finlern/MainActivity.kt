package com.example.finlern

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.example.finlern.navigation.AppNavigation
import com.example.finlern.ui.theme.FinLernTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {
    // Add permission launchers
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d("MainActivity", "Notification permission granted")
        } else {
            Log.d("MainActivity", "Notification permission denied")
            // Handle the denial gracefully
        }
    }

    private val photoPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED, false) -> {
                Log.d("MainActivity", "Selected photos access granted")
            }
            permissions.getOrDefault(android.Manifest.permission.READ_MEDIA_IMAGES, false) -> {
                Log.d("MainActivity", "Photo permission granted")
            }
            permissions.getOrDefault(android.Manifest.permission.READ_EXTERNAL_STORAGE, false) -> {
                Log.d("MainActivity", "Storage permission granted")
            }
            else -> {
                Log.d("MainActivity", "Photo permissions denied")
                // Handle the denial gracefully
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Wait for auth state changes and handle FCM token
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            val currentUser = auth.currentUser
            if (currentUser != null) {
                // User is logged in, now get and store FCM token
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val token = task.result
                        Log.d("FCM_TOKEN", "Token retrieved: $token")
                        
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                // Use set() with merge so the document is created on first
                                // login (instead of update(), which fails when the doc
                                // doesn't exist yet). We also write the email field so the
                                // user immediately shows up in the inbox search, even if
                                // they haven't completed the Finnish level screen yet.
                                val email = currentUser.email!!
                                FirebaseFirestore.getInstance()
                                    .collection("userProfiles")
                                    .document(email)
                                    .set(
                                        mapOf(
                                            "email" to email,
                                            "fcmToken" to token
                                        ),
                                        SetOptions.merge()
                                    )
                                    .await()
                                Log.d("FCM_TOKEN", "Token + profile stub stored for $email")
                            } catch (e: Exception) {
                                Log.e("FCM_TOKEN", "Error storing token", e)
                                e.printStackTrace()
                            }
                        }
                    } else {
                        Log.e("FCM_TOKEN", "Failed to get FCM token: ${task.exception?.message}")
                        task.exception?.printStackTrace()
                    }
                }
            } else {
                Log.d("FCM_TOKEN", "User not logged in yet")
            }
        }

        // Check and request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != 
                PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        // Handle photo permissions based on Android version
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                // Android 14+: Handle both READ_MEDIA_VISUAL_USER_SELECTED and READ_MEDIA_IMAGES
                val permissions = arrayOf(
                    android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
                    android.Manifest.permission.READ_MEDIA_IMAGES
                )
                if (permissions.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) {
                    photoPermissionLauncher.launch(permissions)
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                // Android 13: Only READ_MEDIA_IMAGES
                if (checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) != 
                    PackageManager.PERMISSION_GRANTED) {
                    photoPermissionLauncher.launch(arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES))
                }
            }
            else -> {
                // Android 12 and below: READ_EXTERNAL_STORAGE
            }
        }
        
        // Handle notification click
        intent?.let { handleNotificationIntent(it) }

        setContent {
            FinLernTheme {
                AppNavigation()
            }
        }
    }
    
    private fun handleNotificationIntent(intent: Intent) {
        // Extract data from the intent
        val senderId = intent.getStringExtra("senderId")
        // TODO: Implement navigation to chat screen
        if (senderId != null) {
            // Navigate to chat with this sender
            // This will be implemented when navigation is set up
        }
    }

    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1
        private const val PHOTO_PERMISSION_REQUEST_CODE = 2
    }
}