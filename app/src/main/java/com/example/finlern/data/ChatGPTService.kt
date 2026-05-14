package com.example.finlern.data

import android.content.Context
import android.util.Log
import com.example.finlern.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ChatGPTService(private val context: Context) {
    companion object {
        private const val TAG = "ChatGPTService"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val apiKey: String = BuildConfig.OPENAI_API_KEY

    fun sendMessage(message: String): Flow<ChatResponse> = flow {
        try {
            Log.d(TAG, "Preparing to send message to OpenAI")
            
            val requestBody = JSONObject().apply {
                put("model", "gpt-3.5-turbo")
                put("messages", listOf(
                    JSONObject().apply {
                        put("role", "system")
                        put("content", "You are a helpful assistant specializing in Finnish language and culture.")
                    },
                    JSONObject().apply {
                        put("role", "user")
                        put("content", message)
                    }
                ))
                put("temperature", 0.7)
                put("max_tokens", 1000)
            }.toString()
            
            Log.d(TAG, "Request body prepared: ${requestBody.take(100)}...")

            val response = withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url("https://api.openai.com/v1/chat/completions")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody.toRequestBody("application/json".toMediaType()))
                    .build()

                Log.d(TAG, "Sending request to OpenAI API")
                client.newCall(request).execute()
            }

            Log.d(TAG, "Response received. Status code: ${response.code}")
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                Log.e(TAG, "API call failed. Code: ${response.code}, Error body: $errorBody")
                throw Exception("API call failed with code ${response.code}: $errorBody")
            }

            val responseBody = response.body?.string()
            Log.d(TAG, "Response body received: ${responseBody?.take(100)}...")
            
            val jsonResponse = JSONObject(responseBody!!)
            val content = jsonResponse
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")

            Log.d(TAG, "Successfully parsed response content")
            emit(ChatResponse.Success(content))

        } catch (e: Exception) {
            Log.e(TAG, "Error in sendMessage: ${e.message}", e)
            emit(ChatResponse.Error("Failed to get response: ${e.message}"))
        }
    }

    sealed class ChatResponse {
        data class Success(val content: String) : ChatResponse()
        data class Error(val message: String) : ChatResponse()
    }
} 