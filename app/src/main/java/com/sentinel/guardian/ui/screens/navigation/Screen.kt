package com.sentinel.guardian.ui.screens.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RoomService
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Home : Screen(
        route = "home",
        title = "Home",
        icon = Icons.Default.Home
    )

    object Profile : Screen(
        route = "profile",
        title = "Profile",
        icon = Icons.Default.Person
    )

    object Settings : Screen(
        route = "settings",
        title = "Settings",
        icon = Icons.Default.Settings
    )

    // New Screens
    object Alert : Screen(
        route = "alert",
        title = "Alert",
        icon = Icons.Default.Warning
    )

    object Sms : Screen(
        route = "sms",
        title = "SMS",
        icon = Icons.Default.Sms
    )

    object Location : Screen(
        route = "location",
        title = "Location",
        icon = Icons.Default.LocationOn
    )

    object Media : Screen(
        route = "media",
        title = "Media",
        icon = Icons.Default.PermMedia // Represents media files like photos/videos
    )

    object Services : Screen(
        route = "services",
        title = "Services",
        icon = Icons.Default.RoomService // Can represent various services
    )

    object Messages : Screen(
        route = "messages",
        title = "Messages",
        icon = Icons.Default.Mail // Represents general messages/inbox
    )

    object Contacts : Screen(
        route = "contacts",
        title = "Contacts",
        icon = Icons.Default.Contacts
    )

    object Notifications : Screen(
        route = "notifications",
        title = "Notifications",
        icon = Icons.Default.Notifications
    )

    object LocationSettings : Screen(
        route = "location_settings",
        title = "Location Settings",
        icon = Icons.Default.Settings
    )


}