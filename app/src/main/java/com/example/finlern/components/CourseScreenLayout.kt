package com.example.finlern.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.Email
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.finlern.data.AuthorizedEmails
import com.example.finlern.data.FirebaseAuthManager
import com.example.finlern.ui.theme.AuroraBlue1
import com.example.finlern.ui.theme.AuroraBlue2
import com.example.finlern.ui.theme.AuroraBlue3
import com.example.finlern.ui.theme.FinLernBackground
import kotlin.math.abs

@Composable
fun CourseScreenLayout(
    courseName: String,
    onNavigateBack: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToInbox: () -> Unit,
    onNavigateToAIAssistant: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit,
    content: @Composable () -> Unit
) {
    var isSidePanelExpanded by remember { mutableStateOf(true) }
    
    // Get current user and check if admin/teacher
    val isAdminOrTeacher = remember {
        val currentUser = FirebaseAuthManager.getCurrentUser()
        currentUser?.email?.let { email ->
            AuthorizedEmails.isAdminOrTeacher(email)
        } ?: false
    }
    
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
                        // Change panel state based on velocity
                        if (abs(velocity) > 500f) {
                            isSidePanelExpanded = velocity > 0f
                        }
                    }
                )
        ) {
            // Animated Side Panel
            AnimatedSidePanel(
                isExpanded = isSidePanelExpanded,
                onToggle = { isSidePanelExpanded = !isSidePanelExpanded },
                onNavigateBack = onNavigateBack,
                onNavigateToProfile = onNavigateToProfile,
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToInbox = onNavigateToInbox,
                onNavigateToAIAssistant = onNavigateToAIAssistant,
                onNavigateToPrivacyPolicy = onNavigateToPrivacyPolicy,
                showBackButton = isAdminOrTeacher
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
                // Only show header when side panel is collapsed
                if (!isSidePanelExpanded) {
                    CourseHeader(courseName)
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
private fun AnimatedSidePanel(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToInbox: () -> Unit,
    onNavigateToAIAssistant: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit,
    showBackButton: Boolean
) {
    val width by animateDpAsState(
        targetValue = if (isExpanded) 260.dp else 48.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "panel width"
    )

    val alpha by animateFloatAsState(
        targetValue = if (isExpanded) 0.85f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "background alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(width)
    ) {
        // Enhanced panel background with gradient and blur
        if (isExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF1A1A2F).copy(alpha = alpha),
                                Color(0xFF1A1A2F).copy(alpha = alpha * 0.95f)
                            ),
                            startX = 0f,
                            endX = width.value * 0.8f
                        ),
                        shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
                    )
                    .blur(radius = 1.dp)
            )
        }

        // Panel content with improved spacing and animations
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // Hamburger menu with smooth rotation
            val rotation by animateFloatAsState(
                targetValue = if (isExpanded) 180f else 0f,
                animationSpec = tween(350, easing = FastOutSlowInEasing),
                label = "menu rotation"
            )

            IconButton(
                onClick = onToggle,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = if (isExpanded) 
                        Icons.AutoMirrored.Filled.MenuOpen
                    else 
                        Icons.Default.Menu,
                    contentDescription = "Toggle panel",
                    tint = Color.White,
                    modifier = Modifier
                        .size(28.dp)
                        .graphicsLayer { rotationY = rotation }
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(16.dp))

                // Main navigation sections with enhanced dividers
                NavigationSection(
                    title = "Main",
                    items = buildList {
                        add(NavItem("Profile", Icons.Default.Person))
                        add(NavItem("Study Materials", Icons.AutoMirrored.Filled.MenuBook))
                    },
                    isExpanded = true,
                    onNavigate = { /* Handle navigation */ },
                    onNavigateToProfile = onNavigateToProfile
                )

                DividerWithAnimation()

                NavigationSection(
                    title = "Support",
                    items = listOf(
                        NavItem("AI Assistant", Icons.Default.Assistant),
                        NavItem("Inbox", Icons.Default.Email)
                    ),
                    isExpanded = true,
                    onNavigate = { label -> 
                        when (label) {
                            "AI Assistant" -> onNavigateToAIAssistant()
                            "Inbox" -> onNavigateToInbox()
                            // Handle other navigation items if needed
                        }
                    },
                    onNavigateToProfile = onNavigateToProfile
                )

                DividerWithAnimation()

                NavigationSection(
                    title = "Other",
                    items = listOf(
                        NavItem("Settings", Icons.Default.Settings),
                        NavItem("Privacy Policy", Icons.Default.Security)
                    ),
                    isExpanded = true,
                    onNavigate = { label -> 
                        when (label) {
                            "Settings" -> onNavigateToSettings()
                            "Privacy Policy" -> onNavigateToPrivacyPolicy()
                        }
                    },
                    onNavigateToProfile = onNavigateToProfile
                )

                if (showBackButton) {
                    DividerWithAnimation()
                    
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
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

@Composable
private fun DividerWithAnimation() {
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
private fun NavigationSection(
    title: String,
    items: List<NavItem>,
    isExpanded: Boolean,
    onNavigate: (String) -> Unit,
    onNavigateToProfile: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        if (isExpanded) {
            Text(
                text = title,
                color = Color.Cyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        items.forEach { item ->
            NavButton(
                icon = item.icon,
                text = item.text,
                isExpanded = isExpanded,
                onClick = { 
                    when (item.text) {
                        "Profile" -> onNavigateToProfile()
                        else -> onNavigate(item.text)
                    }
                }
            )
        }
    }
}

@Composable
private fun NavButton(
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

private data class NavItem(
    val text: String,
    val icon: ImageVector
)

@Composable
private fun CourseHeader(courseName: String) {
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