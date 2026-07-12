package com.sentinel.guardian.ui.screens

import android.content.Context
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.sentinel.guardian.R
import com.sentinel.guardian.features.FeaturesServices
import com.sentinel.guardian.TAG
import com.sentinel.guardian.ui.screens.features.PermissionManager
import com.sentinel.guardian.ui.screens.features.SensorsFeatures

// --- Data Model for State Management (Structure remains the same) ---
data class PermissionInfo(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val permissionsRequired: List<String>,
    val isGranted: Boolean
)

data class SensorInfo(
    val name: String,
    val icon: ImageVector,
    val isActive: Boolean
)

// --- Color Palette ---
val lightGreen = Color(0xFFE8F5E9)
val darkGreen = Color(0xFF388E3C)
val lightRed = Color(0xFFFFEBEE)
val darkRed = Color(0xFFD32F2F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedSettingsScreen(navController: NavHostController) {
    val context = LocalContext.current
    var isServiceRunning by remember { mutableStateOf(FeaturesServices.isServiceRunning) }

    // --- State Management for Permissions (now using stringResource) ---
    val permissionsState = remember {
        mutableStateListOf(
            PermissionInfo(
                title = context.getString(R.string.permission_location_title),
                description = context.getString(R.string.permission_location_description),
                icon = Icons.Outlined.LocationOn,
                permissionsRequired = mutableListOf(
                    PermissionManager.ACCESS_FINE_LOCATION,
                    PermissionManager.ACCESS_COARSE_LOCATION
                ).apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        add(PermissionManager.FOREGROUND_SERVICE_LOCATION)
                    }
                },
                isGranted = false
            ),
            PermissionInfo(
                title = context.getString(R.string.permission_sms_title),
                description = context.getString(R.string.permission_sms_description),
                icon = Icons.Outlined.Sms,
                permissionsRequired = listOf(
                    PermissionManager.SEND_SMS,
                    PermissionManager.RECEIVE_SMS
                ),
                isGranted = false
            ),
            PermissionInfo(
                title = context.getString(R.string.permission_phone_calls_title),
                description = context.getString(R.string.permission_phone_calls_description),
                icon = Icons.Outlined.Call,
                permissionsRequired = listOf(PermissionManager.READ_PHONE_STATE),
                isGranted = false
            ),
            PermissionInfo(
                title = context.getString(R.string.permission_notifications_title),
                description = context.getString(R.string.permission_notifications_description),
                icon = Icons.Outlined.Notifications,
                permissionsRequired = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    listOf(PermissionManager.POST_NOTIFICATIONS)
                } else {
                    emptyList()
                },
                isGranted = false
            ),
            PermissionInfo(
                title = context.getString(R.string.permission_physical_activity_title),
                description = context.getString(R.string.permission_physical_activity_description),
                icon = Icons.Outlined.DirectionsRun,
                permissionsRequired = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    listOf(PermissionManager.ACTIVITY_RECOGNITION)
                } else {
                    emptyList()
                },
                isGranted = false
            ),
//            PermissionInfo(
//                title = context.getString(R.string.permission_camera_title),
//                description = context.getString(R.string.permission_camera_description),
//                icon = Icons.Outlined.CameraAlt,
//                permissionsRequired = listOf(PermissionManager.CAMERA),
//                isGranted = false
//            ),
            PermissionInfo(
                title = context.getString(R.string.permission_microphone_title),
                description = context.getString(R.string.permission_microphone_description),
                icon = Icons.Outlined.Mic,
                permissionsRequired = mutableListOf(PermissionManager.RECORD_AUDIO).apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        add(PermissionManager.FOREGROUND_SERVICE_MICROPHONE)
                    }
                },
                isGranted = false
            ),
            PermissionInfo(
                title = context.getString(R.string.permission_storage_title),
                description = context.getString(R.string.permission_storage_description),
                icon = Icons.Outlined.Folder,
                permissionsRequired = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    listOf(
                        PermissionManager.WRITE_EXTERNAL_STORAGE,
                        PermissionManager.READ_EXTERNAL_STORAGE
                    )
                } else {
                    emptyList()
                },
                isGranted = false
            ),
            PermissionInfo(
                title = context.getString(R.string.permission_contact_title),
                description = context.getString(R.string.permission_contact_description),
                icon = Icons.Outlined.Contacts,
                permissionsRequired = listOf(
                    PermissionManager.WRITE_CONTACTS,
                    PermissionManager.READ_CONTACTS
                ),
                isGranted = false
            ),
        )
    }

    // Helper function to check and update the state of our permissions list
    fun updatePermissionsStatus(context: Context) {
        permissionsState.forEachIndexed { index, info ->
            val allGranted = if (info.permissionsRequired.isEmpty()) {
                true
            } else {
                PermissionManager.arePermissionsGranted(context, info.permissionsRequired)
            }
            if (permissionsState[index].isGranted != allGranted) {
                permissionsState[index] = info.copy(isGranted = allGranted)
            }
        }
    }

    // This effect runs when the composable is first displayed to set the initial permission state.
    LaunchedEffect(Unit) {
        updatePermissionsStatus(context)
    }

    // The launcher now triggers the state update function on result.
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // After the user responds, update our state list to reflect the changes.
        updatePermissionsStatus(context)
        Toast.makeText(context, R.string.toast_permissions_updated, Toast.LENGTH_SHORT).show()
    }

    // Static data for sensors, this would typically come from a ViewModel or sensor manager.
    val sensors = listOf(
        SensorInfo(
            stringResource(R.string.sensor_gyroscope_name), Icons.Outlined.SyncAlt,
            SensorsFeatures.isGyroscopeAvailable(context)
        ),
        SensorInfo(
            stringResource(R.string.sensor_accelerometer_name), Icons.Outlined.TrendingUp,
            SensorsFeatures.isAccelerometerAvailable(context)
        ),
        //        SensorInfo(
        //            stringResource(R.string.sensor_gps_name), Icons.Outlined.GpsFixed,
        //            SensorsFeatures.isGpsEnabled(context)
        //        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_button_description)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .background(Color.White),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            SettingsSection(title = stringResource(R.string.section_title_sms_config)) {
                SimCardSettingRow()
            }

            SettingsSection(title = stringResource(R.string.section_title_core_functionality)) {
                BackgroundServiceCard(
                    isRunning = isServiceRunning,
                    onToggle = { isEnabled ->
                        isServiceRunning = isEnabled
                        if (isEnabled) {
                            FeaturesServices.startService(context)
                            Toast.makeText(
                                context,
                                R.string.toast_background_monitoring_started,
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            FeaturesServices.stopService(context)
                            Toast.makeText(
                                context,
                                R.string.toast_background_monitoring_stopped,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            /*
                        SettingsSection(title = stringResource(R.string.section_title_app_permissions)) {
                            PermissionsCard(
                                permissions = permissionsState.filter { it.permissionsRequired.isNotEmpty() }, // Only show rows that need permissions
                                onGrantClick = { permission ->
                                    // Request only the permissions from this group that are not yet granted.
                                    val permissionsToRequest = permission.permissionsRequired
                                        .filter { !PermissionManager.isPermissionGranted(context, it) }
                                        .toTypedArray()

                                    if (permissionsToRequest.isNotEmpty()) {
                                        permissionsLauncher.launch(permissionsToRequest)
                                    } else {
                                        runBlocking {
                                            val text = stringResource(
                                                R.string.toast_permission_already_granted,
                                                permission.title
                                            )

                                        }
                                        Toast.makeText(
                                            context,
                                            text,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                        }
            */
            SettingsSection(title = stringResource(R.string.section_title_app_permissions)) {
                PermissionsCard(
                    permissions = permissionsState.filter { it.permissionsRequired.isNotEmpty() }, // Only show rows that need permissions
                    onGrantClick = { permission ->
                        // Request only the permissions from this group that are not yet granted.
                        val permissionsToRequest = permission.permissionsRequired
                            .filter { !PermissionManager.isPermissionGranted(context, it) }
                            .toTypedArray()

                        if (permissionsToRequest.isNotEmpty()) {
                            permissionsLauncher.launch(permissionsToRequest)
                        } else {
                            val text = context.getString(
                                R.string.toast_permission_already_granted,
                                permission.title
                            )
                            Toast.makeText(
                                context,
                                text,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }

            SettingsSection(title = stringResource(R.string.section_title_device_sensors)) {
                SensorsCard(sensors = sensors)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        content()
    }
}

@Composable
fun BackgroundServiceCard(isRunning: Boolean, onToggle: (Boolean) -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.background_service_icon_description),
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.background_service_title),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.background_service_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = isRunning, onCheckedChange = onToggle)
        }
    }
}

@Composable
fun PermissionsCard(permissions: List<PermissionInfo>, onGrantClick: (PermissionInfo) -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            permissions.forEachIndexed { index, permission ->
                PermissionRow(permission = permission, onGrantClick = { onGrantClick(permission) })
                if (index < permissions.size - 1) {
                    Divider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionRow(permission: PermissionInfo, onGrantClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = permission.icon,
            contentDescription = permission.title,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(permission.title, fontWeight = FontWeight.SemiBold)
            Text(
                permission.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (permission.isGranted) {
            StatusChip(
                text = stringResource(R.string.permission_status_granted),
                textColor = darkGreen,
                backgroundColor = lightGreen
            )
        } else {
            Button(
                onClick = onGrantClick,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.permission_action_grant))
            }
        }
    }
}

@Composable
fun SensorsCard(sensors: List<SensorInfo>) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Card(
                Modifier.padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = AppColors.Blue.copy(alpha = 0.1f))
            ) {
                Text(stringResource(R.string.sensors_card_info_text), Modifier.padding(16.dp))
            }
            sensors.forEachIndexed { index, sensor ->
                val statusText =
                    if (sensor.isActive) stringResource(R.string.sensor_status_active) else stringResource(
                        R.string.sensor_status_unavailable
                    )
                val textColor = if (sensor.isActive) darkGreen else darkRed
                val bgColor = if (sensor.isActive) lightGreen else lightRed

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = sensor.icon,
                        contentDescription = sensor.name,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        sensor.name,
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold
                    )
                    StatusChip(text = statusText, textColor = textColor, backgroundColor = bgColor)
                }
                if (index < sensors.size - 1) {
                    Divider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                }
            }
        }
    }
}

@Composable
fun StatusChip(text: String, textColor: Color, backgroundColor: Color) {
    Text(
        text = text,
        color = textColor,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        modifier = Modifier
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
fun SimCardSettingRow() {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.SimCard,
                contentDescription = stringResource(R.string.sim_card_icon_description),
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                stringResource(R.string.sim_card_default_title),
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(R.string.sim_card_default_value),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

fun loadSimInfo() {
    Log.d(TAG, "SIM info loading logic would be here.")
}

@RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
@Preview(showBackground = true)
@Composable
fun EnhancedSettingsScreenPreview() {
    MaterialTheme {
        EnhancedSettingsScreen(rememberNavController())
    }
}