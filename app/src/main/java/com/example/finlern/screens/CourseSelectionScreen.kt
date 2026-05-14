package com.example.finlern.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.dp
import com.example.finlern.ui.theme.AuroraBlue1
import com.example.finlern.ui.theme.AuroraBlue2
import com.example.finlern.ui.theme.FinLernBackground

@Composable
fun CourseSelectionScreen(
    onCourseSelected: (String) -> Unit
) {
    FinLernBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Select Course to View",
                style = MaterialTheme.typography.headlineMedium.copy(
                    color = Color.White,
                    shadow = Shadow(
                        color = AuroraBlue1.copy(alpha = 0.7f),
                        offset = Offset(2f, 2f),
                        blurRadius = 4f
                    )
                ),
                modifier = Modifier.padding(bottom = 32.dp)
            )

            CourseButton("Beginner Course") { onCourseSelected("Beginner") }
            CourseButton("Amateur Course") { onCourseSelected("Amateur") }
            CourseButton("Intermediate Course") { onCourseSelected("Intermediate") }
        }
    }
}

@Composable
private fun CourseButton(
    text: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .padding(vertical = 8.dp)
            .fillMaxWidth(0.8f)
            .height(72.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AuroraBlue2
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(
                color = Color.White
            )
        )
    }
} 