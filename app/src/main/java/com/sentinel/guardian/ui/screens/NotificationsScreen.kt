package com.sentinel.guardian.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import coil3.compose.rememberAsyncImagePainter


// --- 3. Data Model & Sample Data ---
// Using a sealed class to represent the different types of notification content.
sealed class NotificationContent {
    data class Evacuation(val details: String, val buttonText: String) : NotificationContent()
    data class VoiceMessage(val progress: Float, val duration: String) : NotificationContent()
    data class RoadClosure(val imageUrl: String, val details: String) : NotificationContent()
    data class WeatherAlert(val details: String, val linkText: String) : NotificationContent()
}

// A data class to hold all information for a single notification item.
data class Notification(
    val id: Int,
    val icon: ImageVector,
    val iconBackgroundColor: Color,
    val iconTintColor: Color,
    val title: String,
    val time: String,
    val summary: String,
    val content: NotificationContent
)

// --- Main Composable Function ---
// This is the single entry point for the screen.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(navController: NavHostController) {

    // --- 1. Theme & Colors ---
    // These colors are extracted directly from the CSS variables in the HTML.
    val primaryColor = Color(0xFFE92933)
    val primaryBg = Color(0xFFFDF2F2)
    val textPrimary = Color(0xFF1A1A1A)
    val textSecondary = Color(0xFF666666)
    val borderColor = Color(0xFFE5E7EB)

    // Note: To use the "Lexend" font, you would add it to your `res/font` directory
    // and create a FontFamily. For this example, we'll use the default font.
    // val lexendFontFamily = FontFamily(Font(R.font.lexend_regular))

    // --- 2. State Management ---
    // This state holds the ID of the currently expanded notification item.
    // It mimics the behavior of `x-data="{ expanded: null }"` from Alpine.js.
    var expandedItemId by remember { mutableStateOf<Int?>(null) }


    // A list of notifications, matching the HTML content.
    val notifications = remember {
        listOf(
            Notification(
                id = 1,
                icon = Icons.Default.Notifications,
                iconBackgroundColor = primaryBg,
                iconTintColor = primaryColor,
                title = "Emergency Alert",
                time = "10:30 AM",
                summary = "High priority: Evacuate area immediately.",
                content = NotificationContent.Evacuation(
                    details = "A critical emergency has been declared in your vicinity. Please evacuate to the nearest designated shelter. Follow the instructions of emergency personnel.",
                    buttonText = "View Evacuation Route"
                )
            ),
            Notification(
                id = 2,
                icon = CustomIcons.MedicalAssistance,
                iconBackgroundColor = Color(0xFFEFF6FF), // blue-100
                iconTintColor = Color(0xFF3B82F6), // blue-500
                title = "Medical Assistance Needed",
                time = "11:45 AM",
                summary = "Voice message received.",
                content = NotificationContent.VoiceMessage(progress = 0.25f, duration = "0:12")
            ),
            Notification(
                id = 3,
                icon = CustomIcons.RoadClosure,
                iconBackgroundColor = Color(0xFFFEFCE8), // yellow-100
                iconTintColor = Color(0xFFEAB308), // yellow-500
                title = "Road Closure Update",
                time = "12:15 PM",
                summary = "Main street closed due to an incident.",
                content = NotificationContent.RoadClosure(
                    imageUrl = "https://images.unsplash.com/photo-1567110168430-74ac6c1e944a?q=80&w=1974&auto=format&fit=crop",
                    details = "Main Street between 1st and 3rd Ave is closed to all traffic. Please use alternative routes."
                )
            ),
            Notification(
                id = 4,
                icon = CustomIcons.Weather,
                iconBackgroundColor = Color(0xFFF5F3FF), // purple-100
                iconTintColor = Color(0xFF8B5CF6), // purple-500
                title = "Severe Weather Warning",
                time = "1:00 PM",
                summary = "Thunderstorm warning in effect.",
                content = NotificationContent.WeatherAlert(
                    details = "A severe thunderstorm warning is in effect until 3:00 PM. Expect heavy rain, strong winds, and possible hail. Seek shelter indoors and stay away from windows.",
                    linkText = "More Info"
                )
            )
        )
    }

    // --- 4. UI Structure (Scaffold) ---
    // Scaffold provides the basic Material Design layout structure (TopBar, BottomBar, Content).
    Scaffold(
        containerColor = Color(0xFFF9FAFB), // bg-gray-50
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Notifications",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                        color = textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = textPrimary
                        )
                    }
                },
                actions = {
                    // A spacer to balance the title, mimicking the empty div in the HTML.
                    Spacer(modifier = Modifier.width(48.dp)) // width of IconButton
                },
                colors = TopAppBarDefaults.topAppBarColors(Color.White),
//                elevation = 4.dp
            )
        },
    ) { innerPadding ->
        // --- 5. Main Content (LazyColumn for the scrollable list) ---
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(Color.White)
        ) {
            items(notifications, key = { it.id }) { notification ->
                val isExpanded = expandedItemId == notification.id

                NotificationItem(
                    notification = notification,
                    isExpanded = isExpanded,
                    onItemClick = {
                        expandedItemId = if (isExpanded) null else notification.id
                    },
                    colors = Triple(primaryColor, textPrimary, textSecondary)
                )

                // Add a divider between items, but not after the last one.
                if (notification.id != notifications.last().id) {
                    Divider(color = borderColor, thickness = 1.dp)
                }
            }
        }
    }
}

// --- 6. Reusable Notification Item Composable ---
@Composable
private fun NotificationItem(
    notification: Notification,
    isExpanded: Boolean,
    onItemClick: () -> Unit,
    colors: Triple<Color, Color, Color>
) {
    val (primaryColor, textPrimary, textSecondary) = colors

    val chevronRotation: Float by animateFloatAsState(targetValue = if (isExpanded) 90f else 0f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onItemClick)
    ) {
        // --- Collapsed View ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(notification.iconBackgroundColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = notification.icon,
                    contentDescription = notification.title,
                    tint = notification.iconTintColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))

            // Text content
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = notification.title,
                        fontWeight = FontWeight.SemiBold,
                        color = textPrimary,
                        fontSize = 16.sp,
                    )
                    Text(
                        text = notification.time,
                        color = textSecondary,
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = notification.summary,
                    color = textSecondary,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.width(16.dp))

            // Chevron Icon
            Icon(
                imageVector = CustomIcons.ChevronRight,
                contentDescription = "Expand",
                tint = Color.Gray,
                modifier = Modifier
                    .size(20.dp)
                    .align(Alignment.CenterVertically)
                    .rotate(chevronRotation)
            )
        }

        // --- Expanded View (Animated) ---
        AnimatedVisibility(visible = isExpanded) {
            Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                when (val content = notification.content) {
                    is NotificationContent.Evacuation -> {
                        Column {
                            Text(text = content.details, color = textSecondary, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { /* Handle route view */ },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = content.buttonText,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                    }

                    is NotificationContent.VoiceMessage -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF3F4F6)) // gray-100
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            IconButton(
                                onClick = { /* Handle play */ },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    tint = notification.iconTintColor
                                )
                            }
                            LinearProgressIndicator(
                            progress = { content.progress },
                            modifier = Modifier.weight(1f),
                            color = notification.iconTintColor,
                            trackColor = Color.LightGray,
                            strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                            )
                            Text(text = content.duration, color = textSecondary, fontSize = 12.sp)
                        }
                    }

                    is NotificationContent.RoadClosure -> {
                        Column {
                            Image(
                                painter = rememberAsyncImagePainter(content.imageUrl),
                                contentDescription = "Road Closure",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(text = content.details, color = textSecondary, fontSize = 14.sp)
                        }
                    }

                    is NotificationContent.WeatherAlert -> {
                        Column {
                            Text(text = content.details, color = textSecondary, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = content.linkText,
                                color = primaryColor,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                modifier = Modifier.clickable { /* Handle more info click */ }
                            )
                        }
                    }
                }
            }
        }
    }
}


// --- 7. Custom Icons ---
// These are ImageVector representations of the SVG paths from the HTML.
object CustomIcons {
    val ChevronRight: ImageVector
        get() = ImageVector.Builder(
            name = "ChevronRight",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = null,
//            stroke = Color.Black,
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(9f, 18f)
            lineTo(15f, 12f)
            lineTo(9f, 6f)
        }.build()

    val MedicalAssistance: ImageVector
        get() = ImageVector.Builder(
            name = "MedicalAssistance",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).run {
            path(
//                stroke = Color.Black,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(21f, 15f)
                curveToRelative(0f, -4.4f, -3.6f, -8f, -8f, -8f)
                reflectiveCurveToRelative(-8f, 3.6f, -8f, 8f)
            }
            path(
//                stroke = Color.Black,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(9f, 15f)
                lineToRelative(-1f, -1f)
            }
            path(
//                stroke = Color.Black,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(15f, 15f)
                lineToRelative(1f, -1f)
            }
            path(
//                stroke = Color.Black,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 6f)
                lineToRelative(2f, 3f)
            }
            path(
//                stroke = Color.Black,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(10f, 9f)
                lineToRelative(-2f, -3f)
            }
            path(
//                stroke = Color.Black,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 19.5f)
                verticalLineToRelative(-3f)
            }
            path(
//                stroke = Color.Black,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(9f, 16.5f)
                verticalLineToRelative(3f)
            }
            path(
//                stroke = Color.Black,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(15f, 16.5f)
                verticalLineToRelative(3f)
            }
            build()
        }

    val RoadClosure: ImageVector
        get() = ImageVector.Builder(
            name = "RoadClosure",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).run {
            path(
//                stroke = Color.Black,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3f, 11f)
                lineToRelative(18f, -5f)
                verticalLineToRelative(12f)
                lineTo(3f, 14f)
                verticalLineToRelative(-3f)
                close()
            }
            path(
//                stroke = Color.Black,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(11.6f, 16.8f)
                arcToRelative(3f, 3f, 0f, true, true, -5.8f, -1.6f)
            }
            build()
        }

    val Weather: ImageVector
        get() = ImageVector.Builder(
            name = "Weather",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
//            stroke = Color.Black,
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(17.5f, 19.0f)
            horizontalLineTo(9.0f)
            arcToRelative(7.0f, 7.0f, 0.0f, true, true, 6.71f, -9.0f)
            horizontalLineToRelative(1.79f)
            arcToRelative(4.5f, 4.5f, 0.0f, true, true, 0.0f, 9.0f)
            close()
        }.build()

    val Home: ImageVector
        get() = ImageVector.Builder(
            name = "Home",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).run {
            path(
//                stroke = Color.Black,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3f, 9f)
                lineTo(12f, 2f)
                lineTo(21f, 9f)
                verticalLineToRelative(11f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
                horizontalLineTo(5f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
                close()
            }
            path(
//                stroke = Color.Black,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(9f, 22f)
                lineTo(9f, 12f)
                lineTo(15f, 12f)
                lineTo(15f, 22f)
            }
            build()
        }

    val Report: ImageVector
        get() = ImageVector.Builder(
            name = "Report",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).run {
            path(
//                stroke = Color.Black,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(14.5f, 2.0f)
                horizontalLineTo(6.0f)
                arcToRelative(2.0f, 2.0f, 0.0f, false, false, -2.0f, 2.0f)
                verticalLineToRelative(16.0f)
                arcToRelative(2.0f, 2.0f, 0.0f, false, false, 2.0f, 2.0f)
                horizontalLineToRelative(12.0f)
                arcToRelative(2.0f, 2.0f, 0.0f, false, false, 2.0f, -2.0f)
                verticalLineTo(7.5f)
                lineTo(14.5f, 2.0f)
                close()
            }
            path(
//                stroke = Color.Black,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(14f, 2f)
                lineTo(14f, 8f)
                lineTo(20f, 8f)
            }
            build()
        }

    val Profile: ImageVector
        get() = ImageVector.Builder(
            name = "Profile",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).run {
            path(
//                stroke = Color.Black,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(19f, 21f)
                verticalLineToRelative(-2f)
                arcToRelative(4f, 4f, 0f, false, false, -4f, -4f)
                horizontalLineTo(9f)
                arcToRelative(4f, 4f, 0f, false, false, -4f, 4f)
                verticalLineToRelative(2f)
            }
            path(
//                stroke = Color.Black,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 7f)
                moveTo(16f, 7f)
                arcTo(4f, 4f, 0f, true, true, 8f, 7f)
                arcTo(4f, 4f, 0f, true, true, 16f, 7f)
                close()
            }
            build()
        }
}

// --- 8. Preview ---
// This allows you to see the screen in the Android Studio design preview.
@Preview(showBackground = true, showSystemUi = true)
@Composable
fun EmergencyNotificationsScreenPreview() {
    MaterialTheme {
        NotificationsScreen(rememberNavController())
    }
}