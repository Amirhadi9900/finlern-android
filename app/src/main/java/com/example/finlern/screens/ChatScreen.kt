package com.example.finlern.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.util.Patterns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.finlern.calls.CallActivity
import com.example.finlern.calls.CallType
import com.example.finlern.data.ChatManager
import com.example.finlern.data.FirebaseAuthManager
import com.example.finlern.data.UserProfile
import com.example.finlern.data.models.Message
import com.example.finlern.data.models.MessageStatus
import com.example.finlern.data.models.MessageType
import com.example.finlern.ui.theme.AuroraBlue1
import com.example.finlern.ui.theme.FinLernBackground
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SenderBubbleColor = Color(0xFF4A5D7B)
private val ReceiverBubbleColor = Color(0xFF556B2F).copy(alpha = 0.9f)
private val TextColor = Color.White
private val HighlightColor = Color(0xFFFFD700)
private val MaxBubbleWidth = 280.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: String,
    onNavigateBack: () -> Unit,
    onNavigateToInbox: () -> Unit
) {
    val tag = "ChatScreen"
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var messageText by remember { mutableStateOf("") }
    var chatPartnerName by remember { mutableStateOf("") }
    var chatPartnerEmail by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val chatManager = remember { ChatManager.getInstance() }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var highlightedMessageId by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    DisposableEffect(Unit) {
        onDispose {
            chatManager.apply {
                cancelDownload()
                cancelUpload()
            }
            Log.d(tag, "Cleaned up transfers on dispose")
        }
    }

    LaunchedEffect(chatId) {
        val currentUserEmail = FirebaseAuthManager.getCurrentUser()?.email ?: return@LaunchedEffect
        val chat = chatManager.getChat(chatId)
        val partnerEmail = chat?.participants?.find { it != currentUserEmail }
        partnerEmail?.let { email ->
            chatPartnerEmail = email
            val userProfile = FirebaseFirestore.getInstance()
                .collection("userProfiles")
                .document(email)
                .get()
                .await()
                .toObject(UserProfile::class.java)
            chatPartnerName = userProfile?.name ?: email
        }
    }

    val startCall: (CallType) -> Unit = { callType ->
        if (chatPartnerEmail.isBlank()) {
            errorMessage = "Cannot start call: partner not loaded yet"
        } else {
            val intent = Intent(context, CallActivity::class.java).apply {
                putExtra(CallActivity.EXTRA_MODE, CallActivity.MODE_OUTGOING)
                putExtra(CallActivity.EXTRA_RECEIVER_ID, chatPartnerEmail)
                putExtra(CallActivity.EXTRA_CALL_TYPE, callType.raw)
                putExtra(
                    CallActivity.EXTRA_PEER_NAME,
                    chatPartnerName.ifBlank { chatPartnerEmail }
                )
            }
            context.startActivity(intent)
        }
    }

    LaunchedEffect(messages) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val sendMessage = {
        if (messageText.isNotBlank()) {
            val messageToSend = messageText.trim()
            Log.d(tag, "Attempting to send message: $messageToSend")
            scope.launch {
                val success = chatManager.sendMessage(chatId, messageToSend)
                if (success) {
                    Log.d(tag, "Message sent successfully")
                    messageText = ""
                } else {
                    Log.e(tag, "Failed to send message")
                    errorMessage = "Failed to send message"
                }
            }
        }
    }

    LaunchedEffect(chatId) {
        Log.d(tag, "Starting to collect messages for chat: $chatId")
        chatManager.getMessagesFlow(chatId).collect { newMessages ->
            if (newMessages.isEmpty() && !chatManager.checkChatExists(chatId)) {
                onNavigateToInbox()
            } else {
                messages = newMessages
            }
        }
    }

    LaunchedEffect(highlightedMessageId) {
        if (highlightedMessageId != null) {
            delay(3000)
            highlightedMessageId = null
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            errorMessage = null
        }
    }

    FinLernBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        if (isSearchActive) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                                placeholder = { Text("Search messages...", color = TextColor.copy(alpha = 0.6f)) },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedTextColor = TextColor,
                                    unfocusedTextColor = TextColor,
                                    focusedIndicatorColor = TextColor,
                                    unfocusedIndicatorColor = TextColor.copy(alpha = 0.5f)
                                )
                            )
                            LaunchedEffect(Unit) {
                                focusRequester.requestFocus()
                            }
                        } else {
                            Text(
                                text = if (chatPartnerName.isNotEmpty()) "Chat with $chatPartnerName" else "Chat",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = TextColor
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextColor
                            )
                        }
                    },
                    actions = {
                        AnimatedVisibility(visible = isSearchActive, enter = fadeIn(), exit = fadeOut()) {
                            IconButton(
                                onClick = {
                                    isSearchActive = false
                                    searchQuery = ""
                                    highlightedMessageId = null
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Search",
                                    tint = TextColor
                                )
                            }
                        }
                        AnimatedVisibility(visible = !isSearchActive, enter = fadeIn(), exit = fadeOut()) {
                            Row {
                                IconButton(onClick = { startCall(CallType.AUDIO) }) {
                                    Icon(
                                        imageVector = Icons.Default.Call,
                                        contentDescription = "Voice call",
                                        tint = TextColor
                                    )
                                }
                                IconButton(onClick = { startCall(CallType.VIDEO) }) {
                                    Icon(
                                        imageVector = Icons.Default.VideoCall,
                                        contentDescription = "Video call",
                                        tint = TextColor
                                    )
                                }
                                IconButton(onClick = { isSearchActive = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = TextColor
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { padding ->
            ChatContent(
                messages = messages,
                padding = padding,
                messageText = messageText,
                onMessageChange = { messageText = it },
                onSendMessage = sendMessage,
                chatManager = chatManager,
                chatId = chatId,
                scope = scope,
                searchQuery = searchQuery,
                highlightedMessageId = highlightedMessageId,
                listState = listState,
                onError = { errorMessage = it }
            )
        }
    }
}

@Composable
private fun ChatContent(
    messages: List<Message>,
    padding: PaddingValues,
    messageText: String,
    onMessageChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    chatManager: ChatManager,
    chatId: String,
    scope: CoroutineScope,
    searchQuery: String,
    highlightedMessageId: String?,
    listState: LazyListState,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { message ->
                val isHighlighted = message.messageId == highlightedMessageId
                val matchesSearch = searchQuery.isBlank() || message.content.contains(
                    searchQuery,
                    ignoreCase = true
                ) || message.metadata["fileName"]?.contains(searchQuery, ignoreCase = true) == true

                if (matchesSearch) {
                    Box(
                        modifier = Modifier.then(
                            if (isHighlighted) Modifier.border(
                                width = 2.dp,
                                color = HighlightColor,
                                shape = RoundedCornerShape(24.dp)
                            ) else Modifier
                        )
                    ) {
                        MessageBubble(
                            message = message,
                            isOwnMessage = message.senderId == FirebaseAuthManager.getCurrentUser()?.email,
                            chatId = chatId,
                            scope = scope,
                            chatManager = chatManager,
                            context = context,
                            onError = onError
                        )
                    }
                }
            }
        }

        if (searchQuery.isNotBlank() && messages.none { message ->
                message.content.contains(searchQuery, ignoreCase = true) ||
                        message.metadata["fileName"]?.contains(searchQuery, ignoreCase = true) == true
            }) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No messages found",
                    color = TextColor.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        MessageInput(
            messageText = messageText,
            onMessageChange = onMessageChange,
            onSendMessage = onSendMessage,
            chatManager = chatManager,
            chatId = chatId,
            scope = scope,
            context = context,
            onError = onError
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: Message,
    isOwnMessage: Boolean,
    chatId: String,
    scope: CoroutineScope,
    chatManager: ChatManager,
    context: Context,
    onError: (String) -> Unit
) {
    var showOptionsDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isOwnMessage) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            color = if (isOwnMessage) SenderBubbleColor else ReceiverBubbleColor,
            shape = RoundedCornerShape(
                topStart = if (isOwnMessage) 24.dp else 8.dp,
                topEnd = if (isOwnMessage) 8.dp else 24.dp,
                bottomStart = 24.dp,
                bottomEnd = 24.dp
            ),
            modifier = Modifier
                .widthIn(max = MaxBubbleWidth)
                .animateContentSize()
                .combinedClickable(
                    onClick = {},
                    onLongClick = { if (isOwnMessage) showOptionsDialog = true }
                )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                MessageContent(
                    message = message,
                    scope = scope,
                    chatManager = chatManager,
                    context = context,
                    onError = onError
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isOwnMessage) Arrangement.End else Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val timestampText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
                    Text(
                        text = timestampText,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextColor,
                        modifier = Modifier.semantics { contentDescription = "Message sent at $timestampText" }
                    )
                    if (isOwnMessage) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when (message.status) {
                                MessageStatus.SENDING -> "Sending..."
                                MessageStatus.SENT -> "✓ Sent"
                                MessageStatus.FAILED -> "⚠️ Failed to send"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = TextColor
                        )
                    }
                }
            }
        }
    }

    if (showOptionsDialog) {
        MessageOptionsDialog(
            onDismiss = { showOptionsDialog = false },
            onDelete = {
                scope.launch {
                    val success = chatManager.deleteMessage(chatId, message.messageId)
                    if (!success) onError("Failed to delete message")
                }
            },
            onEdit = { showEditDialog = true }
        )
    }

    if (showEditDialog) {
        EditMessageDialog(
            initialContent = message.content,
            onDismiss = { showEditDialog = false },
            onConfirm = { newContent ->
                scope.launch {
                    val success = chatManager.editMessage(chatId, message.messageId, newContent)
                    if (!success) onError("Failed to edit message")
                }
            }
        )
    }
}

@Composable
private fun MessageContent(
    message: Message,
    scope: CoroutineScope,
    chatManager: ChatManager,
    context: Context,
    onError: (String) -> Unit
) {
    var downloadProgress by remember(message.messageId) { mutableFloatStateOf(-1f) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var downloadedFilePath by remember { mutableStateOf<String?>(null) }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = {
                showSaveDialog = false
                downloadedFilePath = null
            },
            title = { Text("Save File") },
            text = { Text("Do you want to save this file to your device?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        downloadedFilePath?.let { path ->
                            scope.launch {
                                try {
                                    val success = chatManager.saveFileToDownloads(
                                        context,
                                        path,
                                        message.metadata["fileName"] ?: "file"
                                    )
                                    if (!success) onError("Failed to save file")
                                } catch (e: Exception) {
                                    onError("Failed to save file: ${e.message}")
                                }
                            }
                        }
                        showSaveDialog = false
                        downloadedFilePath = null
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false; downloadedFilePath = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    when (message.type) {
        MessageType.TEXT -> {
            val annotatedText = buildAnnotatedString {
                val text = message.content
                val matches = Patterns.WEB_URL.matcher(text)
                var lastIndex = 0
                while (matches.find()) {
                    val start = matches.start()
                    val end = matches.end()
                    val url = text.substring(start, end)
                    append(text.substring(lastIndex, start))
                    withStyle(
                        SpanStyle(
                            color = if (url.startsWith("https://", ignoreCase = true)) HighlightColor else TextColor,
                            textDecoration = TextDecoration.Underline
                        )
                    ) {
                        if (url.startsWith("https://", ignoreCase = true)) {
                            pushStringAnnotation(tag = "URL", annotation = url)
                            append(url)
                            pop()
                        } else {
                            append(url)
                        }
                    }
                    lastIndex = end
                }
                if (lastIndex < text.length) append(text.substring(lastIndex))
            }
            Text(
                text = annotatedText,
                style = MaterialTheme.typography.bodyMedium.copy(color = TextColor),
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .clickable {
                        annotatedText.getStringAnnotations("URL", 0, annotatedText.length)
                            .firstOrNull()?.let { annotation ->
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            }
                    },
                overflow = TextOverflow.Ellipsis
            )
        }
        MessageType.IMAGE -> {
            val fileName = message.metadata["fileName"] ?: "Image"
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.width(200.dp).height(150.dp)) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(message.metadata["fileUrl"])
                            .crossfade(true)
                            .build(),
                        contentDescription = "Image message: $fileName",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                IconButton(
                    onClick = {
                        scope.launch {
                            message.metadata["fileUrl"]?.let { url ->
                                try {
                                    chatManager.downloadFile(
                                        context = context,
                                        fileUrl = url,
                                        fileName = fileName,
                                        metadata = message.metadata,
                                        onProgress = { progress -> downloadProgress = progress },
                                        onComplete = { filePath ->
                                            downloadedFilePath = filePath
                                            showSaveDialog = true
                                        }
                                    )
                                } catch (e: Exception) {
                                    onError(if (downloadProgress == -1f) "Download cancelled by user" else "Failed to download image: ${e.message}")
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                ) {
                    if (downloadProgress in 0f..99f) {
                        FileProgressIndicator(
                            progress = downloadProgress,
                            onCancel = {
                                chatManager.cancelDownload()
                                downloadProgress = -1f
                            }
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download image",
                            tint = TextColor
                        )
                    }
                }
            }
        }
        MessageType.FILE -> {
            val fileName = message.metadata["fileName"] ?: "File"
            when {
                fileName.matches(Regex(".*\\.(jpg|jpeg|png|gif|webp)$", RegexOption.IGNORE_CASE)) -> {
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = "Image file",
                                tint = TextColor,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = fileName,
                                color = TextColor,
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .semantics { contentDescription = "File name: $fileName" }
                            )
                        }
                        IconButton(
                            onClick = {
                                scope.launch {
                                    message.metadata["fileUrl"]?.let { url ->
                                        try {
                                            chatManager.downloadFile(
                                                context = context,
                                                fileUrl = url,
                                                fileName = fileName,
                                                metadata = message.metadata,
                                                onProgress = { progress -> downloadProgress = progress },
                                                onComplete = { filePath ->
                                                    downloadedFilePath = filePath
                                                    showSaveDialog = true
                                                }
                                            )
                                        } catch (e: Exception) {
                                            onError(if (downloadProgress == -1f) "Download cancelled by user" else "Failed to download file: ${e.message}")
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        ) {
                            if (downloadProgress in 0f..99f) {
                                FileProgressIndicator(
                                    progress = downloadProgress,
                                    onCancel = {
                                        chatManager.cancelDownload()
                                        downloadProgress = -1f
                                    }
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Download",
                                    tint = TextColor
                                )
                            }
                        }
                    }
                }
                fileName.matches(Regex(".*\\.(mp4|mov|avi|mkv)$", RegexOption.IGNORE_CASE)) -> {
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideoFile,
                                contentDescription = "Video file",
                                tint = TextColor,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = fileName,
                                color = TextColor,
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .semantics { contentDescription = "File name: $fileName" }
                            )
                        }
                        IconButton(
                            onClick = {
                                scope.launch {
                                    message.metadata["fileUrl"]?.let { url ->
                                        try {
                                            chatManager.downloadFile(
                                                context = context,
                                                fileUrl = url,
                                                fileName = fileName,
                                                metadata = message.metadata,
                                                onProgress = { progress -> downloadProgress = progress },
                                                onComplete = { filePath ->
                                                    downloadedFilePath = filePath
                                                    showSaveDialog = true
                                                }
                                            )
                                        } catch (e: Exception) {
                                            onError(if (downloadProgress == -1f) "Download cancelled by user" else "Failed to download file: ${e.message}")
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        ) {
                            if (downloadProgress in 0f..99f) {
                                FileProgressIndicator(
                                    progress = downloadProgress,
                                    onCancel = {
                                        chatManager.cancelDownload()
                                        downloadProgress = -1f
                                    }
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Download",
                                    tint = TextColor
                                )
                            }
                        }
                    }
                }
                else -> {
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = "Document file",
                                tint = TextColor,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = fileName,
                                color = TextColor,
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .semantics { contentDescription = "File name: $fileName" }
                            )
                        }
                        IconButton(
                            onClick = {
                                scope.launch {
                                    message.metadata["fileUrl"]?.let { url ->
                                        try {
                                            chatManager.downloadFile(
                                                context = context,
                                                fileUrl = url,
                                                fileName = fileName,
                                                metadata = message.metadata,
                                                onProgress = { progress -> downloadProgress = progress },
                                                onComplete = { filePath ->
                                                    downloadedFilePath = filePath
                                                    showSaveDialog = true
                                                }
                                            )
                                        } catch (e: Exception) {
                                            onError(if (downloadProgress == -1f) "Download cancelled by user" else "Failed to download file: ${e.message}")
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        ) {
                            if (downloadProgress in 0f..99f) {
                                FileProgressIndicator(
                                    progress = downloadProgress,
                                    onCancel = {
                                        chatManager.cancelDownload()
                                        downloadProgress = -1f
                                    }
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Download",
                                    tint = TextColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageOptionsDialog(
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Message Options") },
        text = {
            Column {
                TextButton(onClick = { onEdit(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Edit Message")
                }
                TextButton(onClick = { onDelete(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete Message")
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun EditMessageDialog(
    initialContent: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var editedText by remember { mutableStateOf(initialContent) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Message") },
        text = {
            TextField(
                value = editedText,
                onValueChange = { editedText = it },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(editedText); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun MessageInput(
    messageText: String,
    onMessageChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    chatManager: ChatManager,
    chatId: String,
    scope: CoroutineScope,
    context: Context,
    onError: (String) -> Unit
) {
    var fileUploadProgress by remember { mutableFloatStateOf(-1f) }
    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val fileName = uri.lastPathSegment ?: "file"
                    val success = chatManager.sendFile(
                        context = context,
                        chatId = chatId,
                        uri = uri,
                        fileName = fileName,
                        onProgress = { progress -> fileUploadProgress = progress }
                    )
                    if (!success) {
                        onError(if (fileUploadProgress == -1f) "Upload cancelled by user" else "Failed to send file")
                    }
                    fileUploadProgress = -1f
                } catch (e: Exception) {
                    onError("Upload failed: ${e.message}")
                    fileUploadProgress = -1f
                }
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        color = Color.White.copy(alpha = 0.1f),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { filePickerLauncher.launch("*/*") },
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            ) {
                if (fileUploadProgress in 0f..99f) {
                    FileProgressIndicator(
                        progress = fileUploadProgress,
                        onCancel = {
                            chatManager.cancelUpload()
                            fileUploadProgress = -1f
                            onError("Upload cancelled by user")
                        }
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = "Attach file",
                        tint = TextColor
                    )
                }
            }
            TextField(
                value = messageText,
                onValueChange = onMessageChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                placeholder = { Text("Type a message...", color = TextColor.copy(alpha = 0.6f)) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = TextColor,
                    unfocusedTextColor = TextColor,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                maxLines = 4
            )
            IconButton(
                onClick = onSendMessage,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(AuroraBlue1)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = TextColor
                )
            }
        }
    }
}

@Composable
private fun FileProgressIndicator(progress: Float, onCancel: () -> Unit) {
    if (progress in 0f..99f) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                .clickable { onCancel() }
        ) {
            CircularProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                color = TextColor,
                strokeWidth = 2.dp,
                trackColor = ProgressIndicatorDefaults.circularIndeterminateTrackColor
            )
            Text(
                text = "${progress.toInt()}%",
                color = TextColor,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}