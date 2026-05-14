package com.example.finlern.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.finlern.ui.theme.AuroraBlue1
import com.example.finlern.ui.theme.AuroraBlue2
import com.example.finlern.ui.theme.AuroraTeal
import com.example.finlern.ui.theme.AuroraViolet
import com.example.finlern.ui.theme.FinLernBackground
import com.example.finlern.ui.theme.StarColor
import com.example.finlern.viewmodels.AIAssistantViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIAssistantScreen(
    onNavigateBack: () -> Unit,
    viewModel: AIAssistantViewModel = viewModel()
) {
    var userInput by remember { mutableStateOf("") }
    val chatState by viewModel.chatState.collectAsState()
    val messages by viewModel.messages.collectAsState()

    FinLernBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(16.dp)
        ) {
            // Top Bar with Back Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Navigate Back",
                        tint = Color.White
                    )
                }
                Text(
                    text = "AI Assistant",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            // Welcome Message (only show if no messages)
            if (messages.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(
                        width = 2.dp,
                        color = AuroraBlue2.copy(alpha = 0.7f)
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        AuroraViolet.copy(alpha = 0.95f),
                                        AuroraBlue2.copy(alpha = 0.95f)
                                    )
                                )
                            )
                            .padding(24.dp)
                    ) {
                        Text(
                            text = "Interested in learning more about Finnish culture and language?!\n" +
                                  "Ask anything you like from our professional AI assistant!",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = StarColor,
                                textAlign = TextAlign.Center,
                                lineHeight = 28.sp,
                                fontSize = 16.sp
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Chat Messages
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                reverseLayout = false,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { message ->
                    ChatMessage(
                        content = message.content,
                        isUserMessage = message.isFromUser
                    )
                }

                // Show loading indicator when waiting for response
                item {
                    if (chatState is AIAssistantViewModel.ChatState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(16.dp)
                                .align(Alignment.CenterHorizontally),
                            color = Color.White
                        )
                    }
                }
            }

            // Error message if any
            if (chatState is AIAssistantViewModel.ChatState.Error) {
                Text(
                    text = (chatState as AIAssistantViewModel.ChatState.Error).message,
                    color = Color.Red,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // Chat Input Box
            TextField(
                value = userInput,
                onValueChange = { userInput = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                placeholder = {
                    Text(
                        "Type your question here...",
                        color = Color.White.copy(alpha = 0.6f)
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = AuroraBlue1.copy(alpha = 0.2f),
                    unfocusedContainerColor = AuroraBlue1.copy(alpha = 0.2f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color.White,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(16.dp),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (userInput.isNotBlank()) {
                                viewModel.sendMessage(userInput)
                                userInput = ""
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun ChatMessage(
    content: String,
    isUserMessage: Boolean
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUserMessage) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            color = if (isUserMessage) AuroraBlue1 else AuroraTeal.copy(alpha = 0.9f),
            shape = RoundedCornerShape(
                topStart = if (isUserMessage) 24.dp else 8.dp,
                topEnd = if (isUserMessage) 8.dp else 24.dp,
                bottomStart = 24.dp,
                bottomEnd = 24.dp
            ),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = content,
                color = Color.White,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
} 