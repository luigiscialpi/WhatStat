package com.whatstat.reborn.ui.screen.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import com.whatstat.reborn.ui.navigation.BottomNavigationBar
import com.whatstat.reborn.ui.navigation.NavGraphRoute

@Composable
fun AppScaffold(
    navController: NavHostController,
    currentDestination: NavDestination?,
    isLiteMode: Boolean = false,
    content: @Composable (PaddingValues) -> Unit
) {
    val route = currentDestination?.route.orEmpty()
    val isLiteImportRoute = route.contains("lite_setup")
    val showBottomBar = currentDestination
        ?.hierarchy
        ?.any { destination -> destination.route == NavGraphRoute.Main } == true && !isLiteImportRoute

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) {
                BottomNavigationBar(
                    navController = navController,
                    currentDestination = currentDestination,
                    isLiteMode = isLiteMode
                )
            }
        }
    ) { innerPadding ->
        content(innerPadding)
    }
}
