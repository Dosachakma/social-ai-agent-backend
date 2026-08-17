package com.example.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.repository.*
import com.example.ui.components.AppNavigationLayout
import com.example.ui.components.AppTopBar
import com.example.ui.screens.accounts.AccountsScreen
import com.example.ui.screens.accounts.AccountsViewModel
import com.example.ui.screens.agent.AiAgentScreen
import com.example.ui.screens.agent.AiAgentViewModel
import com.example.ui.screens.analytics.AnalyticsScreen
import com.example.ui.screens.analytics.AnalyticsViewModel
import com.example.ui.screens.auth.AuthViewModel
import com.example.ui.screens.auth.LoginScreen
import com.example.ui.screens.brand.BrandProfileScreen
import com.example.ui.screens.brand.BrandProfileViewModel
import com.example.ui.screens.calendar.CalendarScreen
import com.example.ui.screens.calendar.CalendarViewModel
import com.example.ui.screens.content.ContentScreen
import com.example.ui.screens.content.ContentViewModel
import com.example.ui.screens.dashboard.DashboardScreen
import com.example.ui.screens.dashboard.DashboardViewModel
import com.example.ui.screens.onboarding.OnboardingScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.settings.SettingsViewModel
import com.example.ui.screens.splash.SplashScreen

class ViewModelFactory(
    private val socialRepository: SocialMediaRepository,
    private val aiAgentService: AiAgentService,
    private val brandProfileRepository: BrandProfileRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(DashboardViewModel::class.java) -> DashboardViewModel(socialRepository, aiAgentService) as T
            modelClass.isAssignableFrom(AiAgentViewModel::class.java) -> {
                val aiService = aiAgentService as? AIService ?: MockAiAgentService(brandProfileRepository)
                AiAgentViewModel(aiService) as T
            }
            modelClass.isAssignableFrom(ContentViewModel::class.java) -> ContentViewModel(socialRepository) as T
            modelClass.isAssignableFrom(CalendarViewModel::class.java) -> CalendarViewModel() as T
            modelClass.isAssignableFrom(AccountsViewModel::class.java) -> AccountsViewModel(socialRepository) as T
            modelClass.isAssignableFrom(AnalyticsViewModel::class.java) -> AnalyticsViewModel(socialRepository) as T
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel() as T
            modelClass.isAssignableFrom(BrandProfileViewModel::class.java) -> BrandProfileViewModel(brandProfileRepository) as T
            modelClass.isAssignableFrom(AuthViewModel::class.java) -> AuthViewModel() as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    isExpandedScreen: Boolean = false
) {
    val repository = remember { MockSocialMediaRepository() }
    val brandProfileRepository = remember { MockBrandProfileRepository() }
    val aiAgentService = remember { MockAiAgentService(brandProfileRepository) }
    val factory = remember { ViewModelFactory(repository, aiAgentService, brandProfileRepository) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val isFullscreenRoute = currentRoute == Screen.Splash.route ||
            currentRoute == Screen.Onboarding.route ||
            currentRoute == Screen.Login.route

    if (isFullscreenRoute) {
        NavHost(
            navController = navController,
            startDestination = Screen.Splash.route,
            modifier = modifier
        ) {
            composable(Screen.Splash.route) {
                SplashScreen(
                    onSplashComplete = {
                        navController.navigate(Screen.Onboarding.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    onFinishOnboarding = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Login.route) {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                )
            }
        }
    } else {
        AppNavigationLayout(
            currentRoute = currentRoute,
            onNavigate = { route ->
                navController.navigate(route) {
                    popUpTo(Screen.Dashboard.route) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            isExpandedScreen = isExpandedScreen
        ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = Screen.Dashboard.route,
                modifier = modifier
            ) {
                composable(Screen.Dashboard.route) {
                    val viewModel: DashboardViewModel = viewModel(factory = factory)
                    DashboardScreen(
                        viewModel = viewModel,
                        onNavigateToAgent = { navController.navigate(Screen.Agent.route) },
                        onNavigateToCalendar = { navController.navigate(Screen.Calendar.route) },
                        onNavigateToAccounts = { navController.navigate(Screen.Accounts.route) },
                        onNavigateToContent = { navController.navigate(Screen.Content.route) },
                        onNavigateToAnalytics = { navController.navigate(Screen.Analytics.route) }
                    )
                }
                composable(Screen.Agent.route) {
                    val viewModel: AiAgentViewModel = viewModel(factory = factory)
                    AiAgentScreen(
                        viewModel = viewModel,
                        onNavigateToBrandProfile = { navController.navigate(Screen.BrandProfile.route) }
                    )
                }
                composable(Screen.Content.route) {
                    val viewModel: ContentViewModel = viewModel(factory = factory)
                    ContentScreen(viewModel = viewModel)
                }
                composable(Screen.Calendar.route) {
                    val viewModel: CalendarViewModel = viewModel(factory = factory)
                    CalendarScreen(viewModel = viewModel)
                }
                composable(Screen.Accounts.route) {
                    val viewModel: AccountsViewModel = viewModel(factory = factory)
                    AccountsScreen(viewModel = viewModel)
                }
                composable(Screen.Analytics.route) {
                    val viewModel: AnalyticsViewModel = viewModel(factory = factory)
                    AnalyticsScreen(viewModel = viewModel)
                }
                composable(Screen.Settings.route) {
                    val viewModel: SettingsViewModel = viewModel(factory = factory)
                    SettingsScreen(
                        viewModel = viewModel,
                        onLogout = {
                            navController.navigate(Screen.Login.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onNavigateToBrandProfile = {
                            navController.navigate(Screen.BrandProfile.route)
                        }
                    )
                }
                composable(Screen.BrandProfile.route) {
                    val viewModel: BrandProfileViewModel = viewModel(factory = factory)
                    BrandProfileScreen(
                        viewModel = viewModel,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
