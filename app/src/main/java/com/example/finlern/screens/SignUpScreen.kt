package com.example.finlern.screens

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.finlern.data.AuthorizedEmails
import com.example.finlern.data.FirebaseAuthManager
import com.example.finlern.data.UserLevelManager
import com.example.finlern.ui.theme.AuroraBlue1
import com.example.finlern.ui.theme.AuroraBlue2
import com.example.finlern.ui.theme.AuroraPink
import com.example.finlern.ui.theme.FinLernBackground
import kotlinx.coroutines.launch

private const val TAG = "SignUpScreen"

@Composable
fun SignUpScreen(
    onSignUpSuccess: () -> Unit,
    onBackToWelcome: () -> Unit,
    onNavigateToCourse: (String) -> Unit,
    onNavigateToCourseSelection: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoginMode by remember { mutableStateOf(true) }
    
    val coroutineScope = rememberCoroutineScope()

    // Add animation for the form elements
    val formScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "formScale"
    )

    LaunchedEffect(Unit) {
        Log.d(TAG, "Checking current user status")
        val currentUser = FirebaseAuthManager.getCurrentUser()
        if (currentUser != null) {
            val userEmail = currentUser.email!!
            Log.d(TAG, "Found logged in user: $userEmail")
            if (AuthorizedEmails.isAdminOrTeacher(userEmail)) {
                Log.d(TAG, "User is admin/teacher, navigating to course selection")
                onNavigateToCourseSelection()
            } else {
                Log.d(TAG, "User is student, checking level")
                val level = UserLevelManager.getInstance().getUserLevel(userEmail)
                if (level != null) {
                    Log.d(TAG, "Found level $level, navigating to course")
                    onNavigateToCourse(level)
                } else {
                    Log.d(TAG, "No level found, navigating to level selection")
                    onSignUpSuccess()
                }
            }
        } else {
            Log.d(TAG, "No user logged in")
        }
    }

    fun handleAuthResult(result: Result<Unit>, isLoginMode: Boolean) {
        result.fold(
            onSuccess = {
                isLoading = false
                val userEmail = FirebaseAuthManager.getCurrentUser()?.email
                if (userEmail != null) {
                    if (isLoginMode) {
                        // For login, check user type and existing level
                        if (AuthorizedEmails.isAdminOrTeacher(userEmail)) {
                            onNavigateToCourseSelection()
                        } else {
                            coroutineScope.launch {
                                val level = UserLevelManager.getInstance().getUserLevel(userEmail)
                                if (level != null) {
                                    onNavigateToCourse(level)
                                } else {
                                    // This shouldn't happen for existing users, but just in case
                                    onSignUpSuccess()
                                }
                            }
                        }
                    } else {
                        // For new sign-ups, always go to level selection
                        onSignUpSuccess()
                    }
                }
            },
            onFailure = { e ->
                isLoading = false
                errorMessage = when {
                    isLoginMode && e.message?.contains("user-not-found") == true ->
                        "No account found. Please sign up."
                    !isLoginMode && e.message?.contains("email-already-in-use") == true ->
                        "Account already exists. Please login."
                    else -> e.message ?: "Authentication failed"
                }
                showError = true
            }
        )
    }

    FinLernBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
                .scale(formScale),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Enhanced title with animation
            Text(
                text = if (isLoginMode) "Welcome Back" else "Join FinLern",
                style = MaterialTheme.typography.headlineLarge.copy(
                    color = Color.White,
                    shadow = Shadow(
                        color = AuroraBlue1.copy(alpha = 0.7f),
                        offset = Offset(2f, 2f),
                        blurRadius = 4f
                    ),
                    fontSize = 40.sp,
                    letterSpacing = 1.sp
                ),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Subtitle
            Text(
                text = if (isLoginMode) 
                    "Continue your educational journey!" 
                else 
                    "Start your educational journey!",
                style = TextStyle(
                    fontSize = 18.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    letterSpacing = 0.5.sp,
                    textAlign = TextAlign.Center,
                    shadow = Shadow(
                        color = AuroraBlue2.copy(alpha = 0.3f),
                        offset = Offset(1f, 1f),
                        blurRadius = 2f
                    )
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            )

            // Enhanced email field
            OutlinedTextField(
                value = email,
                onValueChange = { 
                    email = it
                    showError = false 
                },
                label = { Text("Email", color = Color.White.copy(alpha = 0.7f)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                isError = showError,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AuroraBlue2,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = AuroraBlue2,
                    focusedContainerColor = Color(0xFF1A1A2F),
                    unfocusedContainerColor = Color(0xFF1A1A2F),
                    focusedLabelColor = AuroraBlue2,
                    unfocusedLabelColor = Color.White.copy(alpha = 0.7f)
                ),
                shape = MaterialTheme.shapes.medium
            )

            // Enhanced password field with similar styling
            OutlinedTextField(
                value = password,
                onValueChange = { 
                    password = it
                    showError = false 
                },
                label = { Text("Password", color = Color.White.copy(alpha = 0.7f)) },
                visualTransformation = if (passwordVisible) VisualTransformation.None 
                                     else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility
                                        else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" 
                                               else "Show password",
                            tint = Color.White.copy(alpha = 0.7f)
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                isError = showError,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AuroraBlue2,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = AuroraBlue2,
                    focusedContainerColor = Color(0xFF1A1A2F),
                    unfocusedContainerColor = Color(0xFF1A1A2F)
                )
            )

            // Enhanced error message display
            AnimatedVisibility(
                visible = showError,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Row(
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .background(
                            AuroraPink.copy(alpha = 0.1f),
                            shape = MaterialTheme.shapes.small
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = AuroraPink,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = errorMessage,
                        color = AuroraPink,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Enhanced button with animation
            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        showError = true
                        errorMessage = "Please fill in all fields"
                        return@Button
                    }

                    if (!isLoginMode && !AuthorizedEmails.isEmailAuthorized(email)) {
                        showError = true
                        errorMessage = "You haven't enrolled in the course yet. Please contact the administrator."
                        return@Button
                    }

                    coroutineScope.launch {
                        isLoading = true
                        try {
                            val result = if (isLoginMode) {
                                FirebaseAuthManager.signInWithEmailAndPassword(email, password)
                            } else {
                                FirebaseAuthManager.createUserWithEmailAndPassword(email, password)
                            }
                            
                            handleAuthResult(result, isLoginMode)
                        } catch (e: Exception) {
                            isLoading = false
                            showError = true
                            errorMessage = "Authentication failed: ${e.message}"
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(horizontal = 8.dp),
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AuroraBlue1,
                    disabledContainerColor = AuroraBlue1.copy(alpha = 0.5f)
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 6.dp,
                    pressedElevation = 8.dp
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                } else {
                    Text(
                        if (isLoginMode) "Login" else "Sign Up",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.25f),
                                offset = Offset(1f, 1f),
                                blurRadius = 2f
                            )
                        )
                    )
                }
            }

            // Enhanced toggle button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isLoginMode) 
                        "Don't have an account? " 
                    else 
                        "Already have an account? ",
                    color = Color.White.copy(alpha = 0.8f)
                )
                TextButton(
                    onClick = { isLoginMode = !isLoginMode },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = AuroraBlue2
                    )
                ) {
                    Text(
                        if (isLoginMode) "Sign Up" else "Login",
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(
                            fontSize = 16.sp,
                            letterSpacing = 0.5.sp
                        )
                    )
                }
            }

            // Enhanced back button
            Button(
                onClick = onBackToWelcome,
                modifier = Modifier
                    .padding(top = 24.dp)
                    .fillMaxWidth(0.9f)
                    .height(60.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1A1A2F).copy(alpha = 0.8f),
                    contentColor = Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 6.dp,
                    hoveredElevation = 8.dp
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = AuroraBlue2.copy(alpha = 0.3f)
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.padding(end = 8.dp),
                        tint = Color.White.copy(alpha = 0.9f)
                    )
                    Text(
                        "Back to Welcome Screen",
                        style = TextStyle(
                            fontSize = 12.sp,
                            letterSpacing = 0.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }
    }
}

