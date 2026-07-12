package com.sentinel.guardian.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.sentinel.guardian.R
import com.sentinel.guardian.BatteryStateReceiver
import com.sentinel.guardian.features.SmsReceiver
import com.sentinel.guardian.ui.screens.features.ServiceRegistry
import com.sentinel.guardian.ui.screens.navigation.Screen

// Data class to hold information for each action item for cleaner code.
data class ActionItemData(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val route: String
)

/**
 * The main screen composable for the Emergency Hub.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyHubScreen(navController: NavHostController) {
    val context = LocalContext.current
    ServiceRegistry.disableManifestComponent(context, SmsReceiver::class.java)
    ServiceRegistry.disableManifestComponent(context, BatteryStateReceiver::class.java)
    Scaffold(
        containerColor = Color.White,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row {
                            Text(
                                text = stringResource(id = R.string.emergency_hub_title),
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                modifier = Modifier.weight(1f).align(Alignment.CenterVertically)
                            )

/*
                            IconButton(
                                onClick = {
                                    navController.navigate(Screen.Notifications.route)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = R.string.notifications_content_description_icon.toString()
                                )
                            }
*/

                        }
                    },

//                    navigationIcon = {
//                        IconButton(onClick = { /* TODO: Handle drawer menu click */ }) {
//                            Icon(
//                                imageVector = Icons.Default.Menu,
//                                contentDescription = stringResource(id = R.string.menu_content_description),
//                                tint = Color.Black
//                            )
//                        }
//                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.White,
                    ),
                )
                // A subtle divider line below the TopAppBar
                Divider(color = Color.LightGray.copy(alpha = 0.5f), thickness = 1.dp)
            }
        }
    ) { paddingValues ->
        MainContent(
            modifier = Modifier.padding(paddingValues),
            navController
        )
    }
}

/**
 * The main scrollable content of the screen.
 */
@Composable
fun MainContent(modifier: Modifier = Modifier, navController: NavHostController) {
    val quickActions = listOf(
        ActionItemData(
            Icons.Filled.Chat,
            stringResource(id = R.string.action_title_send_sms),
            stringResource(id = R.string.action_subtitle_send_sms),
            Screen.Sms.route
        ),
        ActionItemData(
            Icons.Filled.LocationOn,
            stringResource(id = R.string.action_title_share_location),
            stringResource(id = R.string.action_subtitle_share_location),
            Screen.Location.route
        ),
        /*
                ActionItemData(
                    Icons.Filled.History,
                    stringResource(id = R.string.action_title_periodic_location),
                    stringResource(id = R.string.action_subtitle_periodic_location),
                    Screen.Location.route
                ),
        */
//        ActionItemData(
//            Icons.Filled.Mic,
//            stringResource(id = R.string.action_title_capture_audio),
//            stringResource(id = R.string.action_subtitle_capture_audio),
//            Screen.Media.route
//        ),
//        ActionItemData(
//            Icons.Filled.Videocam,
//            stringResource(id = R.string.action_title_capture_video),
//            stringResource(id = R.string.action_subtitle_capture_video),
//            Screen.Media.route
//        ),
//        ActionItemData(
//            Icons.Filled.PhotoCamera,
//            stringResource(id = R.string.action_title_capture_photo),
//            stringResource(id = R.string.action_subtitle_capture_photo),
//            Screen.Media.route
//        )
    )

    val scenarioAlerts = listOf(
        ActionItemData(
            Icons.Filled.Notifications,
            stringResource(id = R.string.action_title_trigger_alerts),
            stringResource(id = R.string.action_subtitle_trigger_alerts),
            Screen.Alert.route
        )
    )

    // Using LazyColumn for efficient scrolling of list items.
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White),
        contentPadding = PaddingValues(all = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeader(title = stringResource(id = R.string.section_header_quick_actions))
        }
        items(quickActions) { item ->
            ActionItem(data = item) {
                navController.navigate(item.route)
            }
        }
        item {
            // Add extra space before the next section
            Spacer(modifier = Modifier.height(16.dp))
            SectionHeader(title = stringResource(id = R.string.section_header_scenario_alerts))
        }
        items(scenarioAlerts) { item ->
            ActionItem(data = item) {
                navController.navigate(item.route)
            }
        }

        // Section 1: Scenario Alerts
//        item {
//            SectionHeader(title = "Scenario Alerts")
//        }

        // Spacer between sections
//        item {
//            Spacer(modifier = Modifier.height(16.dp))
//        }

        // Section 2: Emergency Events
//        item {
//            SectionHeader(title = stringResource(id = R.string.section_header_emergency_events))
//        }
//        item {
//            EmergencyEventButton(onClick = { /* TODO: Handle Emergency Event click */ })
//        }

    }
}

@Composable
fun EmergencyEventButton(onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFDC3545), // Red button color
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFC82333)), // Darker red for icon background
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✱", // Asterisk icon is a symbol, not a translatable string
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Normal
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = stringResource(id = R.string.emergency_event_button_text),
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null, // Decorative icon
                tint = Color.White
            )
        }
    }
}

/**
 * A reusable composable for displaying a section header.
 */
@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = Color.Black,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

/**
 * A reusable composable for the action items in the list.
 */
@Composable
fun ActionItem(data: ActionItemData, onClick: () -> Unit) {
    val iconBackgroundColor = Color(0xFFEAF2FF)
    val iconTintColor = Color(0xFF3589FF)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        // Using a border instead of elevation for a flatter design
        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f)),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon with a circular background
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(iconBackgroundColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = data.icon,
                    contentDescription = data.title, // Now uses the localized title for accessibility
                    tint = iconTintColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Title and Subtitle
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = data.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(2.dp))
                // The subtitle is passed in data class but not displayed in the original code.
                // It is now ready to be used with localized text if you uncomment it.
//                Text(
//                    text = data.subtitle,
//                    fontSize = 14.sp,
//                    color = Color.Gray
//                )
            }

            // Arrow Icon
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null, // Decorative icon
                tint = Color.LightGray
            )
        }
    }
}

/**
 * Preview for the EmergencyHubScreen in Android Studio.
 */
@Preview(showBackground = true, device = "id:pixel_4")
@Composable
fun DefaultPreview() {
    MaterialTheme {
        EmergencyHubScreen(rememberNavController())
    }
}