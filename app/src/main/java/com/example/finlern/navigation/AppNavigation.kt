package com.example.finlern.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.finlern.components.AdminCourseScreenLayout
import com.example.finlern.components.CourseScreenLayout
import com.example.finlern.data.DeveloperModeManager
import com.example.finlern.data.FirebaseAuthManager
import com.example.finlern.screens.AIAssistantScreen
import com.example.finlern.screens.ChatScreen
import com.example.finlern.screens.CourseSelectionScreen
import com.example.finlern.screens.FinnishLevelScreen
import com.example.finlern.screens.InboxScreen
import com.example.finlern.screens.ManageStudentsScreen
import com.example.finlern.screens.PrivacyPolicyScreen
import com.example.finlern.screens.SettingsScreen
import com.example.finlern.screens.SignUpScreen
import com.example.finlern.screens.UserProfileScreen
import com.example.finlern.screens.WelcomeScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "welcome"
    ) {
        composable(route = "welcome") {
            WelcomeScreen(
                onNavigateToSignUp = {
                    navController.navigate("signup") {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
        
        composable(route = "signup") {
            SignUpScreen(
                onSignUpSuccess = {
                    navController.navigate("finnish_level") {
                        popUpTo("welcome") { inclusive = true }
                    }
                },
                onBackToWelcome = {
                    navController.navigateUp()
                },
                onNavigateToCourse = { level ->
                    navController.navigate("course/$level") {
                        popUpTo("welcome") { inclusive = true }
                    }
                },
                onNavigateToCourseSelection = {
                    navController.navigate("course_selection") {
                        popUpTo("welcome") { inclusive = true }
                    }
                }
            )
        }

        composable(route = "finnish_level") {
            FinnishLevelScreen { level ->
                navController.navigate("course/$level") {
                    popUpTo("welcome") { inclusive = true }
                }
            }
        }

        composable(route = "course_selection") {
            CourseSelectionScreen { level ->
                navController.navigate("course/$level") {
                    launchSingleTop = true
                }
            }
        }

        composable("settings") {
            SettingsScreen(
                onNavigateBack = { navController.navigateUp() },
                onNavigateToLogin = {
                    navController.navigate("signup") {
                        popUpTo(navController.graph.id) {
                            inclusive = true
                        }
                    }
                }
            )
        }

        composable(
            route = "course/{level}",
            arguments = listOf(navArgument("level") { type = NavType.StringType })
        ) { backStackEntry ->
            val level = backStackEntry.arguments?.getString("level") ?: return@composable
            val currentUser = FirebaseAuthManager.getCurrentUser()
            val email = currentUser?.email ?: ""
            
            Box(modifier = Modifier.fillMaxSize()) {
                if (!DeveloperModeManager.shouldShowStudentView(email)) {
                    // Show admin view
                    when (level) {
                        "Beginner" -> AdminCourseScreenLayout(
                            courseName = "Beginner Course",
                            onNavigateBack = { navController.navigateUp() },
                            onNavigateToProfile = { 
                                navController.navigate("userProfile") {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onNavigateToStudents = { 
                                navController.navigate("manageStudents") {
                                    launchSingleTop = true
                                }
                            },
                            onNavigateToAIAssistant = {
                                navController.navigate("aiAssistant") {
                                    launchSingleTop = true
                                }
                            },
                            onNavigateToInbox = { 
                                navController.navigate("inbox") {
                                    launchSingleTop = true
                                }
                            },
                            onNavigateToSettings = { 
                                navController.navigate("settings") {
                                    launchSingleTop = true
                                }
                            },
                            onNavigateToPrivacyPolicy = {
                                navController.navigate("privacyPolicy") {
                                    launchSingleTop = true
                                }
                            }
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                // Course content here
                            }
                        }
                        "Amateur" -> AdminCourseScreenLayout(
                            courseName = "Amateur Course",
                            onNavigateBack = { navController.navigateUp() },
                            onNavigateToProfile = { 
                                navController.navigate("userProfile") {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onNavigateToStudents = { 
                                navController.navigate("manageStudents") {
                                    launchSingleTop = true
                                }
                            },
                            onNavigateToAIAssistant = {
                                navController.navigate("aiAssistant") {
                                    launchSingleTop = true
                                }
                            },
                            onNavigateToInbox = { 
                                navController.navigate("inbox") {
                                    launchSingleTop = true
                                }
                            },
                            onNavigateToSettings = { navController.navigate("settings") },
                            onNavigateToPrivacyPolicy = {
                                navController.navigate("privacyPolicy") {
                                    launchSingleTop = true
                                }
                            }
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                // Course content here
                            }
                        }
                        "Intermediate" -> AdminCourseScreenLayout(
                            courseName = "Intermediate Course",
                            onNavigateBack = { navController.navigateUp() },
                            onNavigateToProfile = { 
                                navController.navigate("userProfile") {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onNavigateToStudents = { 
                                navController.navigate("manageStudents") {
                                    launchSingleTop = true
                                }
                            },
                            onNavigateToAIAssistant = {
                                navController.navigate("aiAssistant") {
                                    launchSingleTop = true
                                }
                            },
                            onNavigateToInbox = { 
                                navController.navigate("inbox") {
                                    launchSingleTop = true
                                }
                            },
                            onNavigateToSettings = { navController.navigate("settings") },
                            onNavigateToPrivacyPolicy = {
                                navController.navigate("privacyPolicy") {
                                    launchSingleTop = true
                                }
                            }
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                // Course content here
                            }
                        }
                    }
                } else {
                    // Show student view
                    when (level) {
                        "Beginner" -> CourseScreenLayout(
                            courseName = "Beginner Course",
                            onNavigateBack = { navController.navigateUp() },
                            onNavigateToProfile = { navController.navigate("userProfile") },
                            onNavigateToSettings = { 
                                navController.navigate("settings") {
                                    launchSingleTop = true
                                }
                            },
                            onNavigateToInbox = { 
                                navController.navigate("inbox") {
                                    launchSingleTop = true
                                }
                            },
                            onNavigateToAIAssistant = {
                                navController.navigate("aiAssistant") {
                                    launchSingleTop = true
                                }
                            },
                            onNavigateToPrivacyPolicy = {
                                navController.navigate("privacyPolicy") {
                                    launchSingleTop = true
                                }
                            }
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                // Course content here
                            }
                        }
                        "Amateur" -> CourseScreenLayout(
                            courseName = "Amateur Course",
                            onNavigateBack = { navController.navigateUp() },
                            onNavigateToProfile = { navController.navigate("userProfile") },
                            onNavigateToSettings = { 
                                navController.navigate("settings") {
                                    launchSingleTop = true
                                }
                            },
                            onNavigateToInbox = { 
                                navController.navigate("inbox") {
                                    launchSingleTop = true
                                }
                            },
                            onNavigateToAIAssistant = {
                                navController.navigate("aiAssistant") {
                                    launchSingleTop = true
                                }
                            },
                            onNavigateToPrivacyPolicy = {
                                navController.navigate("privacyPolicy") {
                                    launchSingleTop = true
                                }
                            }
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                // Course content here
                            }
                        }
                        "Intermediate" -> CourseScreenLayout(
                            courseName = "Intermediate Course",
                            onNavigateBack = { navController.navigateUp() },
                            onNavigateToProfile = { 
                                navController.navigate("userProfile") {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onNavigateToSettings = { 
                                navController.navigate("settings") {
                                    launchSingleTop = true
                                }
                            },
                            onNavigateToInbox = { 
                                navController.navigate("inbox") {
                                    launchSingleTop = true
                                }
                            },
                            onNavigateToAIAssistant = {
                                navController.navigate("aiAssistant") {
                                    launchSingleTop = true
                                }
                            },
                            onNavigateToPrivacyPolicy = {
                                navController.navigate("privacyPolicy") {
                                    launchSingleTop = true
                                }
                            }
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                // Course content here
                            }
                        }
                    }
                }
            }
        }

        composable("userProfile") {
            val currentUser = FirebaseAuthManager.getCurrentUser()
            currentUser?.email ?: ""
            
            UserProfileScreen(
                onNavigateBack = { navController.navigateUp() }
            )
        }

        composable("manageStudents") {
            ManageStudentsScreen(
                onNavigateBack = { navController.navigateUp() }
            )
        }

        composable(
            route = "chat/{chatId}",
            arguments = listOf(navArgument("chatId") { type = NavType.StringType })
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId") ?: return@composable
            ChatScreen(
                chatId = chatId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToInbox = {
                    navController.navigate("inbox") {
                        popUpTo("inbox") {
                            inclusive = false
                        }
                    }
                }
            )
        }

        composable("inbox") {
            InboxScreen(
                onNavigateBack = { navController.navigateUp() },
                onNavigateToChat = { chatId ->
                    navController.navigate("chat/$chatId")
                }
            )
        }

        composable("aiAssistant") {
            AIAssistantScreen(
                onNavigateBack = { navController.navigateUp() }
            )
        }

        composable("privacyPolicy") {
            PrivacyPolicyScreen(
                onNavigateBack = { navController.navigateUp() }
            )
        }
    }
} 