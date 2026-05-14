package com.example.finlern.data

import android.util.Log
import com.example.finlern.screens.StudentInfo
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

private const val TAG = "UserLevelManager"

class UserLevelManager {
    private val db = FirebaseFirestore.getInstance()
    private val usersCollection = db.collection("userProfiles")

    suspend fun setUserLevel(email: String, level: String): Boolean {
        return try {
            Log.d(TAG, "Attempting to set level '$level' for user '$email'")
            val userDoc = usersCollection.document(email)
            userDoc.set(mapOf(
                "email" to email,
                "finnishLevel" to level,
                "timestamp" to System.currentTimeMillis()
            ), SetOptions.merge()).await()
            Log.d(TAG, "Successfully set level for user '$email'")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error setting level for user '$email': ${e.message}", e)
            e.printStackTrace()
            false
        }
    }

    suspend fun getUserLevel(email: String): String? {
        return try {
            Log.d(TAG, "Attempting to get level for user '$email'")
            val doc = usersCollection.document(email).get().await()
            if (doc.exists()) {
                val level = doc.getString("finnishLevel")
                Log.d(TAG, "Found level '$level' for user '$email'")
                level
            } else {
                Log.d(TAG, "No level found for user '$email'")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting level for user '$email': ${e.message}", e)
            e.printStackTrace()
            null
        }
    }

    suspend fun getAllStudents(): List<StudentInfo> {
        return try {
            Log.d(TAG, "Starting getAllStudents() function")
            val snapshot = usersCollection.get().await()
            Log.d(TAG, "Firestore query completed. Number of documents: ${snapshot.documents.size}")
            
            val students = snapshot.documents
                .mapNotNull { doc ->
                    val email = doc.getString("email")
                    // Only proceed if this is not an admin/teacher
                    if (email != null && !AuthorizedEmails.isAdminOrTeacher(email)) {
                        val level = doc.getString("finnishLevel") // Using "finnishLevel" here
                        val profilePictureUrl = doc.getString("profilePictureUrl") ?: ""
                        Log.d(TAG, "Processing document - Email: $email, Level: $level")
                        
                        if (level != null) {
                            Log.d(TAG, "Created StudentInfo object for email: $email with level: $level")
                            StudentInfo(email, level, profilePictureUrl)
                        } else {
                            Log.w(TAG, "Skipping document - Missing level. Email: $email")
                            null
                        }
                    } else {
                        null
                    }
                }
            
            Log.d(TAG, "Final students list size: ${students.size}")
            Log.d(TAG, "Students list content: ${students.joinToString { "${it.email}:${it.level}" }}")
            students
        } catch (e: Exception) {
            Log.e(TAG, "Error getting students: ${e.message}", e)
            Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
            emptyList()
        }
    }

    companion object {
        @Volatile
        private var instance: UserLevelManager? = null

        fun getInstance(): UserLevelManager {
            return instance ?: synchronized(this) {
                instance ?: UserLevelManager().also { instance = it }
            }
        }
    }
} 