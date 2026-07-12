package com.sentinel.guardian.ui.screens.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.sentinel.guardian.R

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    val screens = listOf(
        Screen.Home,
//        Screen.Messages,
        Screen.Contacts,
        Screen.Settings,
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar {
        screens.forEach { screen ->
            NavigationBarItem(
                label = {
                    val title = when(screen.title){
                        Screen.Home.title -> stringResource(R.string.home)
                        Screen.Settings.title -> stringResource(R.string.settings_title)
                        Screen.Contacts.title -> stringResource(R.string.contacts_screen_title)
                        else -> {"else"}
                    }
                    Text(text = title)
                },
                icon = { Icon(imageVector = screen.icon, contentDescription = "Navigation Icon") },
                selected = currentDestination?.route == screen.route,
                onClick = {
                    navController.navigate(screen.route) {
                        // Pop up to the start destination of the graph to
                        // avoid building up a large stack of destinations
                        // on the back stack as users select items
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        // Avoid multiple copies of the same destination when
                        // reselecting the same item
                        launchSingleTop = true
                        // Restore state when reselecting a previously selected item
                        restoreState = true
                    }
                }
            )
        }
    }
}