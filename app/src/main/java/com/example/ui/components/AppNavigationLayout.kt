package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.navigation.Screen
import com.example.ui.navigation.mainNavigationItems

@Composable
fun AppNavigationLayout(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    isExpandedScreen: Boolean = false,
    content: @Composable (PaddingValues) -> Unit
) {
    val showBottomBar = !isExpandedScreen && mainNavigationItems.any { it.route == currentRoute }
    val showNavRail = isExpandedScreen && mainNavigationItems.any { it.route == currentRoute }

    Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (showNavRail) {
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxHeight()
                    .border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    )
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                mainNavigationItems.forEach { screen ->
                    val selected = currentRoute == screen.route
                    NavigationRailItem(
                        selected = selected,
                        onClick = { onNavigate(screen.route) },
                        icon = {
                            screen.icon?.let {
                                Icon(
                                    imageVector = it,
                                    contentDescription = screen.title
                                )
                            }
                        },
                        label = {
                            Text(
                                screen.title,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        )
                    )
                }
            }
        }

        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                        tonalElevation = 6.dp
                    ) {
                        NavigationBar(
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            windowInsets = WindowInsets.navigationBars,
                            modifier = Modifier.height(68.dp)
                        ) {
                            mainNavigationItems.take(5).forEach { screen -> // Show top 5 primary routes in bottom bar
                                val selected = currentRoute == screen.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { onNavigate(screen.route) },
                                    icon = {
                                        screen.icon?.let {
                                            Icon(
                                                imageVector = it,
                                                contentDescription = screen.title,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    },
                                    label = {
                                        Text(
                                            screen.title,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 10.sp
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            content(paddingValues)
        }
    }
}

