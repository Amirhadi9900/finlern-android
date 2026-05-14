package com.example.finlern.screens

import androidx.compose.runtime.Composable
import com.example.finlern.components.CourseScreenLayout

@Composable
fun IntermediateCourseScreen(
    onNavigateBack: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToInbox: () -> Unit,
    onNavigateToAIAssistant: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit
) {
    CourseScreenLayout(
        courseName = "Intermediate Course",
        onNavigateBack = onNavigateBack,
        onNavigateToProfile = onNavigateToProfile,
        onNavigateToInbox = onNavigateToInbox,
        onNavigateToAIAssistant = onNavigateToAIAssistant,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToPrivacyPolicy = onNavigateToPrivacyPolicy
    ) {
        // Course content
    }
} 