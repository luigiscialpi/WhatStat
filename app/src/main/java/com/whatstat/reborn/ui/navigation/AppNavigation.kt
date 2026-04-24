package com.whatstat.reborn.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.whatstat.reborn.data.repository.WhatsAppRepository
import com.whatstat.reborn.ui.screen.calls.CallsStatsScreen
import com.whatstat.reborn.ui.screen.chatdetail.ChatDetailScreen
import com.whatstat.reborn.ui.screen.chatlist.ChatListScreen
import com.whatstat.reborn.ui.screen.dashboard.DashboardScreen
import com.whatstat.reborn.ui.screen.home.AppScaffold
import com.whatstat.reborn.ui.screen.media.MediaStatsScreen
import com.whatstat.reborn.ui.screen.setup.SetupScreen
import com.whatstat.reborn.ui.screen.wordsearch.WordSearchScreen
import com.whatstat.reborn.ui.screen.history.GroupHistoryScreen

sealed class Screen(val route: String) {
    data object Setup : Screen("setup")
    data object Dashboard : Screen("dashboard")
    data object ChatList : Screen("chat_list")

    data object ChatDetail : Screen("chat_detail/{chatId}") {
        fun createRoute(chatId: Long) = "chat_detail/$chatId"
    }

    data object WordSearch : Screen("word_search") {
        fun createRoute(chatId: Long? = null) = if (chatId != null) "word_search/$chatId" else route
    }

    data object CallsStats : Screen("calls_stats") {
        fun createRoute(chatId: Long? = null) = if (chatId != null) "calls_stats/$chatId" else route
    }

    data object MediaStats : Screen("media_stats") {
        fun createRoute(chatId: Long? = null) = if (chatId != null) "media_stats/$chatId" else route
    }

    data object GroupHistory : Screen("group_history") {
        fun createRoute(chatId: Long) = "group_history/$chatId"
    }
}

object NavGraphRoute {
    const val Setup = "setup_graph"
    const val Main = "main_graph"
    const val Overview = "overview_graph"
    const val Chats = "chats_graph"
    const val Media = "media_graph"
    const val Radar = "radar_graph"
    const val Calls = "calls_graph"
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    repository: WhatsAppRepository
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    AppScaffold(
        navController = navController,
        currentDestination = currentDestination
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NavGraphRoute.Setup,
            route = "root_graph",
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            setupGraph(navController)
            mainGraph(navController, repository)
        }
    }
}

private fun NavGraphBuilder.setupGraph(navController: NavHostController) {
    navigation(
        startDestination = Screen.Setup.route,
        route = NavGraphRoute.Setup
    ) {
        composable(Screen.Setup.route) {
            SetupScreen(
                onSetupComplete = {
                    navController.navigate(NavGraphRoute.Main) {
                        popUpTo(NavGraphRoute.Setup) { inclusive = true }
                    }
                }
            )
        }
    }
}

private fun NavGraphBuilder.mainGraph(
    navController: NavHostController,
    repository: WhatsAppRepository
) {
    navigation(
        startDestination = NavGraphRoute.Overview,
        route = NavGraphRoute.Main
    ) {
        navigation(
            startDestination = Screen.Dashboard.route,
            route = NavGraphRoute.Overview
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    onNavigateBack = {
                        repository.resetState()
                        navController.navigate(Screen.Setup.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }

        navigation(
            startDestination = Screen.ChatList.route,
            route = NavGraphRoute.Chats
        ) {
            composable(Screen.ChatList.route) {
                ChatListScreen(
                    onChatSelected = { chatId ->
                        navController.navigate(Screen.ChatDetail.createRoute(chatId))
                    },
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(
                route = Screen.ChatDetail.route,
                arguments = listOf(navArgument("chatId") { type = NavType.LongType })
            ) { backStackEntry ->
                val chatId = backStackEntry.arguments?.getLong("chatId") ?: return@composable
                ChatDetailScreen(
                    chatId = chatId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToWordSearch = {
                        navController.navigate(Screen.WordSearch.createRoute(chatId))
                    },
                    onNavigateToCalls = {
                        navController.navigate(Screen.CallsStats.createRoute(chatId))
                    },
                    onNavigateToMedia = {
                        navController.navigate(Screen.MediaStats.createRoute(chatId))
                    },
                    onNavigateToHistory = {
                        navController.navigate(Screen.GroupHistory.createRoute(chatId))
                    }
                )
            }

            composable(
                route = "${Screen.WordSearch.route}/{chatId}",
                arguments = listOf(navArgument("chatId") { type = NavType.LongType })
            ) { backStackEntry ->
                val chatId = backStackEntry.arguments?.getLong("chatId")
                WordSearchScreen(
                    chatId = chatId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "${Screen.CallsStats.route}/{chatId}",
                arguments = listOf(navArgument("chatId") { type = NavType.LongType })
            ) { backStackEntry ->
                val chatId = backStackEntry.arguments?.getLong("chatId")
                CallsStatsScreen(
                    chatId = chatId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "${Screen.MediaStats.route}/{chatId}",
                arguments = listOf(navArgument("chatId") { type = NavType.LongType })
            ) { backStackEntry ->
                val chatId = backStackEntry.arguments?.getLong("chatId")
                MediaStatsScreen(
                    chatId = chatId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "${Screen.GroupHistory.route}/{chatId}",
                arguments = listOf(navArgument("chatId") { type = NavType.LongType })
            ) { backStackEntry ->
                val chatId = backStackEntry.arguments?.getLong("chatId")
                GroupHistoryScreen(
                    chatId = chatId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        navigation(
            startDestination = Screen.MediaStats.route,
            route = NavGraphRoute.Media
        ) {
            composable(Screen.MediaStats.route) {
                MediaStatsScreen(
                    chatId = null,
                    onNavigateBack = {}
                )
            }
        }

        navigation(
            startDestination = Screen.WordSearch.route,
            route = NavGraphRoute.Radar
        ) {
            composable(Screen.WordSearch.route) {
                WordSearchScreen(
                    chatId = null,
                    onNavigateBack = {}
                )
            }
        }

        navigation(
            startDestination = Screen.CallsStats.route,
            route = NavGraphRoute.Calls
        ) {
            composable(Screen.CallsStats.route) {
                CallsStatsScreen(
                    chatId = null,
                    onNavigateBack = {}
                )
            }
        }
    }
}
