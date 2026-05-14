package com.example.finlern.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.finlern.data.AuthorizedEmails
import com.example.finlern.data.FirebaseAuthManager
import com.example.finlern.data.UserProfile
import com.example.finlern.data.UserProfileManager
import com.example.finlern.ui.theme.AuroraBlue1
import com.example.finlern.ui.theme.AuroraBlue2
import com.example.finlern.ui.theme.FinLernBackground
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    onNavigateBack: () -> Unit
) {
    var userProfile by remember { mutableStateOf<UserProfile?>(null) }
    var isEditing by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser = FirebaseAuthManager.getCurrentUser()
    val isAdminOrTeacher = remember {
        currentUser?.email?.let { email ->
            AuthorizedEmails.isAdminOrTeacher(email)
        } ?: false
    }
    
    // Load user profile
    LaunchedEffect(currentUser?.email) {
        currentUser?.email?.let { email ->
            isLoading = true
            userProfile = UserProfileManager.getInstance().getUserProfile(email)
            name = userProfile?.name ?: ""
            bio = userProfile?.bio ?: ""
            isLoading = false
        }
    }

    // Save profile function
    val saveProfile = {
        scope.launch {
            currentUser?.email?.let { email ->
                isSaving = true
                val success = UserProfileManager.getInstance().updateProfile(
                    email = email,
                    name = name,
                    bio = bio
                )
                if (success) {
                    isEditing = false
                    // Reload profile to show updated data
                    userProfile = UserProfileManager.getInstance().getUserProfile(email)
                } else {
                    // Show error message
                    Toast.makeText(context, "Failed to save profile", Toast.LENGTH_SHORT).show()
                }
                isSaving = false
            }
        }
        Unit
    }

    // Image picker launcher with loading state
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { 
            imageUri = it
            scope.launch {
                isLoading = true
                currentUser?.email?.let { email ->
                    val url = UserProfileManager.getInstance().uploadProfilePicture(email, it)
                    if (url != null) {
                        // Reload profile to show updated picture
                        userProfile = UserProfileManager.getInstance().getUserProfile(email)
                    } else {
                        Toast.makeText(context, "Failed to upload image", Toast.LENGTH_SHORT).show()
                    }
                }
                isLoading = false
            }
        }
    }

    // Move the camera launcher into a proper composable variable
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            imageUri?.let { uri ->
                scope.launch {
                    isLoading = true
                    currentUser?.email?.let { email ->
                        val url = UserProfileManager.getInstance().uploadProfilePicture(email, uri)
                        if (url != null) {
                            // Reload profile to show updated picture
                            userProfile = UserProfileManager.getInstance().getUserProfile(email)
                        } else {
                            Toast.makeText(context, "Failed to upload image", Toast.LENGTH_SHORT).show()
                        }
                    }
                    isLoading = false
                }
            }
        }
    }

    FinLernBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Profile") },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            titleContentColor = Color.White,
                            navigationIconContentColor = Color.White
                        )
                    )
                },
                containerColor = Color.Transparent
            ) { paddingValues ->
                // Wrap existing content with padding
                Box(modifier = Modifier.padding(paddingValues)) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Profile Picture Section
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(240.dp)
                                    .clip(CircleShape)
                                    .background(AuroraBlue2.copy(alpha = 0.2f))
                                    .border(3.dp, AuroraBlue1, CircleShape)
                                    .clickable {
                                        imagePickerLauncher.launch("image/*")
                                    }
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(imageUri ?: userProfile?.profilePictureUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Profile picture",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        
                        if (!userProfile?.profilePictureUrl.isNullOrEmpty()) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        isLoading = true
                                        currentUser?.email?.let { email ->
                                            val success = UserProfileManager.getInstance().removeProfilePicture(email)
                                            if (success) {
                                                userProfile = UserProfileManager.getInstance().getUserProfile(email)
                                            } else {
                                                Toast.makeText(context, "Failed to remove image", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        isLoading = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Blue
                                ),
                                modifier = Modifier
                                    .padding(top = 14.dp)
                                    .height(46.dp),
                                border = BorderStroke(1.dp, AuroraBlue1)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Remove profile picture",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Remove Picture",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(20.dp))

                        // Profile Information
                        if (isEditing) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Name") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = AuroraBlue1,
                                    unfocusedBorderColor = AuroraBlue2,
                                    focusedLabelColor = AuroraBlue1,
                                    unfocusedLabelColor = AuroraBlue2,
                                    cursorColor = AuroraBlue1
                                )
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedTextField(
                                value = bio,
                                onValueChange = { bio = it },
                                label = { Text("Bio") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = AuroraBlue1,
                                    unfocusedBorderColor = AuroraBlue2,
                                    focusedLabelColor = AuroraBlue1,
                                    unfocusedLabelColor = AuroraBlue2,
                                    cursorColor = AuroraBlue1
                                )
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                TextButton(
                                    onClick = { isEditing = false }
                                ) {
                                    Text("Cancel", color = Color.White)
                                }

                                Button(
                                    onClick = saveProfile,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AuroraBlue1
                                    )
                                ) {
                                    Text("Save", color = Color.Black)
                                }
                            }
                        } else {
                            ProfileInfoCard("Name", name.ifEmpty { "Not set" }) {
                                isEditing = true
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            ProfileInfoCard("Bio", bio.ifEmpty { "Not set" }) {
                                isEditing = true
                            }
                        }
                    }

                    if (isLoading || isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = AuroraBlue1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileInfoCard(
    label: String,
    value: String,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = AuroraBlue2.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = AuroraBlue1
                    )
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
        }
    }
} 