package com.whatstat.reborn.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

private data class BottomNavItem(
    val graphRoute: String,
    val destinationRoute: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
fun BottomNavigationBar(
    navController: NavHostController,
    currentDestination: NavDestination?
) {
    val items = listOf(
        BottomNavItem(
            graphRoute = NavGraphRoute.Overview,
            destinationRoute = Screen.Dashboard.route,
            label = "Home",
            icon = Icons.Outlined.Dashboard
        ),
        BottomNavItem(
            graphRoute = NavGraphRoute.Chats,
            destinationRoute = Screen.ChatList.route,
            label = "Chat",
            icon = Icons.Outlined.Chat
        ),
        BottomNavItem(
            graphRoute = NavGraphRoute.Media,
            destinationRoute = Screen.MediaStats.route,
            label = "Media",
            icon = Icons.Outlined.Link
        ),
        BottomNavItem(
            graphRoute = NavGraphRoute.Radar,
            destinationRoute = Screen.WordSearch.route,
            label = "Radar",
            icon = Icons.Outlined.Search
        ),
        BottomNavItem(
            graphRoute = NavGraphRoute.Calls,
            destinationRoute = Screen.CallsStats.route,
            label = "Chiamate",
            icon = Icons.Outlined.Call
        )
    )

    NavigationBar {
        items.forEach { item ->
            val selected = currentDestination
                ?.hierarchy
                ?.any { destination -> destination.route == item.graphRoute } == true

            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(item.destinationRoute) {
                        popUpTo(NavGraphRoute.Main) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(item.icon, contentDescription = item.label)
                },
                label = {
                    Text(item.label)
                }
            )
        }
    }
}
