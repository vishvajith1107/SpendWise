package com.spendwise.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.spendwise.ui.about.AboutScreen
import com.spendwise.ui.analytics.AnalyticsScreen
import com.spendwise.ui.categories.CategoriesScreen
import com.spendwise.ui.dashboard.DashboardScreen
import com.spendwise.ui.history.HistoryScreen
import com.spendwise.ui.recurring.RecurringScreen
import com.spendwise.ui.settings.SettingsScreen
import com.spendwise.ui.support.SupportScreen

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object Analytics : Screen("analytics")
    object History : Screen("history")
    object Settings : Screen("settings")
    object Recurring : Screen("recurring")
    object Categories : Screen("categories")
    object Support : Screen("support")
    object About : Screen("about")
}

private data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector
)

private val BOTTOM_NAV_ITEMS = listOf(
    BottomNavItem(Screen.Dashboard, "Home", Icons.Default.Home),
    BottomNavItem(Screen.Analytics, "Analytics", Icons.Default.Insights),
    BottomNavItem(Screen.History, "History", Icons.Default.History),
    BottomNavItem(Screen.Settings, "Settings", Icons.Default.Settings)
)

@Composable
fun SpendWiseNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in BOTTOM_NAV_ITEMS.map { it.screen.route }

    fun navigateTopTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    BOTTOM_NAV_ITEMS.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.screen.route,
                            onClick = { navigateTopTab(item.screen.route) },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    onNavigateToAnalytics = { navigateTopTab(Screen.Analytics.route) },
                    onNavigateToRecurring = { navController.navigate(Screen.Recurring.route) },
                    onNavigateToSupport = { navController.navigate(Screen.Support.route) }
                )
            }
            composable(Screen.Analytics.route) {
                AnalyticsScreen()
            }
            composable(Screen.History.route) {
                HistoryScreen(onBack = null)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onBack = null,
                    onNavigateToRecurring = { navController.navigate(Screen.Recurring.route) },
                    onNavigateToCategories = { navController.navigate(Screen.Categories.route) },
                    onNavigateToSupport = { navController.navigate(Screen.Support.route) },
                    onNavigateToAbout = { navController.navigate(Screen.About.route) }
                )
            }
            composable(Screen.About.route) {
                AboutScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Categories.route) {
                CategoriesScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Recurring.route) {
                RecurringScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Support.route) {
                SupportScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
