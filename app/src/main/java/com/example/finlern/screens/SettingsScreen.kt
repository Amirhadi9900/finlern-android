package com.example.finlern.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.finlern.data.DeveloperModeManager
import com.example.finlern.data.FirebaseAuthManager
import com.example.finlern.data.NotificationPreferencesManager
import com.example.finlern.ui.theme.AuroraBlue1
import com.example.finlern.ui.theme.AuroraBlue2
import com.example.finlern.ui.theme.FinLernBackground
import com.example.finlern.ui.theme.MyRed
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val notificationPrefs = remember { NotificationPreferencesManager(context) }
    
    val currentUser = FirebaseAuthManager.getCurrentUser()
    val userEmail = currentUser?.email ?: ""
    remember {
        DeveloperModeManager.canAccessDeveloperMode(userEmail)
    }

    // Collect notification preferences
    val directMessagesEnabled by notificationPrefs.directMessagesEnabled.collectAsState(initial = true)
    val topicNotificationsEnabled by notificationPrefs.topicNotificationsEnabled.collectAsState(initial = true)

    // Add state for delete account dialog
    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    // Add this state for change level dialog
    var showChangeLevelDialog by remember { mutableStateOf(false) }

    // Add these colors from your theme
    MaterialTheme.colorScheme.error
    MaterialTheme.colorScheme.surface

    FinLernBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Text(
                    text = "Settings",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Direct Message Notifications",
                    color = Color.White
                )
                Switch(
                    checked = directMessagesEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            notificationPrefs.setDirectMessagesEnabled(enabled)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "General Notifications",
                    color = Color.White
                )
                Switch(
                    checked = topicNotificationsEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            notificationPrefs.setTopicNotificationsEnabled(enabled)
                            if (enabled) {
                                FirebaseMessaging.getInstance().subscribeToTopic("chat_updates")
                            } else {
                                FirebaseMessaging.getInstance().unsubscribeFromTopic("chat_updates")
                            }
                        }
                    }
                )
            }

            // Spacer for spacing between sections
            Spacer(modifier = Modifier.weight(1f))

            // Change Finnish Level Button
            Button(
                onClick = { showChangeLevelDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(12.dp),
                        spotColor = AuroraBlue2
                    ),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AuroraBlue1
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = "Change Level",
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Change Finnish Level",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Logout Button with enhanced design
            Button(
                onClick = {
                    FirebaseAuthManager.signOut()
                    onNavigateToLogin()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(12.dp),
                        spotColor = AuroraBlue2
                    ),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AuroraBlue1
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Logout",
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Logout",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Delete Account Button with enhanced design
            Button(
                onClick = { showDeleteAccountDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(12.dp),
                        spotColor = Color.Red.copy(alpha = 0.5f)
                    ),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MyRed
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = "Delete Account",
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Delete Account",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Enhanced Delete Account Dialog with animations
            if (showDeleteAccountDialog) {
                val scale = remember { Animatable(0.3f) }
                LaunchedEffect(key1 = true) {
                    scale.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                }

                AlertDialog(
                    modifier = Modifier
                        .scale(scale.value)
                        .clip(RoundedCornerShape(20.dp)),
                    onDismissRequest = { showDeleteAccountDialog = false },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Account Deletion",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    text = {
                        Column(
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            Text(
                                "Dear ${currentUser?.email},",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "To delete your account, please contact the developer:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "h.borji79@gmail.com",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = AuroraBlue2
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "You can either send a direct message within this app or send an email. " +
                                "For creating a new account, please contact the developer as well 🙂",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { showDeleteAccountDialog = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AuroraBlue2
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "Got It!",
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    properties = DialogProperties(
                        dismissOnBackPress = true,
                        dismissOnClickOutside = true
                    )
                )
            }

            // Change Finnish Level Dialog
            if (showChangeLevelDialog) {
                val scale = remember { Animatable(0.3f) }
                LaunchedEffect(key1 = true) {
                    scale.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                }

                AlertDialog(
                    modifier = Modifier
                        .scale(scale.value)
                        .clip(RoundedCornerShape(20.dp)),
                    onDismissRequest = { showChangeLevelDialog = false },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.School,
                                contentDescription = null,
                                tint = AuroraBlue2,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Change Finnish Level",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    text = {
                        Column(
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            Text(
                                "Hey there! 👋",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "To update your Finnish proficiency level, just reach out to your teacher. They'll be happy to help you make this change!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { showChangeLevelDialog = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AuroraBlue2
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "Got It!",
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    properties = DialogProperties(
                        dismissOnBackPress = true,
                        dismissOnClickOutside = true
                    )
                )
            }
        }
    }
}