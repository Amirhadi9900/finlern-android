package com.example.finlern.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.finlern.ui.theme.AuroraBlue2
import com.example.finlern.ui.theme.AuroraBlue3
import com.example.finlern.ui.theme.AuroraGreen3
import com.example.finlern.ui.theme.AuroraPink2
import com.example.finlern.ui.theme.AuroraTeal
import com.example.finlern.ui.theme.AuroraViolet
import com.example.finlern.ui.theme.AuroraYellow
import com.example.finlern.ui.theme.NightSky
import com.example.finlern.ui.theme.StarColor
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun WelcomeScreen(
    onNavigateToSignUp: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NightSky)
    ) {
        // Stars background with more stars
        StarsEffect(starCount = 200)
        
        // Multiple aurora layers with different effects
        repeat(5) { index ->  // Increased from 3 to 5 layers
            NorthernLightsEffect(
                index = index,
                blur = (20 + index * 15).dp  // Adjusted blur values
            )
        }
        
        // Welcome Text with animations
        AnimatedWelcomeText(onNavigateToSignUp = onNavigateToSignUp)
    }
}

@Composable
private fun AnimatedWelcomeText(
    onNavigateToSignUp: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Main title with corrected styling
        Text(
            text = "FinLern",
            style = TextStyle(
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                shadow = Shadow(
                    color = Color(0xFF4B0082).copy(alpha = 0.5f),
                    offset = Offset(2f, 2f),
                    blurRadius = 3f
                )
            ),
            color = Color(0xFFE6E6FA), // Lavender color
            modifier = Modifier
                .padding(bottom = 24.dp)
                .scale(1.1f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Subtitle with corrected styling
        Text(
            text = "Your exciting journey with FinLern starts here...",
            style = TextStyle(
                fontSize = 32.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
                textAlign = TextAlign.Center,
                shadow = Shadow(
                    color = Color(0xFF483D8B).copy(alpha = 0.3f),
                    offset = Offset(1f, 1f),
                    blurRadius = 2f
                )
            ),
            color = Color(0xFFFFFAFA), // Snow White color
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 48.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Button with corrected text styling
        Button(
            onClick = onNavigateToSignUp,
            modifier = Modifier
                .height(70.dp)
                .fillMaxWidth(0.8f),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4169E1).copy(alpha = 0.9f)  // Royal Blue
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 6.dp,
                pressedElevation = 8.dp,
                hoveredElevation = 8.dp
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = "Aloita Matka",
                style = TextStyle(
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.25f),
                        offset = Offset(1f, 1f),
                        blurRadius = 2f
                    )
                ),
                color = Color.White
            )
        }
    }
}

@Composable
fun StarsEffect(starCount: Int = 200) {  // Increased star count
    val stars = remember {
        List(starCount) {
            Triple(
                Random.nextFloat(),
                Random.nextFloat(),
                Random.nextFloat() * 0.8f + 0.2f
            )
        }
    }
    
    // Additional twinkle animation
    val twinkleTransition = rememberInfiniteTransition(label = "twinkle")
    val twinkleScale by twinkleTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "twinkleScale"
    )

    val starAlpha by twinkleTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.9f,  // Increased maximum brightness
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "starAlpha"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        stars.forEach { (x, y, scale) ->
            drawCircle(
                color = StarColor.copy(alpha = starAlpha * scale),
                radius = 2.dp.toPx() * scale * twinkleScale,
                center = Offset(x * size.width, y * size.height)
            )
        }
    }
}

@Composable
fun NorthernLightsEffect(index: Int, blur: Dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "aurora$index")
    
    // Multiple animations for complex natural movement
    val primaryWave by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000 + index * 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "primaryWave$index"
    )

    val secondaryWave by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4500 + index * 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "secondaryWave$index"
    )

    val verticalShift by infiniteTransition.animateFloat(
        initialValue = -0.05f,
        targetValue = 0.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000 + index * 500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "verticalShift$index"
    )

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .blur(blur)
    ) {
        val width = size.width
        val height = size.height
        
        // Create multiple layers for each aurora for depth
        repeat(3) { layerIndex ->
            val path = Path().apply {
                val baseHeight = height * (0.1f + index * 0.08f)
                val layerOffset = layerIndex * 0.03f
                
                moveTo(0f, baseHeight + height * layerOffset)
                
                // Add multiple control points for more natural wave movement
                val segments = 4
                for (i in 0..segments) {
                    val x = width * (i.toFloat() / segments)
                    val phase = (i.toFloat() / segments + primaryWave + layerIndex * 0.5f) * Math.PI.toFloat()
                    val secondaryPhase = (i.toFloat() / segments + secondaryWave) * Math.PI.toFloat() * 2
                    
                    val waveHeight = (sin(phase) * 0.08f + sin(secondaryPhase) * 0.04f) * height
                    val y = baseHeight + waveHeight + verticalShift * height + layerOffset * height
                    
                    if (i == 0) {
                        moveTo(x, y)
                    } else {
                        val prevX = width * ((i - 1).toFloat() / segments)
                        val prevPhase = ((i - 1).toFloat() / segments + primaryWave + layerIndex * 0.5f) * Math.PI.toFloat()
                        val prevSecondaryPhase = ((i - 1).toFloat() / segments + secondaryWave) * Math.PI.toFloat() * 2
                        val prevWaveHeight = (sin(prevPhase) * 0.08f + sin(prevSecondaryPhase) * 0.04f) * height
                        val prevY = baseHeight + prevWaveHeight + verticalShift * height + layerOffset * height
                        
                        val controlX1 = prevX + (x - prevX) * 0.5f
                        val controlY1 = prevY + (y - prevY) * (0.3f + sin(phase) * 0.2f)
                        val controlX2 = prevX + (x - prevX) * 0.5f
                        val controlY2 = y - (y - prevY) * (0.3f + sin(phase) * 0.2f)
                        
                        cubicTo(controlX1, controlY1, controlX2, controlY2, x, y)
                    }
                }
            }

            // Adjusted alpha values for better visibility against dark sky
            drawPath(
                path = path,
                brush = Brush.verticalGradient(
                    colors = when (index % 5) {
                        0 -> listOf(
                            AuroraGreen3.copy(alpha = (0.8f - layerIndex * 0.15f)),
                            AuroraBlue3.copy(alpha = (0.5f - layerIndex * 0.1f)),
                            AuroraViolet.copy(alpha = (0.3f - layerIndex * 0.05f))
                        )
                        1 -> listOf(
                            AuroraBlue2.copy(alpha = (0.6f - layerIndex * 0.15f)),
                            AuroraTeal.copy(alpha = (0.3f - layerIndex * 0.1f)),
                            AuroraPink2.copy(alpha = (0.2f - layerIndex * 0.05f))
                        )
                        2 -> listOf(
                            AuroraPink2.copy(alpha = (0.5f - layerIndex * 0.15f)),
                            AuroraViolet.copy(alpha = (0.3f - layerIndex * 0.1f)),
                            AuroraBlue3.copy(alpha = (0.2f - layerIndex * 0.05f))
                        )
                        3 -> listOf(
                            AuroraYellow.copy(alpha = (0.4f - layerIndex * 0.15f)),
                            AuroraGreen3.copy(alpha = (0.3f - layerIndex * 0.1f)),
                            AuroraTeal.copy(alpha = (0.2f - layerIndex * 0.05f))
                        )
                        else -> listOf(
                            AuroraTeal.copy(alpha = (0.5f - layerIndex * 0.15f)),
                            AuroraBlue2.copy(alpha = (0.3f - layerIndex * 0.1f)),
                            AuroraPink2.copy(alpha = (0.2f - layerIndex * 0.05f))
                        )
                    }
                )
            )
        }
    }
} 