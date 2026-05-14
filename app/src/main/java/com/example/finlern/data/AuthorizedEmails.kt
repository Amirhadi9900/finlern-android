package com.example.finlern.data

object AuthorizedEmails {
    private val adminEmails = setOf(
        "h.borji79@gmail.com"
    )

    private val teacherEmails = setOf<String>(
        // Add teacher emails here later
    )

    fun isEmailAuthorized(email: String): Boolean {
        val normalizedEmail = email.lowercase().trim()
        return adminEmails.contains(normalizedEmail) || 
               teacherEmails.contains(normalizedEmail) || 
               isStudent(normalizedEmail)
    }

    fun isAdminOrTeacher(email: String): Boolean {
        val normalizedEmail = email.lowercase().trim()
        return adminEmails.contains(normalizedEmail) || 
               teacherEmails.contains(normalizedEmail)
    }

    private fun isStudent(email: String): Boolean {
        val normalizedEmail = email.lowercase().trim()
        // Return false if it's an admin or teacher
        return !isAdminOrTeacher(normalizedEmail)
    }

}