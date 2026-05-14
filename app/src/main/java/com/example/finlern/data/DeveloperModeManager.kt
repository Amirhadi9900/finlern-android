package com.example.finlern.data

object DeveloperModeManager {
    private var isStudentView = false
    private var isDeveloperMode = false
    
    // Add your email here
    private const val DEVELOPER_EMAIL = "h.borji79@gmail.com"  // Replace with your actual email

    fun shouldShowStudentView(email: String): Boolean {
        return when {
            !AuthorizedEmails.isAdminOrTeacher(email) -> true // Always show student view for students
            isDeveloperMode && isStudentView && email == DEVELOPER_EMAIL -> true // Show student view only if developer mode is on and it's your email
            else -> false // Show admin view by default for admins/teachers
        }
    }

    fun canAccessDeveloperMode(email: String): Boolean {
        return email == DEVELOPER_EMAIL
    }
} 