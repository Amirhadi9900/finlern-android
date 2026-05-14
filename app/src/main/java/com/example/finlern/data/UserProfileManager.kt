package com.example.finlern.data

import android.net.Uri
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

class UserProfileManager {
    private val TAG = "UserProfileManager"
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val profilesCollection = db.collection("userProfiles")
    
    suspend fun getUserProfile(email: String): UserProfile? {
        return try {
            Log.d(TAG, "Attempting to get profile for email: $email")
            val doc = profilesCollection.document(email).get().await()
            
            if (doc.exists()) {
                val profile = doc.toObject(UserProfile::class.java)
                Log.d(TAG, "Found profile: $profile")
                profile
            } else {
                Log.d(TAG, "No profile found, creating new profile")
                val level = UserLevelManager.getInstance().getUserLevel(email)
                UserProfile(email = email, finnishLevel = level ?: "")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting profile: ${e.message}", e)
            null
        }
    }

    suspend fun updateProfile(
        email: String,
        name: String? = null,
        occupation: String? = null,
        bio: String? = null
    ): Boolean {
        return try {
            Log.d(TAG, "Updating profile for email: $email")
            Log.d(TAG, "Update values - name: $name, occupation: $occupation, bio: $bio")
            
            // First get the existing profile or create a new one
            val existingProfile = getUserProfile(email)
            Log.d(TAG, "Existing profile: $existingProfile")
            
            val updatedProfile = existingProfile?.copy(
                name = name ?: existingProfile.name,
                bio = bio ?: existingProfile.bio
            ) ?: UserProfile(
                email = email,
                name = name ?: "",
                bio = bio ?: "",
                finnishLevel = UserLevelManager.getInstance().getUserLevel(email) ?: ""
            )
            
            Log.d(TAG, "Updated profile to save: $updatedProfile")
            
            // Save the complete profile
            profilesCollection.document(email)
                .set(updatedProfile)
                .await()
            
            Log.d(TAG, "Profile successfully updated")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating profile: ${e.message}", e)
            e.printStackTrace()
            false
        }
    }

    suspend fun uploadProfilePicture(email: String, imageUri: Uri): String? {
        return try {
            Log.d(TAG, "Uploading profile picture for email: $email")
            
            // Create a reference to the profile pictures folder
            val storageRef = storage.reference
            val profilePicsRef = storageRef.child("profile_pictures")
            
            // Create a reference to the specific user's profile picture
            val userImageRef = profilePicsRef.child("$email.jpg")
            
            Log.d(TAG, "Attempting to upload to path: ${userImageRef.path}")
            
            // Upload the file
            userImageRef.putFile(imageUri).await()
            val url = userImageRef.downloadUrl.await().toString()
            
            Log.d(TAG, "Image uploaded successfully, URL: $url")
            
            // Update profile with new URL using merge option
            profilesCollection.document(email)
                .set(mapOf("profilePictureUrl" to url), SetOptions.merge())
                .await()
            
            Log.d(TAG, "Profile picture URL saved to profile")
            url
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading profile picture: ${e.message}", e)
            e.printStackTrace()
            null
        }
    }

    suspend fun removeProfilePicture(email: String): Boolean {
        return try {
            val ref = storage.reference.child("profile_pictures/$email.jpg")
            ref.delete().await()
            
            profilesCollection.document(email)
                .update("profilePictureUrl", "")
                .await()
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error removing profile picture: ${e.message}", e)
            false
        }
    }

    companion object {
        @Volatile
        private var instance: UserProfileManager? = null

        fun getInstance(): UserProfileManager {
            return instance ?: synchronized(this) {
                instance ?: UserProfileManager().also { instance = it }
            }
        }
    }
} 