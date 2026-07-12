package com.sentinel.guardian.ui.screens.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.sentinel.guardian.ui.screens.AlertSettingsScreen
import com.sentinel.guardian.ui.screens.AlertsAndMonitoringScreen
//import com.sentinel.guardian.ui.screens.CaptureMediaScreen
import com.sentinel.guardian.ui.screens.ContactsScreen
import com.sentinel.guardian.ui.screens.EmergencyHubScreen
import com.sentinel.guardian.ui.screens.NotificationsScreen
import com.sentinel.guardian.ui.screens.EmergencySmsScreen
import com.sentinel.guardian.ui.screens.EnhancedLocationScreen
import com.sentinel.guardian.ui.screens.EnhancedSettingsScreen
import com.sentinel.guardian.ui.screens.MessagesScreen

@Composable
fun AppNavHost(
    lifecycleOwner: LifecycleOwner,
    context: Context,
    navController: NavHostController,
    startDestination: String = Screen.Home.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Home.route) {
            EmergencyHubScreen(navController)
        }

        composable(Screen.Settings.route) {
            EnhancedSettingsScreen(navController)
        }

        composable(Screen.Sms.route) {
            EmergencySmsScreen(navController=navController)
        }

        composable(Screen.Location.route) {
            EnhancedLocationScreen(
                context = context,
                navController = navController
            )
        }

        composable(Screen.Media.route) {
//            CaptureMediaScreen(lifecycleOwner)
        }

        composable(Screen.Services.route) {
            TODO("Not Implemented")
        }

        composable(Screen.Messages.route) {
            MessagesScreen()
        }

        composable(Screen.Contacts.route) {
            ContactsScreen()
        }

        composable(Screen.Alert.route) {
            AlertsAndMonitoringScreen(navController=navController)
        }

        composable(Screen.Notifications.route) {
            NotificationsScreen(navController)
        }

        composable(Screen.LocationSettings.route){
            AlertSettingsScreen(navController)
        }
    }
}