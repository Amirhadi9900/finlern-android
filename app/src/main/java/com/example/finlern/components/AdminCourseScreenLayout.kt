package com.example.finlern.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.finlern.ui.theme.AuroraBlue1
import com.example.finlern.ui.theme.AuroraBlue2
import com.example.finlern.ui.theme.AuroraBlue3
import com.example.finlern.ui.theme.FinLernBackground
import kotlin.math.abs

@Composable
fun AdminCourseScreenLayout(
    courseName: String,
    onNavigateBack: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToStudents: () -> Unit,
    onNavigateToAIAssistant: () -> Unit,
    onNavigateToInbox: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit,
    content: @Composable () -> Unit
) {
    var isSidePanelExpanded by remember { mutableStateOf(true) }
    
    // For swipe gesture detection
    val dragState = rememberDraggableState { delta ->
        if (delta > 50f && !isSidePanelExpanded) {
            isSidePanelExpanded = true
        } else if (delta < -50f && isSidePanelExpanded) {
            isSidePanelExpanded = false
        }
    }
    
    FinLernBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    onDragStarted = { },
                    onDragStopped = { velocity ->
                        if (abs(velocity) > 500f) {
                            isSidePanelExpanded = velocity > 0f
                        }
                    }
                )
        ) {
            // Animated Side Panel
            AnimatedAdminSidePanel(
                isExpanded = isSidePanelExpanded,
                onToggle = { isSidePanelExpanded = !isSidePanelExpanded },
                onNavigateBack = onNavigateBack,
                onNavigateToProfile = onNavigateToProfile,
                onNavigateToStudents = onNavigateToStudents,
                onNavigateToAIAssistant = onNavigateToAIAssistant,
                onNavigateToInbox = onNavigateToInbox,
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToPrivacyPolicy = onNavigateToPrivacyPolicy
            )
            
            // Main Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = animateDpAsState(
                            if (isSidePanelExpanded) 240.dp else 48.dp,
                            animationSpec = tween(250, easing = FastOutSlowInEasing),
                            label = "content padding"
                        ).value
                    )
            ) {
                if (!isSidePanelExpanded) {
                    AdminCourseHeader(courseName)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun AdminCourseHeader(courseName: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        AuroraBlue1.copy(alpha = 0.7f),
                        AuroraBlue2.copy(alpha = 0.7f),
                        AuroraBlue3.copy(alpha = 0.7f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = courseName,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun AdminNavigationSection(
    title: String,
    items: List<AdminNavItem>,
    isExpanded: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            color = Color.Cyan,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        
        items.forEach { item ->
            AdminNavigationItem(
                icon = item.icon,
                label = item.label,
                onClick = item.onClick,
                isExpanded = isExpanded
            )
        }
    }
}

@Composable
private fun AdminDividerWithAnimation() {
    HorizontalDivider(
        modifier = Modifier
            .padding(vertical = 8.dp, horizontal = 16.dp)
            .fillMaxWidth()
            .graphicsLayer {
                alpha = 0.7f
            },
        thickness = 8.dp,
        color = AuroraBlue2.copy(alpha = 0.3f)
    )
}

@Composable
private fun AdminNavButton(
    icon: ImageVector,
    text: String,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isHovered) 
                AuroraBlue1.copy(alpha = 0.1f) 
            else 
                Color.Transparent,
            contentColor = Color.White
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        interactionSource = interactionSource,
        elevation = null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isExpanded) 
                Arrangement.Start 
            else 
                Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = if (isHovered) AuroraBlue1 else AuroraBlue2,
                modifier = Modifier.size(28.dp)
            )
            if (isExpanded) {
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isHovered) Color.White else Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

private data class AdminNavItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
private fun AdminNavigationItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    isExpanded: Boolean
) {
    AdminNavButton(
        icon = icon,
        text = label,
        isExpanded = isExpanded,
        onClick = onClick
    )
}

@Composable
private fun AnimatedAdminSidePanel(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToStudents: () -> Unit,
    onNavigateToAIAssistant: () -> Unit,
    onNavigateToInbox: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit
) {
    val width by animateDpAsState(
        targetValue = if (isExpanded) 260.dp else 48.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "panel width"
    )

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(width)
    ) {
        // Hamburger menu icon (always visible)
        IconButton(
            onClick = onToggle,
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .align(Alignment.TopStart)
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.AutoMirrored.Filled.MenuOpen else Icons.Default.Menu,
                contentDescription = if (isExpanded) "Collapse menu" else "Expand menu",
                tint = AuroraBlue2
            )
        }

        // Panel content (only visible when expanded)
        if (isExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                AuroraBlue1.copy(alpha = 0.1f),
                                AuroraBlue2.copy(alpha = 0.05f)
                            )
                        )
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 16.dp)
                ) {
                    // Skip the first IconButton since we moved it outside
                    Spacer(modifier = Modifier.height(48.dp))  // Height to compensate for the IconButton

                    // Main Navigation Section
                    AdminNavigationSection(
                        title = "Management",
                        items = listOf(
                            AdminNavItem("Profile", Icons.Default.Person, onNavigateToProfile),
                            AdminNavItem("Students", Icons.Default.Group, onNavigateToStudents)
                        ),
                        isExpanded = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    AdminDividerWithAnimation()
                    Spacer(modifier = Modifier.height(16.dp))

                    // Support Section
                    AdminNavigationSection(
                        title = "Support",
                        items = listOf(
                            AdminNavItem("AI Assistant", Icons.Default.Assistant, onNavigateToAIAssistant),
                            AdminNavItem("Inbox", Icons.Default.Email, onNavigateToInbox)
                        ),
                        isExpanded = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    AdminDividerWithAnimation()
                    Spacer(modifier = Modifier.height(16.dp))

                    // Other Section
                    AdminNavigationSection(
                        title = "Other",
                        items = listOf(
                            AdminNavItem("Settings", Icons.Default.Settings, onNavigateToSettings),
                            AdminNavItem("Privacy Policy", Icons.Default.Security, onNavigateToPrivacyPolicy)
                        ),
                        isExpanded = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    AdminDividerWithAnimation()
                    
                    // Back button at the bottom
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .align(Alignment.Start)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = AuroraBlue2
                        )
                    }
                }
            }
        }
    }
} 