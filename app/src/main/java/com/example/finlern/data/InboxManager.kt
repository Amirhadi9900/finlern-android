package com.example.finlern.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class InboxManager private constructor() {
    private val TAG = "InboxManager"
    private val db = FirebaseFirestore.getInstance()
    private val usersCollection = db.collection("userProfiles")
    private val chatsCollection = db.collection("chats")

    suspend fun searchStudents(
        query: String,
        currentUserEmail: String? = null,
        finnishLevel: String? = null
    ): List<UserProfile> {
        return try {
            var filteredUsers = usersCollection
                .get()
                .await()
                .documents
                .mapNotNull { it.toObject(UserProfile::class.java) }
                .filter { it.email.isNotEmpty() }
                .filter { !AuthorizedEmails.isAdminOrTeacher(it.email) }
                .filter { currentUserEmail == null || !it.email.equals(currentUserEmail, ignoreCase = true) }

            if (query.isNotEmpty()) {
                filteredUsers = filteredUsers.filter {
                    it.email.contains(query, ignoreCase = true) ||
                        it.name.contains(query, ignoreCase = true)
                }
            }

            if (!finnishLevel.isNullOrEmpty()) {
                filteredUsers = filteredUsers.filter {
                    it.finnishLevel == finnishLevel
                }
            }

            filteredUsers
        } catch (e: Exception) {
            Log.e(TAG, "Error searching students: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun searchAdmins(
        query: String,
        currentUserEmail: String? = null
    ): List<UserProfile> {
        return try {
            var filteredUsers = usersCollection
                .get()
                .await()
                .documents
                .mapNotNull { it.toObject(UserProfile::class.java) }
                .filter { it.email.isNotEmpty() }
                .filter { AuthorizedEmails.isAdminOrTeacher(it.email) }
                .filter { currentUserEmail == null || !it.email.equals(currentUserEmail, ignoreCase = true) }

            if (query.isNotEmpty()) {
                filteredUsers = filteredUsers.filter {
                    it.email.contains(query, ignoreCase = true) ||
                        it.name.contains(query, ignoreCase = true)
                }
            }

            filteredUsers
        } catch (e: Exception) {
            Log.e(TAG, "Error searching admins: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getRecentChats(currentUserEmail: String): List<RecentChat> {
        return try {
            // We intentionally do NOT chain .orderBy("lastMessageTimestamp") onto the
            // query, because Firestore silently drops documents missing that field
            // (older chats from before this fix won't have it). Instead, we fetch
            // every chat that includes the user and sort client-side.
            chatsCollection
                .whereArrayContains("participants", currentUserEmail)
                .get()
                .await()
                .documents
                .mapNotNull { doc ->
                    val participants = doc.get("participants") as? List<*> ?: return@mapNotNull null
                    val otherParticipant = participants
                        .filterNotNull()
                        .map { it.toString() }
                        .find { it != currentUserEmail }
                        ?: return@mapNotNull null

                    // Prefer the new denormalized fields written by ChatManager.sendMessage;
                    // fall back to the lastMessage map for chats that still hold the old shape.
                    val lastMessagePreview = doc.getString("lastMessagePreview")
                        ?: (doc.get("lastMessage") as? Map<*, *>)?.get("content")?.toString()
                        ?: ""
                    val timestamp = doc.getLong("lastMessageTimestamp")
                        ?: ((doc.get("lastMessage") as? Map<*, *>)?.get("timestamp") as? Long)
                        ?: doc.getLong("createdAt")
                        ?: 0L

                    RecentChat(
                        chatId = doc.id,
                        participantEmail = otherParticipant,
                        lastMessage = lastMessagePreview,
                        timestamp = timestamp
                    )
                }
                .sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting recent chats: ${e.message}", e)
            emptyList()
        }
    }

    data class RecentChat(
        val chatId: String,
        val participantEmail: String,
        val lastMessage: String,
        val timestamp: Long
    )

    companion object {
        @Volatile
        private var instance: InboxManager? = null

        fun getInstance(): InboxManager {
            return instance ?: synchronized(this) {
                instance ?: InboxManager().also { instance = it }
            }
        }
    }
} 