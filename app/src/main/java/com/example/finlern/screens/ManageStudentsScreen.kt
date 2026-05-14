package com.example.finlern.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.finlern.R
import com.example.finlern.data.UserLevelManager
import com.example.finlern.ui.theme.AuroraBlue1
import com.example.finlern.ui.theme.FinLernBackground
import kotlinx.coroutines.launch

data class StudentInfo(
    val email: String,
    val level: String,
    val profilePictureUrl: String = ""
)

@Composable
fun ManageStudentsScreen(
    onNavigateBack: () -> Unit
) {
    var students by remember { mutableStateOf<List<StudentInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()
    
    // Load students when screen is created
    LaunchedEffect(Unit) {
        students = UserLevelManager.getInstance().getAllStudents()
        isLoading = false
    }

    FinLernBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 16.dp)
        ) {
            // Header with back button
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .align(Alignment.Start)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            Text(
                text = "Manage Students",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AuroraBlue1)
                }
            } else {
                LazyColumn {
                    items(students) { student ->
                        StudentCard(
                            student = student,
                            onLevelChange = { newLevel ->
                                coroutineScope.launch {
                                    UserLevelManager.getInstance().setUserLevel(student.email, newLevel)
                                    // Refresh the list after updating
                                    students = UserLevelManager.getInstance().getAllStudents()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentCard(
    student: StudentInfo,
    onLevelChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val levels = listOf("Beginner", "Amateur", "Intermediate")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Student Email
            Text(
                text = student.email,
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Current Level
            Text(
                text = "Current Level: ${student.level}",
                color = AuroraBlue1,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Profile Picture
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(student.profilePictureUrl.ifEmpty { R.drawable.profile_placeholder })
                    .crossfade(true)
                    .placeholder(R.drawable.profile_placeholder)
                    .error(R.drawable.profile_placeholder)
                    .build(),
                contentDescription = "Profile picture",
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f)),
                contentScale = ContentScale.Crop
            )

            // Change Level Button
            Box {
                Button(
                    onClick = { expanded = true },
                    modifier = Modifier
                        .height(55.dp)
                        .padding(top = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AuroraBlue1,
                        contentColor = Color.White
                    ),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        "Update Finnish Level",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier
                        .background(Color(0xFF2E3440))
                ) {
                    levels.forEach { level ->
                        DropdownMenuItem(
                            text = { Text(level, color = Color.White) },
                            onClick = {
                                onLevelChange(level)
                                expanded = false
                            },
                            colors = MenuDefaults.itemColors(
                                textColor = Color.White,
                                leadingIconColor = Color.White,
                                trailingIconColor = Color.White,
                                disabledTextColor = Color.White.copy(alpha = 0.5f),
                                disabledLeadingIconColor = Color.White.copy(alpha = 0.5f),
                                disabledTrailingIconColor = Color.White.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }
        }
    }
}