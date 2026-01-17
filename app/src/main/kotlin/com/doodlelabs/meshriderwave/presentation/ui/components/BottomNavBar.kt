/*
 * Mesh Rider Wave - Premium Bottom Navigation Bar
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Glassmorphism bottom navigation with animations
 * - Floating glass design
 * - Animated selection indicator
 * - Haptic feedback ready
 *
 * SOLID: Single Responsibility - Navigation only
 * DRY: Single component for all nav needs
 */

package com.doodlelabs.meshriderwave.presentation.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doodlelabs.meshriderwave.presentation.ui.theme.PremiumColors

/**
 * Navigation items enum
 */
enum class NavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME(
        route = "dashboard",
        label = "Home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    ),
    GROUPS(
        route = "groups",
        label = "Groups",
        selectedIcon = Icons.Filled.Groups,
        unselectedIcon = Icons.Outlined.Groups
    ),
    CHANNELS(
        route = "channels",
        label = "PTT",
        selectedIcon = Icons.Filled.RecordVoiceOver,
        unselectedIcon = Icons.Outlined.RecordVoiceOver
    ),
    MAP(
        route = "map",
        label = "Map",
        selectedIcon = Icons.Filled.Map,
        unselectedIcon = Icons.Outlined.Map
    ),
    SETTINGS(
        route = "settings",
        label = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
}

/**
 * Premium Floating Bottom Navigation Bar
 *
 * Glassmorphism floating navigation with smooth animations.
 *
 * @param selectedItem Currently selected nav item
 * @param onItemSelected Callback when item is tapped
 * @param modifier Modifier for customization
 * @param hasNotifications Map of route to notification count
 */
@Composable
fun PremiumBottomNavBar(
    selectedItem: NavItem,
    onItemSelected: (NavItem) -> Unit,
    modifier: Modifier = Modifier,
    hasNotifications: Map<String, Int> = emptyMap()
) {
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        // Glass background
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(PremiumColors.GlassWhite)
                .border(
                    width = 1.dp,
                    color = PremiumColors.GlassBorder,
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem.entries.forEach { item ->
                val isSelected = item == selectedItem
                val notificationCount = hasNotifications[item.route] ?: 0

                NavBarItem(
                    item = item,
                    isSelected = isSelected,
                    notificationCount = notificationCount,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onItemSelected(item)
                    }
                )
            }
        }
    }
}

/**
 * Individual Navigation Bar Item
 */
@Composable
private fun NavBarItem(
    item: NavItem,
    isSelected: Boolean,
    notificationCount: Int,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    val iconColor by animateColorAsState(
        targetValue = if (isSelected) PremiumColors.ElectricCyan else PremiumColors.TextSecondary,
        animationSpec = tween(200),
        label = "iconColor"
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .then(
                if (isSelected) {
                    Modifier.background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                PremiumColors.ElectricCyan.copy(alpha = 0.2f),
                                Color.Transparent
                            )
                        )
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Icon with notification badge
            Box {
                Icon(
                    imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                    contentDescription = item.label,
                    modifier = Modifier.size(24.dp),
                    tint = iconColor
                )

                // Notification badge
                if (notificationCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(PremiumColors.NeonMagenta),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (notificationCount > 9) "9+" else notificationCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // Label (visible when selected)
            AnimatedVisibility(
                visible = isSelected,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = PremiumColors.ElectricCyan,
                    fontSize = 10.sp
                )
            }
        }
    }
}

/**
 * Premium Tab Bar (Alternative design)
 *
 * Pill-style tab bar for secondary navigation.
 */
@Composable
fun PremiumTabBar(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PremiumColors.SpaceGrayLight)
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        tabs.forEachIndexed { index, tab ->
            val isSelected = index == selectedIndex

            val backgroundColor by animateColorAsState(
                targetValue = if (isSelected) PremiumColors.ElectricCyan else Color.Transparent,
                animationSpec = tween(200),
                label = "tabBackground"
            )

            val textColor by animateColorAsState(
                targetValue = if (isSelected) Color.White else PremiumColors.TextSecondary,
                animationSpec = tween(200),
                label = "tabTextColor"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(backgroundColor)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onTabSelected(index)
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tab,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = textColor
                )
            }
        }
    }
}

/**
 * Floating Action Button Bar
 *
 * Central FAB with secondary actions.
 */
@Composable
fun FloatingActionBar(
    onCenterClick: () -> Unit,
    modifier: Modifier = Modifier,
    leftActions: List<Pair<ImageVector, () -> Unit>> = emptyList(),
    rightActions: List<Pair<ImageVector, () -> Unit>> = emptyList(),
    centerIcon: ImageVector = Icons.Default.Call
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left actions
        leftActions.forEach { (icon, onClick) ->
            SmallActionButton(
                icon = icon,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            )
        }

        // Center FAB
        GlowingIconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onCenterClick()
            },
            icon = centerIcon,
            size = 64.dp,
            iconSize = 28.dp,
            backgroundColor = PremiumColors.ElectricCyan,
            glowColor = PremiumColors.ElectricCyanGlow
        )

        // Right actions
        rightActions.forEach { (icon, onClick) ->
            SmallActionButton(
                icon = icon,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            )
        }
    }
}

/**
 * Small action button for FloatingActionBar
 */
@Composable
private fun SmallActionButton(
    icon: ImageVector,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(PremiumColors.SpaceGrayLight)
            .border(
                width = 1.dp,
                color = PremiumColors.GlassBorder,
                shape = CircleShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = PremiumColors.TextSecondary
        )
    }
}

/**
 * Minimal Bottom Bar
 *
 * Simplified bottom bar for call screens.
 */
@Composable
fun MinimalBottomBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        PremiumColors.DeepSpace.copy(alpha = 0.9f)
                    )
                )
            )
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}
