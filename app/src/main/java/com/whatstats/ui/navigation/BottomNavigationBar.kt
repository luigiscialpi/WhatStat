package com.whatstats.ui.navigation

import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import com.whatstats.R

private data class BottomNavItem(
    val graphRoute: String,
    val destinationRoute: String,
    val label: String,
    val icon: ImageVector,
)

@Composable
@Suppress("FunctionName")
fun BottomNavigationBar(
    navController: NavHostController,
    currentDestination: NavDestination?,
    isLiteMode: Boolean = false,
    isLandscape: Boolean = false,
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

    val filteredItems = if (isLiteMode) items.filter { it.graphRoute != NavGraphRoute.CALLS } else items

    fun navigateTo(item: BottomNavItem) {
        val topLevelRoute = if (isLiteMode) NavGraphRoute.LITE else NavGraphRoute.ROOT
        navController.navigate(item.destinationRoute) {
            popUpTo(topLevelRoute) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    if (isLandscape) {
        NavigationRail(
            containerColor = MaterialTheme.colorScheme.surface,
            modifier =
                Modifier.drawBehind {
                    val shadowWidth = 8.dp.toPx()
                    drawRect(
                        brush =
                            Brush.horizontalGradient(
                                colors =
                                    listOf(
                                        Color.Black.copy(alpha = 0.08f),
                                        Color.Transparent,
                                    ),
                                startX = size.width,
                                endX = size.width + shadowWidth,
                            ),
                        topLeft = Offset(size.width, 0f),
                        size = Size(shadowWidth, size.height),
                    )
                },
        ) {
            filteredItems.forEachIndexed { index, item ->
                val selected =
                    currentDestination
                        ?.hierarchy
                        ?.any { destination -> destination.route == item.graphRoute } == true

                NavigationRailItem(
                    selected = selected,
                    onClick = { navigateTo(item) },
                    icon = { Icon(item.icon, contentDescription = item.label) },
                    label = {
                        Text(
                            text = item.label,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    alwaysShowLabel = true,
                    colors =
                        NavigationRailItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                )

                if (index < filteredItems.lastIndex) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    } else {
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
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
                        topLeft = Offset(0f, -shadowHeight),
                        size = Size(size.width, shadowHeight),
                    )
                },
        ) {
            filteredItems.forEach { item ->
                val selected =
                    currentDestination
                        ?.hierarchy
                        ?.any { destination -> destination.route == item.graphRoute } == true

                NavigationBarItem(
                    selected = selected,
                    onClick = { navigateTo(item) },
                    icon = {
                        Icon(item.icon, contentDescription = item.label)
                    },
                    label = {
                        Text(
                            text = item.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false,
                        )
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
}
