package com.sentinel.guardian.ui.screens

import android.app.Application
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Sos
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.sentinel.guardian.R
import com.sentinel.guardian.Contact
import com.sentinel.guardian.features.FeaturesServices
import com.sentinel.guardian.features.LocationTaskDetails
import com.sentinel.guardian.features.LocationTypeTracking
import com.sentinel.guardian.features.TaskDetails
import com.sentinel.guardian.ui.screens.features.AppFeatures
import com.sentinel.guardian.ui.screens.features.PermissionManager
import com.sentinel.guardian.ui.screens.features.PermissionManager.FOREGROUND_SERVICE_LOCATION
import com.sentinel.guardian.ui.screens.navigation.Screen
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Data class to represent a Geofence
data class Geofence(
    val id: Int,
    val name: String,
    val address: String,
    val icon: ImageVector,
    var isEnabled: Boolean
)

class LocationsViewModel(application: Application) : AndroidViewModel(application) {
    private val contactManager = ContactManager(application)
    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts = _contacts.asStateFlow()
    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()
    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage = _snackbarMessage.asSharedFlow()

    init {
        loadInitialContacts()
    }

    private fun loadInitialContacts() {
        viewModelScope.launch {
            _isLoading.value = true
            _contacts.value = contactManager.loadContacts()
            _isLoading.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedLocationScreen(
    viewModel: LocationsViewModel = viewModel(),
    context: Context,
    navController: NavHostController
) {
    val emergencyContacts by viewModel.contacts.collectAsState()

    // --- State Management ---
    var isLiveSharingActive by remember { mutableStateOf(false) }
    // UPDATED: Initialize with string resource
    var selectedDuration by remember { mutableStateOf(context.getString(R.string.duration_one_hour)) }
    var locationHistoryEnabled by remember { mutableStateOf(false) }

    // UPDATED: Sample data for the geofence list now uses string resources
    val geofences = remember {
        mutableStateListOf(
            Geofence(
                1,
                context.getString(R.string.home),
                "123 Main St, Anytown",
                Icons.Outlined.Home,
                true
            ),
            Geofence(
                2,
                context.getString(R.string.work),
                "456 Business Ave, City",
                Icons.Outlined.Work,
                true
            ),
            Geofence(
                3,
                context.getString(R.string.school),
                "789 Education Rd, Suburb",
                Icons.Outlined.School,
                false
            )
        )
    }

    var selectedSimSubscriptionId by remember { mutableStateOf<Int?>(null) }

    val permissionsState = remember {
        mutableStateListOf(
            PermissionManager.SEND_SMS,
            PermissionManager.ACCESS_COARSE_LOCATION,
            PermissionManager.ACCESS_FINE_LOCATION,
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                add(FOREGROUND_SERVICE_LOCATION)
            }
        }
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        PermissionManager.arePermissionsGranted(
            context,
            permissionsState.filter { PermissionManager.isPermissionGranted(context, it) })
        // UPDATED: Using getString from context for Toast
        Toast.makeText(
            context,
            context.getString(R.string.permissions_status_updated),
            Toast.LENGTH_SHORT
        ).show()
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row {
                        Text(
                            stringResource(R.string.location_safety_title),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .weight(1f)
                                .align(Alignment.CenterVertically)
                        )

/*
                        IconButton(onClick = {
                            navController.navigate(Screen.LocationSettings.route)
                        }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = stringResource(R.string.back),
                                tint = Color(0xFF85B3D9)
                            )
                        }
*/
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        navController.popBackStack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF8F9FA))
            )
        },
        containerColor = Color(0xFFF8F9FA)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Live Share Card - NOW CONTAINS BOTH LIVE AND LAST KNOWN LOCATION
            LiveShareCard(
                isSharing = isLiveSharingActive,
                selectedDuration = selectedDuration,
                onDurationChange = { selectedDuration = it },
                onShareClick = {
                    val isPermissionsGranted =
                        PermissionManager.arePermissionsGranted(context, permissionsState)
                    if (isPermissionsGranted) {
                        isLiveSharingActive = !isLiveSharingActive
                        if (isLiveSharingActive) {
//                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//                                AppFeatures.executeTask(
//                                    context,
//                                    "SEND_LIVE_LOCATION_005",
//                                    emergencyContacts,
//                                    selectedSimSubscriptionId
//                                )
//                            } else {
                            FeaturesServices.startTask(
                                context,
                                "SEND_LIVE_LOCATION",
                                emergencyContacts,
                                selectedSimSubscriptionId,
                                TaskDetails(
                                    "SEND_LIVE_LOCATION",
                                    locationTask = LocationTaskDetails(
                                        LocationTypeTracking.LIVE,
                                        interval = 60_000,
                                        duration = 30 * 60_000
                                    ),
                                )
                            )
                            // UPDATED
                            Toast.makeText(
                                context,
                                context.getString(R.string.sending_live_location_for_5_mins),
                                Toast.LENGTH_SHORT
                            ).show()
//                            }
                        } else {
//                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//                            AppFeatures.stopLiveLocationUpdates()
//                            } else {
                            FeaturesServices.stopService(context)
//                            }
                        }
                    } else {
                        permissionsLauncher.launch(
                            permissionsState
                                .filter { !PermissionManager.isPermissionGranted(context, it) }
                                .toTypedArray()
                        )
                    }
                },
                emergencyContacts = emergencyContacts
            )

            // Geofencing Card
//            GeofenceCard(
//                geofences = geofences,
//                onToggleGeofence = { id, isEnabled ->
//                    geofences.find { it.id == id }?.isEnabled = isEnabled
//                },
//                onAddGeofence = { /* TODO: Navigate to Add Geofence screen */ }
//            )

            // Location History Card
//            LocationHistoryCard(
//                isEnabled = locationHistoryEnabled,
//                onToggle = { locationHistoryEnabled = it },
//                onViewTimeline = { /* TODO: Navigate to Timeline/History screen */ }
//            )

            // SOS Button at the bottom
            SosButton(onClick = {
                val isPermissionsGranted =
                    PermissionManager.arePermissionsGranted(context, permissionsState)
                if (isPermissionsGranted) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        AppFeatures.executeTask(
                            context,
                            "SEND_LAST_SAVED_LOCATION",
                            emergencyContacts,
                            selectedSimSubscriptionId
                        )
                    } else {
                        FeaturesServices.startTask(
                            context,
                            "SEND_LAST_SAVED_LOCATION",
                            emergencyContacts,
                            selectedSimSubscriptionId
                        )
                        // UPDATED
                        Toast.makeText(
                            context,
                            context.getString(R.string.sending_last_known_location),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    permissionsLauncher.launch(
                        permissionsState
                            .filter { !PermissionManager.isPermissionGranted(context, it) }
                            .toTypedArray()
                    )
                }
            })

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}


// --- NEW & UPDATED ---
@Composable
fun LiveShareCard(
    emergencyContacts: List<Contact>,
    isSharing: Boolean,
    selectedDuration: String,
    onDurationChange: (String) -> Unit,
    onShareClick: () -> Unit
) {
    // UPDATED: Getting options from resources
    val durationOptions = listOf(
        stringResource(R.string.duration_one_hour),
//        stringResource(R.string.duration_one_hour),
//        stringResource(R.string.duration_eight_hours),
//        stringResource(R.string.duration_until_i_stop)
    )
    val context = LocalContext.current

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.location_sharing_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            AnimatedVisibility(visible = !isSharing) {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        durationOptions.forEach { duration ->
                            val isSelected = selectedDuration == duration
                            OutlinedButton(
                                onClick = { onDurationChange(duration) },
                                shape = CircleShape,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray
                                ),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(
                                        alpha = 0.1f
                                    ) else Color.Transparent
                                )
                            ) {
                                Text(
                                    duration,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }

            Button(
                onClick = onShareClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSharing) Color(0xFFDC3545) else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = if (isSharing) Icons.Outlined.StopCircle else Icons.Outlined.MyLocation,
                    contentDescription = null, // Decorative icon
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = if (isSharing) stringResource(R.string.stop_sharing) else stringResource(
                        R.string.share_live_location
                    ),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            AnimatedVisibility(visible = !isSharing) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            var selectedSimSubscriptionId by mutableStateOf<Int?>(null)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                AppFeatures.executeTask(
                                    context,
                                    "SEND_LAST_SAVED_LOCATION",
                                    emergencyContacts,
                                    selectedSimSubscriptionId
                                )
                            } else {
                                FeaturesServices.startTask(
                                    context,
                                    "SEND_LAST_SAVED_LOCATION",
                                    emergencyContacts,
                                    selectedSimSubscriptionId
                                )
                                // UPDATED
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.sending_last_known_location),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color.LightGray)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.GpsFixed,
                            contentDescription = null, // Decorative icon
                            modifier = Modifier.padding(end = 8.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.send_last_known_location),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun GeofenceCard(
    geofences: List<Geofence>,
    onToggleGeofence: (Int, Boolean) -> Unit,
    onAddGeofence: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.geofencing_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onAddGeofence) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.add_geofence_content_description),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.add_new))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                geofences.forEach { geofence ->
                    GeofenceItem(
                        geofence = geofence,
                        onToggle = { onToggleGeofence(geofence.id, it) })
                    if (geofences.last() != geofence) {
                        Divider(color = Color.LightGray.copy(alpha = 0.3f))
                    }
                }
            }
        }
    }
}

@Composable
fun GeofenceItem(geofence: Geofence, onToggle: (Boolean) -> Unit) {
    var isEnabled by remember(geofence.isEnabled) { mutableStateOf(geofence.isEnabled) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = geofence.icon,
            contentDescription = geofence.name, // Name already comes from string resource
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(geofence.name, fontWeight = FontWeight.SemiBold)
            Text(geofence.address, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        Switch(
            checked = isEnabled,
            onCheckedChange = {
                isEnabled = it
                onToggle(it)
            }
        )
    }
}

@Composable
fun LocationHistoryCard(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onViewTimeline: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.location_history_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = isEnabled, onCheckedChange = onToggle)
            }
            Spacer(modifier = Modifier.height(8.dp))
            AnimatedVisibility(visible = isEnabled) {
                OutlinedButton(
                    onClick = onViewTimeline,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Outlined.Timeline,
                        contentDescription = stringResource(id = R.string.view_timeline_content_description),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(stringResource(R.string.view_timeline))
                }
            }
        }
    }
}

@Composable
fun SosButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFD32F2F),
            contentColor = Color.White
        )
    ) {
        Icon(
            imageVector = Icons.Outlined.Sos,
            contentDescription = stringResource(R.string.sos_content_description),
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = stringResource(R.string.emergency_sos_button),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}


@Preview(showBackground = true, device = "id:pixel_6")
@Composable
fun EnhancedLocationScreenPreview() {
    MaterialTheme {
        EnhancedLocationScreen(
            context = LocalContext.current,
            viewModel = viewModel(),
            navController = rememberNavController()
        )
    }
}