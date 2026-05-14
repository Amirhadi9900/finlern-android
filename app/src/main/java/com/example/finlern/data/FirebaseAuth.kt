package com.example.finlern.data

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

object FirebaseAuthManager {
    private val auth = FirebaseAuth.getInstance()

    suspend fun signInWithEmailAndPassword(email: String, password: String): Result<Unit> {
        return try {
            auth.signInWithEmailAndPassword(email, password).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createUserWithEmailAndPassword(email: String, password: String): Result<Unit> {
        return try {
            auth.createUserWithEmailAndPassword(email, password).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        auth.signOut()
    }

    fun getCurrentUser() = auth.currentUser
} 