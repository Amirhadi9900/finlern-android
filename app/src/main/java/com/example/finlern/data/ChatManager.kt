package com.example.finlern.data

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import android.util.Patterns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.example.finlern.data.models.Chat
import com.example.finlern.data.models.Message
import com.example.finlern.data.models.MessageStatus
import com.example.finlern.data.models.MessageType
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.io.File

object ErrorLogger {
    fun logError(tag: String, message: String, e: Exception? = null) {
        Log.e(tag, message, e)
    }
}

class ChatManager private constructor() {
    private val tag = "ChatManager"
    private val db = FirebaseFirestore.getInstance()
    private val chatsCollection = db.collection("chats")
    private val storage = FirebaseStorage.getInstance()
    private var currentDownloadTask: com.google.firebase.storage.FileDownloadTask? = null
    private var currentUploadTask: com.google.firebase.storage.UploadTask? = null
    private val transferQueue = mutableListOf<suspend () -> Boolean>()
    private val queueMutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO)
    private val MAX_FILE_SIZE = 100 * 1024 * 1024 // 100 MB limit

    private fun validateMessage(message: String): Boolean {
        val urlMatcher = Patterns.WEB_URL.matcher(message)
        while (urlMatcher.find()) {
            val url = message.substring(urlMatcher.start(), urlMatcher.end())
            if (!url.startsWith("https://", ignoreCase = true)) return false
        }
        return message.length <= 1000
    }

    suspend fun createChat(participants: List<String>): String? {
        Log.d(tag, "Creating chat for participants: $participants")
        return try {
            val sortedParticipants = participants.sorted()
            val existingChat = chatsCollection
                .whereEqualTo("participants", sortedParticipants)
                .get()
                .await()
                .documents
                .firstOrNull()
            if (existingChat != null) {
                Log.d(tag, "Found existing chat with ID: ${existingChat.id}")
                return existingChat.id
            }
            val chat = Chat(participants = sortedParticipants, createdAt = System.currentTimeMillis())
            val docRef = chatsCollection.add(chat).await()
            Log.d(tag, "Chat document created with ID: ${docRef.id}")
            docRef.id
        } catch (e: Exception) {
            ErrorLogger.logError(tag, "Error creating chat", e)
            null
        }
    }

    suspend fun sendMessage(
        chatId: String,
        content: String,
        type: MessageType = MessageType.TEXT,
        metadata: Map<String, String> = emptyMap()
    ): Boolean {
        Log.d(tag, "Attempting to send message to chat $chatId")
        return try {
            if (!validateMessage(content)) {
                Log.e(tag, "Message validation failed")
                return false
            }
            val chatDoc = chatsCollection.document(chatId).get().await()
            if (!chatDoc.exists()) {
                Log.e(tag, "Chat $chatId does not exist")
                return false
            }
            val currentUser = FirebaseAuthManager.getCurrentUser()?.email ?: run {
                Log.e(tag, "Current user is null")
                return false
            }
            val chat = chatDoc.toObject(Chat::class.java)
            if (chat?.participants?.contains(currentUser) != true) {
                Log.e(tag, "User $currentUser is not a participant in chat $chatId")
                return false
            }
            val message = Message(
                chatId = chatId,
                senderId = currentUser,
                content = content,
                type = type,
                timestamp = System.currentTimeMillis(),
                status = MessageStatus.SENDING,
                metadata = metadata.toMap()
            )
            val messageRef = chatsCollection.document(chatId).collection("messages").add(message).await()
            val updatedMessage = message.copy(messageId = messageRef.id, status = MessageStatus.SENT)
            messageRef.set(updatedMessage).await()
            // Also write denormalized fields so the Inbox "Recent Chats" list can sort
            // and preview without parsing the lastMessage map. The original code's
            // orderBy("lastMessageTimestamp") silently excluded every chat because
            // nothing was writing that field.
            val previewText = when (updatedMessage.type) {
                MessageType.TEXT -> updatedMessage.content
                MessageType.IMAGE -> "📷 Image"
                MessageType.FILE -> "📎 ${updatedMessage.metadata["fileName"] ?: "File"}"
            }
            chatsCollection.document(chatId).update(
                mapOf(
                    "lastMessage" to updatedMessage,
                    "lastMessagePreview" to previewText,
                    "lastMessageTimestamp" to updatedMessage.timestamp
                )
            ).await()
            // Push notifications are sent server-side by the onMessageCreated
            // Cloud Function (see functions/src/index.ts). The old client-side
            // FCMClient required shipping service-account.json inside the APK,
            // which leaked admin credentials to anyone who could unzip it.
            Log.d(tag, "Message sent successfully with ID: ${messageRef.id}")
            true
        } catch (e: Exception) {
            ErrorLogger.logError(tag, "Error sending message", e)
            false
        }
    }

    suspend fun getMessages(chatId: String): List<Message> {
        return try {
            chatsCollection.document(chatId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()
                .toObjects(Message::class.java)
        } catch (e: Exception) {
            ErrorLogger.logError(tag, "Error fetching messages", e)
            emptyList()
        }
    }

    fun getMessagesFlow(chatId: String): Flow<List<Message>> = callbackFlow {
        Log.d(tag, "Starting messages flow for chat: $chatId")
        val listener = chatsCollection.document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    ErrorLogger.logError(tag, "Error listening to messages", error)
                    close(error)
                    return@addSnapshotListener
                }
                snapshot?.let {
                    val messages = it.toObjects(Message::class.java)
                    Log.d(tag, "Received ${messages.size} messages for chat $chatId")
                    trySend(messages)
                }
            }
        awaitClose {
            Log.d(tag, "Closing messages flow for chat: $chatId")
            listener.remove()
        }
    }

    suspend fun getUserChats(userEmail: String): List<Chat> {
        return try {
            chatsCollection.whereArrayContains("participants", userEmail)
                .get()
                .await()
                .toObjects(Chat::class.java)
        } catch (e: Exception) {
            ErrorLogger.logError(tag, "Error fetching user chats", e)
            emptyList()
        }
    }

    suspend fun deleteMessage(chatId: String, messageId: String): Boolean {
        return try {
            val currentUser = FirebaseAuthManager.getCurrentUser()?.email ?: return false
            val messageDoc = chatsCollection.document(chatId).collection("messages").document(messageId).get().await()
            val message = messageDoc.toObject(Message::class.java)
            if (message?.senderId != currentUser) {
                Log.e(tag, "User $currentUser cannot delete message $messageId: not the sender")
                return false
            }
            chatsCollection.document(chatId).collection("messages").document(messageId).delete().await()
            Log.d(tag, "Message $messageId deleted successfully")
            true
        } catch (e: Exception) {
            ErrorLogger.logError(tag, "Error deleting message", e)
            false
        }
    }

    suspend fun editMessage(chatId: String, messageId: String, newContent: String): Boolean {
        return try {
            val currentUser = FirebaseAuthManager.getCurrentUser()?.email ?: return false
            val messageDoc = chatsCollection.document(chatId).collection("messages").document(messageId).get().await()
            val message = messageDoc.toObject(Message::class.java)
            if (message?.senderId != currentUser) {
                Log.e(tag, "User $currentUser cannot edit message $messageId: not the sender")
                return false
            }
            chatsCollection.document(chatId).collection("messages").document(messageId)
                .update("content", newContent)
                .await()
            Log.d(tag, "Message $messageId edited successfully")
            true
        } catch (e: Exception) {
            ErrorLogger.logError(tag, "Error editing message", e)
            false
        }
    }

    suspend fun deleteChat(chatId: String): Boolean {
        return try {
            val currentUser = FirebaseAuthManager.getCurrentUser()?.email ?: return false
            if (!AuthorizedEmails.isAdminOrTeacher(currentUser)) {
                Log.e(tag, "User $currentUser is not authorized to delete chats")
                return false
            }
            val messages = chatsCollection.document(chatId).collection("messages").get().await()
            messages.documents.forEach { it.reference.delete().await() }
            chatsCollection.document(chatId).delete().await()
            Log.d(tag, "Chat $chatId deleted successfully")
            true
        } catch (e: Exception) {
            ErrorLogger.logError(tag, "Error deleting chat", e)
            false
        }
    }

    suspend fun checkChatExists(chatId: String): Boolean {
        return try {
            val chatDoc = chatsCollection.document(chatId).get().await()
            chatDoc.exists()
        } catch (e: Exception) {
            ErrorLogger.logError(tag, "Error checking chat existence", e)
            false
        }
    }

    suspend fun getChat(chatId: String): Chat? {
        return try {
            val chatDoc = chatsCollection.document(chatId).get().await()
            chatDoc.toObject(Chat::class.java)
        } catch (e: Exception) {
            ErrorLogger.logError(tag, "Error getting chat", e)
            null
        }
    }

    suspend fun sendFile(
        context: Context,
        chatId: String,
        uri: Uri,
        fileName: String,
        onProgress: (Float) -> Unit = {}
    ): Boolean {
        val sendFileTask = suspend sendFileTask@{
            var success = false
            try {
                val contentResolver = context.contentResolver
                // Check file size before proceeding
                val sizeCursor = contentResolver.query(uri, null, null, null, null)
                sizeCursor?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex >= 0) {
                            val size = cursor.getLong(sizeIndex)
                            if (size > MAX_FILE_SIZE) {
                                Log.e(tag, "File size ($size bytes) exceeds 100 MB limit")
                                return@sendFileTask false
                            }
                        }
                    }
                }

                val mimeType = contentResolver.getType(uri) ?: getMimeType(fileName)
                val actualFileName = contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) cursor.getString(nameIndex) else null
                    } else null
                } ?: fileName
                val fileNameWithExt = if (actualFileName.contains(".")) actualFileName else "$fileName${getExtension(mimeType)}"
                val timestamp = System.currentTimeMillis()
                val currentUser = FirebaseAuthManager.getCurrentUser()?.email
                if (currentUser == null) {
                    Log.e(tag, "Current user is null")
                } else {
                    val safeFileName = "${currentUser}_${timestamp}_$fileNameWithExt"
                    val fileRef = storage.reference.child("chat_attachments").child(chatId).child(safeFileName)
                    val inputStream = contentResolver.openInputStream(uri)
                    if (inputStream == null) {
                        Log.e(tag, "Failed to open input stream for URI: $uri")
                    } else {
                        val encryptedFile = File(context.cacheDir, "encrypted_$safeFileName")
                        val encryptedData: EncryptionManager.EncryptedData
                        encryptedFile.outputStream().use { output ->
                            encryptedData = EncryptionManager.encrypt(inputStream, output)
                            val metadata = StorageMetadata.Builder()
                                .setContentType("application/octet-stream")
                                .setCustomMetadata("originalFileName", fileNameWithExt)
                                .setCustomMetadata("originalMimeType", mimeType)
                                .setCustomMetadata("iv", encryptedData.iv)
                                .setCustomMetadata("encryptedKey", encryptedData.encryptedKey)
                                .build()
                            currentUploadTask = fileRef.putFile(Uri.fromFile(encryptedFile), metadata)
                            currentUploadTask?.addOnProgressListener { taskSnapshot ->
                                val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toFloat()
                                onProgress(progress)
                            }?.await()
                            onProgress(100f)
                            currentUploadTask = null
                        }
                        encryptedFile.delete()
                        val downloadUrl = fileRef.downloadUrl.await().toString()
                        val type = if (mimeType.startsWith("image/")) MessageType.IMAGE else MessageType.FILE
                        success = sendMessage(
                            chatId = chatId,
                            content = downloadUrl,
                            type = type,
                            metadata = mapOf(
                                "fileName" to fileNameWithExt,
                                "fileUrl" to downloadUrl,
                                "iv" to encryptedData.iv,
                                "encryptedKey" to encryptedData.encryptedKey,
                                "mimeType" to mimeType
                            )
                        )
                    }
                }
            } catch (e: StorageException) {
                Log.e(tag, "Storage error during file upload: ${e.message}", e)
            } catch (e: Exception) {
                ErrorLogger.logError(tag, "Unexpected error during file upload", e)
            }
            success
        }

        return queueMutex.withLock {
            if (currentUploadTask == null) {
                try {
                    sendFileTask()
                } catch (e: Exception) {
                    handleFileTransferError(e, onProgress, true)
                    false
                }
            } else {
                transferQueue.add(sendFileTask)
                Log.d(tag, "Upload queued for $fileName")
                false
            }
        }.also { scope.launch { processQueue() } }
    }

    private fun getExtension(mimeType: String): String {
        return MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)?.let { ".$it" } ?: ".bin"
    }

    private fun getMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast(".").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }

    suspend fun downloadFile(
        context: Context,
        fileUrl: String,
        fileName: String,
        metadata: Map<String, String>,
        onProgress: (Float) -> Unit = {},
        onComplete: (String) -> Unit = {}
    ) {
        val downloadFileTask = suspend {
            var success = false
            try {
                val mimeType = metadata["mimeType"] ?: getMimeType(fileName)
                val cleanFileName = fileName.replace(":", "_")
                val reference = storage.getReferenceFromUrl(fileUrl)
                val encryptedFile = File(context.cacheDir, "encrypted_$cleanFileName")
                currentDownloadTask = reference.getFile(encryptedFile)
                currentDownloadTask?.addOnProgressListener { taskSnapshot ->
                    val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toFloat()
                    onProgress(progress)
                }?.await()
                onProgress(100f)
                currentDownloadTask = null

                val decryptedFile = File(context.cacheDir, cleanFileName)
                encryptedFile.inputStream().use { input ->
                    decryptedFile.outputStream().use { output ->
                        EncryptionManager.decrypt(
                            input,
                            output,
                            metadata["iv"] ?: throw Exception("Missing IV"),
                            metadata["encryptedKey"] ?: throw Exception("Missing encrypted key")
                        )
                    }
                }
                encryptedFile.delete()
                onComplete(decryptedFile.absolutePath)

                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", decryptedFile)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(intent, "Open file"))
                success = true
            } catch (e: Exception) {
                ErrorLogger.logError(tag, "Error during file download", e)
            }
            success
        }

        queueMutex.withLock {
            if (currentDownloadTask == null) {
                try {
                    downloadFileTask()
                } catch (e: Exception) {
                    handleFileTransferError(e, onProgress, false)
                    throw e
                }
            } else {
                transferQueue.add(downloadFileTask)
                Log.d(tag, "Download queued for $fileName")
            }
        }.also { scope.launch { processQueue() } }
    }

    private suspend fun processQueue() {
        queueMutex.withLock {
            if (transferQueue.isNotEmpty() && currentUploadTask == null && currentDownloadTask == null) {
                val task = transferQueue.removeAt(0)
                try {
                    task()
                } catch (e: Exception) {
                    ErrorLogger.logError(tag, "Error processing queued task", e)
                }
                processQueue()
            }
        }
    }

    fun cancelDownload() {
        currentDownloadTask?.cancel()
        currentDownloadTask = null
        Log.d(tag, "Download cancelled by user")
        scope.launch { processQueue() }
    }

    fun cancelUpload() {
        currentUploadTask?.cancel()
        currentUploadTask = null
        Log.d(tag, "Upload cancelled by user")
        scope.launch { processQueue() }
    }

    companion object {
        @Volatile
        private var instance: ChatManager? = null

        fun getInstance(): ChatManager {
            return instance ?: synchronized(this) {
                val newInstance = ChatManager()
                instance = newInstance
                newInstance
            }
        }
    }

    private fun handleFileTransferError(e: Exception, onProgress: (Float) -> Unit, isUpload: Boolean): Boolean {
        onProgress(-1f)
        if (isUpload) currentUploadTask = null else currentDownloadTask = null
        return when (e) {
            is java.util.concurrent.CancellationException -> {
                Log.d(tag, "Transfer cancelled by user")
                true
            }
            is java.io.IOException -> {
                ErrorLogger.logError(tag, "Network error during file transfer", e)
                false
            }
            is SecurityException -> {
                ErrorLogger.logError(tag, "Permission denied", e)
                false
            }
            else -> {
                ErrorLogger.logError(tag, "Error during file transfer", e)
                false
            }
        }.also { scope.launch { processQueue() } }
    }

    fun saveFileToDownloads(context: Context, sourcePath: String, fileName: String): Boolean {
        return try {
            val sourceFile = File(sourcePath)
            if (!sourceFile.exists()) {
                Log.e(tag, "Source file does not exist: $sourcePath")
                return false
            }
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            var finalFile = File(downloadsDir, fileName)
            var counter = 1
            while (finalFile.exists()) {
                val nameWithoutExt = fileName.substringBeforeLast(".")
                val extension = fileName.substringAfterLast(".", "")
                finalFile = File(downloadsDir, "${nameWithoutExt}_$counter.$extension")
                counter++
            }
            sourceFile.inputStream().use { input ->
                finalFile.outputStream().use { output -> input.copyTo(output) }
            }
            MediaScannerConnection.scanFile(context, arrayOf(finalFile.absolutePath), null) { path, _ ->
                Log.d(tag, "File saved and scanned: $path")
            }
            true
        } catch (e: Exception) {
            ErrorLogger.logError(tag, "Error saving file", e)
            false
        }
    }

}