package com.example.finlern.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.finlern.data.ChatGPTService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AIAssistantViewModel(application: Application) : AndroidViewModel(application) {
    private val chatGPTService = ChatGPTService(application)

    private val _chatState = MutableStateFlow<ChatState>(ChatState.Idle)
    val chatState: StateFlow<ChatState> = _chatState.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    fun sendMessage(message: String) {
        if (message.isBlank()) return

        viewModelScope.launch {
            _chatState.value = ChatState.Loading
            _messages.value = _messages.value + ChatMessage(message, isFromUser = true)

            chatGPTService.sendMessage(message).collect { response ->
                when (response) {
                    is ChatGPTService.ChatResponse.Success -> {
                        _messages.value = _messages.value + ChatMessage(response.content, isFromUser = false)
                        _chatState.value = ChatState.Idle
                    }
                    is ChatGPTService.ChatResponse.Error -> {
                        _chatState.value = ChatState.Error(response.message)
                    }
                }
            }
        }
    }

    sealed class ChatState {
        object Idle : ChatState()
        object Loading : ChatState()
        data class Error(val message: String) : ChatState()
    }

    data class ChatMessage(
        val content: String,
        val isFromUser: Boolean,
        val timestamp: Long = System.currentTimeMillis()
    )
} 