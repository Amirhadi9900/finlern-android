package com.example.finlern.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.finlern.data.AuthorizedEmails
import com.example.finlern.data.ChatManager
import com.example.finlern.data.FirebaseAuthManager
import com.example.finlern.data.InboxManager
import com.example.finlern.data.UserProfile
import com.example.finlern.ui.theme.AuroraBlue1
import com.example.finlern.ui.theme.AuroraBlue2
import com.example.finlern.ui.theme.FinLernBackground
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun InboxScreen(
    onNavigateBack: () -> Unit,
    onNavigateToChat: (String) -> Unit
) {
    val currentUser = FirebaseAuthManager.getCurrentUser()
    val isAdmin = remember(currentUser?.email) {
        currentUser?.email?.let { AuthorizedEmails.isAdminOrTeacher(it) } ?: false
    }

    var searchQuery by remember { mutableStateOf("") }
    var users by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var recentChats by remember { mutableStateOf<List<InboxManager.RecentChat>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(currentUser?.email) {
        currentUser?.email?.let { email ->
            recentChats = InboxManager.getInstance().getRecentChats(email)
        }
    }

    // Debounced search. Everyone sees admins + every other student (minus themselves).
    // Originally students could only see admins, which is why Leila couldn't find Amir.
    LaunchedEffect(searchQuery) {
        isLoading = true
        delay(300)
        val inboxManager = InboxManager.getInstance()
        val email = currentUser?.email
        val admins = inboxManager.searchAdmins(searchQuery, currentUserEmail = email)
        val students = inboxManager.searchStudents(searchQuery, currentUserEmail = email)
        users = students + admins
        isLoading = false
    }

    FinLernBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(16.dp)
        ) {
            // Header
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            Text(
                text = if (isAdmin) "Welcome to Your Inbox" else "Welcome to Your Inbox",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                placeholder = { Text("Search by email") },
                leadingIcon = { 
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search"
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AuroraBlue1,
                    unfocusedBorderColor = AuroraBlue2,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLeadingIconColor = AuroraBlue1,
                    unfocusedLeadingIconColor = AuroraBlue2
                )
            )

            // Results
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AuroraBlue1)
                }
            } else {
                LazyColumn {
                    // Show recent chats if search query is empty
                    if (searchQuery.isEmpty() && recentChats.isNotEmpty()) {
                        item {
                            Text(
                                text = "Recent Chats",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        items(recentChats) { recentChat ->
                            RecentChatCard(
                                recentChat = recentChat,
                                onNavigateToChat = onNavigateToChat
                            )
                        }
                        
                        item {
                            Text(
                                text = "All Users",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                    
                    // Show search results
                    items(users) { user ->
                        UserCard(user, onNavigateToChat)
                    }
                }
            }
        }
    }
}

@Composable
private fun UserCard(user: UserProfile, onNavigateToChat: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val tag = "InboxScreen"
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable {
                Log.d(tag, "UserCard clicked for user: ${user.email}")
                scope.launch {
                    Log.d(tag, "Starting chat creation for user: ${user.email}")
                    startChat(user) { chatId ->
                        Log.d(tag, "Chat created with ID: $chatId")
                        onNavigateToChat(chatId)
                    }
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile Picture
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(user.profilePictureUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "Profile picture",
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f)),
                contentScale = ContentScale.Crop
            )

            // User Info
            Column(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .weight(1f)
            ) {
                Text(
                    text = user.email,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge
                )
                
                if (user.finnishLevel.isNotEmpty()) {
                    Text(
                        text = "Level: ${user.finnishLevel}",
                        color = AuroraBlue1,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentChatCard(
    recentChat: InboxManager.RecentChat,
    onNavigateToChat: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onNavigateToChat(recentChat.chatId) },
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile Picture (you can load this from UserProfile if needed)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(AuroraBlue2.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = recentChat.participantEmail.firstOrNull()?.uppercase() ?: "?",
                    color = Color.White
                )
            }

            Column(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .weight(1f)
            ) {
                Text(
                    text = recentChat.participantEmail,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = recentChat.lastMessage,
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

private suspend fun startChat(user: UserProfile, onChatCreated: (String) -> Unit) {
    val tag = "InboxScreen"
    val currentUser = FirebaseAuthManager.getCurrentUser()?.email
    Log.d(tag, "Current user email: $currentUser")
    
    if (currentUser == null) {
        Log.e(tag, "Failed to start chat: Current user is null")
        return
    }
    
    val participants = listOf(currentUser, user.email)
    Log.d(tag, "Attempting to create chat with participants: $participants")
    
    ChatManager.getInstance().createChat(participants)?.let { chatId ->
        Log.d(tag, "Chat created successfully with ID: $chatId")
        onChatCreated(chatId)
    } ?: run {
        Log.e(tag, "Failed to create chat: ChatManager returned null chatId")
    }
}