package com.example.finlern.data

data class UserProfile(
    val email: String = "",
    val name: String = "",
    val bio: String = "",
    val profilePictureUrl: String = "",
    val finnishLevel: String = "",
    val fcmToken: String? = null
)