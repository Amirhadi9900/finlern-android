package com.example.finlern.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.finlern.data.FirebaseAuthManager
import com.example.finlern.data.UserLevelManager
import com.example.finlern.ui.theme.AuroraBlue1
import com.example.finlern.ui.theme.AuroraBlue2
import com.example.finlern.ui.theme.AuroraPink
import com.example.finlern.ui.theme.FinLernBackground
import kotlinx.coroutines.launch

@Composable
fun FinnishLevelScreen(
    onLevelSelected: (String) -> Unit
) {
    var selectedLevel by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    FinLernBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Select Your Finnish Level",
                style = MaterialTheme.typography.headlineMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                modifier = Modifier.padding(bottom = 32.dp)
            )

            Text(
                text = "Important: This choice cannot be changed later without contacting the administrator",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = AuroraPink.copy(alpha = 0.9f)
                ),
                modifier = Modifier.padding(bottom = 24.dp)
            )

            LevelButton("Beginner", selectedLevel) { selectedLevel = "Beginner" }
            LevelButton("Amateur", selectedLevel) { selectedLevel = "Amateur" }
            LevelButton("Intermediate", selectedLevel) { selectedLevel = "Intermediate" }

            if (selectedLevel != null) {
                Button(
                    onClick = {
                        val email = FirebaseAuthManager.getCurrentUser()?.email
                        val level = selectedLevel
                        if (email == null || level == null) {
                            Toast.makeText(
                                context,
                                "You are not signed in. Please log in again.",
                                Toast.LENGTH_LONG
                            ).show()
                            return@Button
                        }
                        isSaving = true
                        coroutineScope.launch {
                            val success = UserLevelManager.getInstance().setUserLevel(email, level)
                            isSaving = false
                            if (success) {
                                onLevelSelected(level)
                            } else {
                                // Surface the failure instead of silently navigating onward
                                // (this is what hid the original bug for so long).
                                Toast.makeText(
                                    context,
                                    "Could not save your level. Check your connection and try again.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier
                        .padding(top = 32.dp)
                        .fillMaxWidth(0.8f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AuroraBlue1
                    )
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.height(24.dp)
                        )
                    } else {
                        Text(
                            "Confirm Selection",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LevelButton(
    level: String,
    selectedLevel: String?,
    onSelect: () -> Unit
) {
    Button(
        onClick = onSelect,
        modifier = Modifier
            .padding(vertical = 8.dp)
            .fillMaxWidth(0.8f)
            .height(72.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selectedLevel == level) 
                AuroraBlue2 
            else 
                Color(0xFF1A1A2F).copy(alpha = 0.8f)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (selectedLevel == level) 
                AuroraBlue1 
            else 
                AuroraBlue2.copy(alpha = 0.3f)
        )
    ) {
        Text(
            level,
            style = MaterialTheme.typography.titleLarge.copy(
                color = Color.White,
                fontWeight = if (selectedLevel == level) 
                    FontWeight.Bold 
                else 
                    FontWeight.Normal
            )
        )
    }
} 