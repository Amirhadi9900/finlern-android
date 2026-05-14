package com.example.finlern.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun FinLernBackground(
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NightSky)
    ) {
        // Stars effect with more stars
        StarsEffect(starCount = 300)
        
        // Aurora effects at the top with enhanced visibility
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            repeat(5) { index ->
                EnhancedAuroraEffect(
                    index = index,
                    yOffset = -0.5f  // Move aurora effects higher up
                )
            }
        }

        // Lighter semi-transparent overlay for better aurora visibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF05021A).copy(alpha = 0.70f), // Reduced opacity
                            Color(0xFF05021A).copy(alpha = 0.60f)  // Reduced opacity
                        )
                    )
                )
        )

        content()
    }
}

@Composable
private fun StarsEffect(starCount: Int) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        repeat(starCount) {
            val x = Random.nextFloat() * size.width
            val y = Random.nextFloat() * size.height
            val radius = Random.nextFloat() * 2.5f + 1f  // Slightly larger stars
            drawCircle(
                color = Color.White.copy(alpha = Random.nextFloat() * 0.7f + 0.3f), // Brighter stars
                radius = radius,
                center = Offset(x, y)
            )
        }
    }
}

@Composable
private fun EnhancedAuroraEffect(index: Int, yOffset: Float = 0f) {
    // More vibrant colors with higher base alpha
    val colors = listOf(
        AuroraGreen3.copy(alpha = 0.8f),
        AuroraBlue3.copy(alpha = 0.8f),
        AuroraViolet.copy(alpha = 0.8f),
        AuroraTeal.copy(alpha = 0.8f),
        AuroraPink2.copy(alpha = 0.8f)
    )
    
    val infiniteTransition = rememberInfiniteTransition(label = "aurora")
    val phase = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000 + index * 500, easing = FastOutSlowInEasing), // Faster animation
            repeatMode = RepeatMode.Reverse // Smoother transition
        ),
        label = "phase"
    )

    // Add secondary wave animation for more dynamic movement
    val secondaryPhase = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(3000 + index * 300, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "secondaryPhase"
    )

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .blur(radius = (15 + index * 10).dp) // Reduced blur for sharper effects
    ) {
        val path = Path()
        val waveHeight = size.height * 0.15f  // Increased wave height
        val segments = 15  // More segments for smoother curves
        
        path.moveTo(0f, size.height * (0.2f + yOffset))
        
        for (i in 0..segments) {
            val x = size.width * i / segments
            val primaryWave = sin(x / size.width * 4 + phase.value)
            val secondaryWave = sin(x / size.width * 8 + secondaryPhase.value)
            val y = size.height * (0.2f + yOffset) + 
                   (primaryWave * 0.8f + secondaryWave * 0.2f) * waveHeight
            path.lineTo(x, y)
        }
        
        path.lineTo(size.width, size.height)
        path.lineTo(0f, size.height)
        path.close()

        // Multiple layers with different opacities for depth effect
        repeat(3) { layer ->
            drawPath(
                path = path,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        colors[index % colors.size]
                            .copy(alpha = 0.4f - layer * 0.1f),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = size.height * 0.7f
                )
            )
        }
    }
} 