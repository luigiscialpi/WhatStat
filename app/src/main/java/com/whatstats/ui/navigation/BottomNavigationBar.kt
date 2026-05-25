package com.whatstats.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import com.whatstats.R

private data class BottomNavItem(
    val graphRoute: String,
    val destinationRoute: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

@Composable
@Suppress("FunctionName")
fun BottomNavigationBar(
    navController: NavHostController,
    currentDestination: NavDestination?,
    isLiteMode: Boolean = false,
) {
    val items =
        listOf(
            BottomNavItem(
                graphRoute = NavGraphRoute.OVERVIEW,
                destinationRoute = Screen.Dashboard.route,
                label = stringResource(R.string.nav_dashboard),
                icon = Icons.Outlined.Dashboard,
            ),
            BottomNavItem(
                graphRoute = NavGraphRoute.CHATS,
                destinationRoute = Screen.ChatList.route,
                label = stringResource(R.string.nav_chats),
                icon = Icons.AutoMirrored.Outlined.Chat,
            ),
            BottomNavItem(
                graphRoute = NavGraphRoute.MEDIA,
                destinationRoute = Screen.MediaStats.route,
                label = stringResource(R.string.nav_media),
                icon = Icons.Outlined.Link,
            ),
            BottomNavItem(
                graphRoute = NavGraphRoute.RADAR,
                destinationRoute = Screen.WordSearch.route,
                label = stringResource(R.string.nav_radar),
                icon = Icons.Outlined.Search,
            ),
            BottomNavItem(
                graphRoute = NavGraphRoute.CALLS,
                destinationRoute = Screen.CallsStats.route,
                label = stringResource(R.string.nav_calls),
                icon = Icons.Outlined.Call,
            ),
        )

    NavigationBar(
        modifier =
            Modifier.drawBehind {
                val shadowHeight = 8.dp.toPx()
                drawRect(
                    brush =
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.08f),
                                ),
                            startY = -shadowHeight,
                            endY = 0f,
                        ),
                    topLeft = androidx.compose.ui.geometry.Offset(0f, -shadowHeight),
                    size = androidx.compose.ui.geometry.Size(size.width, shadowHeight),
                )
            },
    ) {
        val filteredItems = if (isLiteMode) items.filter { it.graphRoute != NavGraphRoute.CALLS } else items

        filteredItems.forEach { item ->
            val selected =
                currentDestination
                    ?.hierarchy
                    ?.any { destination -> destination.route == item.graphRoute } == true

            NavigationBarItem(
                selected = selected,
                onClick = {
                    val topLevelRoute = if (isLiteMode) NavGraphRoute.LITE else NavGraphRoute.ROOT
                    navController.navigate(item.destinationRoute) {
                        popUpTo(topLevelRoute) {
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
                },
                colors =
                    NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            )
        }
    }
}
