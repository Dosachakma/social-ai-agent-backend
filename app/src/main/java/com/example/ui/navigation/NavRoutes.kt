package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector? = null) {
    object Splash : Screen("splash", "Splash")
    object Onboarding : Screen("onboarding", "Onboarding")
    object Login : Screen("login", "Login")

    // Core Dashboard items
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Dashboard)
    object Agent : Screen("agent", "AI Agent", Icons.Default.AutoAwesome)
    object Content : Screen("content", "Content", Icons.Default.Article)
    object Calendar : Screen("calendar", "Calendar", Icons.Default.CalendarMonth)
    object Accounts : Screen("accounts", "Accounts", Icons.Default.ManageAccounts)
    object Analytics : Screen("analytics", "Analytics", Icons.Default.Analytics)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object BrandProfile : Screen("brand_profile", "Brand Memory", Icons.Default.Psychology)
}

val mainNavigationItems = listOf(
    Screen.Dashboard,
    Screen.Agent,
    Screen.Content,
    Screen.Calendar,
    Screen.Accounts,
    Screen.Analytics,
    Screen.Settings
)
