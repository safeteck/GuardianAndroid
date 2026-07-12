package com.sentinel.guardian.ui.screens

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Battery2Bar
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Assistant
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.sentinel.guardian.R
import com.sentinel.guardian.features.AudioRecordTask
import com.sentinel.guardian.BatteryStateReceiver
import com.sentinel.guardian.features.CallTaskDetails
import com.sentinel.guardian.Contact
import com.sentinel.guardian.features.FeaturesServices
import com.sentinel.guardian.features.LocationTaskDetails
import com.sentinel.guardian.features.LocationTypeTracking
import com.sentinel.guardian.features.SensorTaskDetails
import com.sentinel.guardian.features.SensorType
import com.sentinel.guardian.features.TaskDetails
import com.sentinel.guardian.ui.screens.features.PermissionManager
import com.sentinel.guardian.ui.screens.features.PermissionManager.FOREGROUND_SERVICE_LOCATION
import com.sentinel.guardian.ui.screens.features.PermissionManager.FOREGROUND_SERVICE_MICROPHONE
import com.sentinel.guardian.ui.screens.features.ServiceRegistry
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// Data class to hold the details for the expandable section
data class AlertDetails(
    val usedSensors: List<Int> = emptyList(),
    val usedFeatures: List<Int> = emptyList(),
    val requiredPermissions: List<Int> = emptyList(),
    @StringRes val batteryUsageResId: Int
)

// Main data class now includes details and configurable options
data class AlertItem(
    val icon: ImageVector,
    @StringRes val titleResId: Int,
    @StringRes val descriptionResId: Int,
    val details: AlertDetails,
    val type: ScenarioType,
    val permissions: List<String>,
    val configurableOptions: List<ConfigurableOption> = emptyList(),
    val taskDetails: TaskDetails? = null
)

// NEW: Data class for a configurable option
data class ConfigurableOption(
    val key: String, // A unique key for storing the value, e.g., "SEND_SMS"
    @StringRes val titleResId: Int, // The user-facing title, e.g., "Send Message"
    val state: Boolean = false // The default on/off state
)

enum class ScenarioType {
    CAR_TRIP_START,
    FALL_DETECTION,
    LOW_BATTERY,
    SIM_CARD_NETWORK_LOST,
    FLOOD_ALERT,
    EARTHQUAKE_ALERT,
    PANIC_BUTTON_ASSISTANT,
}

// ======================= REPOSITORY FOR SAVING STATE =======================
class AlertStateRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("alert_prefs", Context.MODE_PRIVATE)

    fun saveState(type: ScenarioType, isEnabled: Boolean) {
        prefs.edit().putBoolean(type.name, isEnabled).apply()
    }

    fun getState(type: ScenarioType): Boolean {
        return prefs.getBoolean(type.name, false)
    }
}
// ==============================================================================

// ======================= NEW: REPOSITORY FOR SAVING ALERT OPTIONS =======================
class AlertOptionsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("alert_options_prefs", Context.MODE_PRIVATE)

    private fun getPreferenceKey(type: ScenarioType, optionKey: String): String {
        return "${type.name}_$optionKey"
    }

    fun saveOptionState(type: ScenarioType, optionKey: String, isEnabled: Boolean) {
        prefs.edit().putBoolean(getPreferenceKey(type, optionKey), isEnabled).apply()
    }

    fun getOptionState(type: ScenarioType, optionKey: String, defaultValue: Boolean): Boolean {
        return prefs.getBoolean(getPreferenceKey(type, optionKey), defaultValue)
    }
}
// =========================================================================================

// ======================= DATA SOURCE FOR ALERT ITEMS =======================
object AlertDataSource {
    fun getAlertItems(): List<AlertItem> = listOf(
        AlertItem(
            icon = Icons.Outlined.DirectionsCar,
            titleResId = R.string.alert_car_trip_title,
            descriptionResId = R.string.alert_car_trip_desc,
            details = AlertDetails(
                usedSensors = listOf(
                    R.string.detail_sensor_accelerometer,
                    R.string.detail_sensor_gps
                ),
                requiredPermissions = listOf(
                    R.string.detail_permission_fine_location,
                    R.string.detail_permission_microphone,
                    R.string.detail_permission_send_sms
                ),
                batteryUsageResId = R.string.battery_usage_high
            ),
            type = ScenarioType.CAR_TRIP_START,
            permissions = mutableListOf(
                PermissionManager.ACCESS_FINE_LOCATION,
                PermissionManager.ACCESS_COARSE_LOCATION,
                PermissionManager.SEND_SMS,
                PermissionManager.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    add(FOREGROUND_SERVICE_LOCATION)
                    add(FOREGROUND_SERVICE_MICROPHONE)
                }
            }.toList(),
            configurableOptions = listOf(
                ConfigurableOption(
                    key = "SEND_MESSAGE",
                    titleResId = R.string.option_send_message,
                    state = true
                ),
                ConfigurableOption(
                    key = "SEND_LOCATION",
                    titleResId = R.string.option_send_location,
                    state = true
                ),
                ConfigurableOption(
                    key = "RECORD_AUDIO",
                    titleResId = R.string.option_record_audio,
                    state = false
                )
            ),
            taskDetails = TaskDetails(
                "CAR_TRIP_START_ALERT",
                recordAudio = AudioRecordTask(
                    System.currentTimeMillis() + 1000L,
                    15.seconds.inWholeMilliseconds
                ),
                locationTask = LocationTaskDetails(
                    LocationTypeTracking.LIVE,
                    5.minutes.inWholeMilliseconds.toInt()
                ),
            )
        ),
        AlertItem(
            icon = Icons.Outlined.Assistant,
            titleResId = R.string.alert_assistant_title,
            descriptionResId = R.string.alert_assistant_desc,
            details = AlertDetails(
                usedSensors = listOf(
                    R.string.detail_sensor_microphone,
                    R.string.detail_sensor_gps
                ),
                requiredPermissions = listOf(
                    R.string.detail_permission_fine_location,
                    R.string.detail_permission_microphone,
                    R.string.detail_permission_send_sms
                ),
                batteryUsageResId = R.string.battery_usage_medium
            ),
            type = ScenarioType.PANIC_BUTTON_ASSISTANT, // NOTE: Add PANIC_BUTTON_ASSISTANT to the ScenarioType enum
            permissions = mutableListOf(
                PermissionManager.ACCESS_FINE_LOCATION,
                PermissionManager.ACCESS_COARSE_LOCATION,
                PermissionManager.SEND_SMS,
//                PermissionManager.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    add(FOREGROUND_SERVICE_LOCATION)
                    add(FOREGROUND_SERVICE_MICROPHONE)
                }
            }.toList(),
            configurableOptions = listOf(
                ConfigurableOption(
                    key = "SEND_MESSAGE",
                    titleResId = R.string.option_send_message,
                    state = true
                ),
                ConfigurableOption(
                    key = "SEND_LOCATION",
                    titleResId = R.string.option_send_location,
                    state = true
                ),
                ConfigurableOption(
                    key = "RECORD_AUDIO",
                    titleResId = R.string.option_record_audio,
                    state = true // Enabled by default for emergency context
                )
            ),
            taskDetails = TaskDetails(
                "OFFLINE_ASSISTANT",
//                recordAudio = AudioRecordTask(
//                    System.currentTimeMillis() + 1000L,
//                    15.seconds.inWholeMilliseconds
//                ),
//                locationTask = LocationTaskDetails(
//                    LocationTypeTracking.LIVE,
//                    5.minutes.inWholeMilliseconds.toInt()
//                ),
            )
        ),
        AlertItem(
            icon = Icons.Outlined.Person,
            titleResId = R.string.alert_fall_detection_title,
            descriptionResId = R.string.alert_fall_detection_desc,
            details = AlertDetails(
                usedSensors = listOf(
                    R.string.detail_sensor_accelerometer,
                    R.string.detail_sensor_gyroscope
                ),
                usedFeatures = listOf(R.string.detail_feature_sms, R.string.detail_feature_call),
                requiredPermissions = listOf(
                    R.string.detail_permission_send_sms,
                    R.string.detail_permission_dial_call,
                    R.string.detail_permission_sensors
                ),
                batteryUsageResId = R.string.battery_usage_high
            ),
            type = ScenarioType.FALL_DETECTION,
            permissions = mutableListOf(
                PermissionManager.RECORD_AUDIO,
                PermissionManager.SEND_SMS,
                PermissionManager.ACCESS_COARSE_LOCATION,
                PermissionManager.ACCESS_FINE_LOCATION,
                PermissionManager.CALL_PHONE
            ).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    add(FOREGROUND_SERVICE_LOCATION)
                    add(FOREGROUND_SERVICE_MICROPHONE)
                }
            }.toList(),
            configurableOptions = listOf(
                ConfigurableOption(
                    key = "USE_SENSORS",
                    titleResId = R.string.option_use_sensors,
                    state = true
                ),
                ConfigurableOption(
                    key = "SEND_MESSAGE",
                    titleResId = R.string.option_send_message,
                    state = true
                ),
                ConfigurableOption(
                    key = "SEND_LOCATION",
                    titleResId = R.string.option_send_location,
                    state = true
                ),
                ConfigurableOption(
                    key = "RECORD_AUDIO",
                    titleResId = R.string.option_record_audio,
                    state = false
                ),
                ConfigurableOption(
                    key = "CALL_PHONE",
                    titleResId = R.string.option_dial_call,
                    state = false
                )
            ),
            taskDetails = TaskDetails(
                "FALL_DETECTED_ALERT",
                recordAudio = AudioRecordTask(
                    System.currentTimeMillis() + 1000L,
                    15.seconds.inWholeMilliseconds
                ),
                locationTask = LocationTaskDetails(
                    LocationTypeTracking.LIVE,
                    1.minutes.inWholeMilliseconds.toInt()
                ),
                sensors = listOf(
                    SensorTaskDetails(SensorType.ACCELEROMETER, true),
                    SensorTaskDetails(SensorType.GYROSCOPE, true)
                ),
                callTaskDetails = CallTaskDetails(),
            )
        ),
//        AlertItem(
//            icon = Icons.Outlined.Flood,
//            titleResId = R.string.alert_flood_title,
//            descriptionResId = R.string.alert_flood_desc,
//            details = AlertDetails(
//                usedSensors = listOf(R.string.detail_sensor_gps),
//                requiredPermissions = listOf(R.string.detail_permission_fine_location),
//                batteryUsageResId = R.string.battery_usage_low
//            ),
//            type = ScenarioType.FLOOD_ALERT,
//            permissions = mutableListOf()
//        ),
//        AlertItem(
//            icon = Icons.Outlined.Landslide,
//            titleResId = R.string.alert_earthquake_title,
//            descriptionResId = R.string.alert_earthquake_desc,
//            details = AlertDetails(
//                usedSensors = listOf(R.string.detail_sensor_gps),
//                requiredPermissions = listOf(R.string.detail_permission_fine_location),
//                batteryUsageResId = R.string.battery_usage_low
//            ),
//            type = ScenarioType.EARTHQUAKE_ALERT,
//            permissions = mutableListOf()
//        ),
        AlertItem(
            icon = Icons.Default.Battery2Bar,
            titleResId = R.string.alert_low_battery_title,
            descriptionResId = R.string.alert_low_battery_desc,
            details = AlertDetails(
                usedSensors = listOf(R.string.detail_sensor_battery),
                requiredPermissions = listOf(R.string.detail_permission_no_special),
                batteryUsageResId = R.string.battery_usage_medium
            ),
            type = ScenarioType.LOW_BATTERY,
            permissions = emptyList()
        ),
//        AlertItem(
//            icon = Icons.Outlined.SimCardAlert,
//            titleResId = R.string.alert_sim_network_loss_title,
//            descriptionResId = R.string.alert_sim_network_loss_desc,
//            details = AlertDetails(
//                requiredPermissions = listOf(R.string.detail_permission_phone_state),
//                batteryUsageResId = R.string.battery_usage_low
//            ),
//            type = ScenarioType.SIM_CARD_NETWORK_LOST,
//            permissions = emptyList()
//        )
    )
}
// ==============================================================================

class AlertViewModel(application: Application) : AndroidViewModel(application) {
    private val contactManager = ContactManager(application)
    private val optionsRepository = AlertOptionsRepository(application)

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts = _contacts.asStateFlow()
    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()
    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage = _snackbarMessage.asSharedFlow()

    private val _alertOptions =
        MutableStateFlow<Map<ScenarioType, Map<String, Boolean>>>(emptyMap())
    val alertOptions = _alertOptions.asStateFlow()

    init {
        loadInitialContacts()
        loadAlertOptions()
    }

    private fun loadInitialContacts() {
        viewModelScope.launch {
            _isLoading.value = true
            _contacts.value = contactManager.loadContacts()
            _isLoading.value = false
        }
    }

    private fun loadAlertOptions() {
        viewModelScope.launch {
            val allOptions = mutableMapOf<ScenarioType, MutableMap<String, Boolean>>()
            val alertItems = AlertDataSource.getAlertItems()

            alertItems.forEach { item ->
                val itemOptions = mutableMapOf<String, Boolean>()
                item.configurableOptions.forEach { option ->
                    itemOptions[option.key] = optionsRepository.getOptionState(
                        type = item.type,
                        optionKey = option.key,
                        defaultValue = option.state
                    )
                }
                if (itemOptions.isNotEmpty()) {
                    allOptions[item.type] = itemOptions
                }
            }
            _alertOptions.value = allOptions
        }
    }

    fun updateAlertOption(type: ScenarioType, optionKey: String, isEnabled: Boolean) {
        viewModelScope.launch {
            optionsRepository.saveOptionState(type, optionKey, isEnabled)

            val newOptions = _alertOptions.value.toMutableMap()
            val itemOptions = newOptions[type]?.toMutableMap() ?: mutableMapOf()
            itemOptions[optionKey] = isEnabled
            newOptions[type] = itemOptions
            _alertOptions.value = newOptions
        }
    }

    fun prepareTaskDetails(item: AlertItem): TaskDetails? {
        val baseDetails = item.taskDetails ?: return null
        val options = _alertOptions.value[item.type]

        if (options == null) {
            return baseDetails
        }

        var finalDetails = baseDetails.copy()

        if (options["RECORD_AUDIO"] == false) {
            finalDetails = finalDetails.copy(recordAudio = null)
        }
        if (options["SEND_LOCATION"] == false) {
            finalDetails = finalDetails.copy(locationTask = null)
        }
        if (options["USE_SENSORS"] == false) {
            finalDetails = finalDetails.copy(sensors = null)
        }
        if (options["CALL_PHONE"] == false) {
            finalDetails = finalDetails.copy(callTaskDetails = null)
        }
        return finalDetails
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsAndMonitoringScreen(
    viewModel: AlertViewModel = viewModel(),
    navController: NavHostController
) {
    val emergencyContacts by viewModel.contacts.collectAsState()
    val alertItems = remember { AlertDataSource.getAlertItems() }

    val context = LocalContext.current
    val alertStateRepository = remember { AlertStateRepository(context) }
    val switchStates = remember { mutableStateMapOf<ScenarioType, Boolean>() }

    var showOptionsDialog by remember { mutableStateOf(false) }
    var selectedItemForOptions by remember { mutableStateOf<AlertItem?>(null) }
    val alertOptions by viewModel.alertOptions.collectAsState()

    LaunchedEffect(Unit) {
        alertItems.forEach { item ->
            switchStates[item.type] = alertStateRepository.getState(item.type)
        }
    }

    var selectedSimSubscriptionId by remember { mutableStateOf<Int?>(null) }
    val batteryReceiver = BatteryStateReceiver()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.screen_title_alerts_monitoring),
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.action_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFF8F9FA),
                    titleContentColor = Color.Black,
                    navigationIconContentColor = Color.Black
                )
            )
        },
        containerColor = Color(0xFFF8F9FA)
    ) { paddingValues ->
        var permissionsState = listOf<String>()
        val toastUpdatePermissions = stringResource(R.string.toast_permissions_updated)
        val permissionsLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) {
            PermissionManager.arePermissionsGranted(
                context,
                permissionsState.filter { PermissionManager.isPermissionGranted(context, it) })
            Toast.makeText(
                context,
                toastUpdatePermissions,
                Toast.LENGTH_SHORT
            ).show()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.header_scenario_alerts),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val text1 =
                    stringResource(R.string.toast_car_trip_activated)

                val text2 =
                    stringResource(R.string.toast_car_trip_deactivated)

                alertItems.forEach { item ->
                    val isChecked = switchStates[item.type] ?: false
                    AlertRow(
                        item = item,
                        isChecked = isChecked,
                        onCheckedChange = { newCheckedState ->
                            switchStates[item.type] = newCheckedState
                            alertStateRepository.saveState(item.type, newCheckedState)

                            when (item.type) {
                                ScenarioType.CAR_TRIP_START -> {
                                    permissionsState = item.permissions
                                    val isPermissionsGranted =
                                        PermissionManager.arePermissionsGranted(
                                            context,
                                            permissionsState
                                        )
                                    if (isPermissionsGranted) {
                                        if (newCheckedState) {
                                            val finalTaskDetails =
                                                viewModel.prepareTaskDetails(item)
                                            FeaturesServices.startTask(
                                                context,
                                                "CAR_TRIP_START_ALERT",
                                                emergencyContacts,
                                                selectedSimSubscriptionId,
                                                finalTaskDetails
                                            )
                                            Toast.makeText(
                                                context,
                                                text1,
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                text2,
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            FeaturesServices.stopService(context)
                                        }
                                    } else {
                                        permissionsLauncher.launch(
                                            permissionsState.filter {
                                                !PermissionManager.isPermissionGranted(context, it)
                                            }.toTypedArray()
                                        )
                                        switchStates[item.type] = false
                                        alertStateRepository.saveState(item.type, false)
                                    }
                                }

                                ScenarioType.FALL_DETECTION -> {
                                    permissionsState = item.permissions
                                    val isPermissionsGranted =
                                        PermissionManager.arePermissionsGranted(
                                            context,
                                            permissionsState
                                        )
                                    if (isPermissionsGranted) {
                                        if (newCheckedState) {
                                            val finalTaskDetails =
                                                viewModel.prepareTaskDetails(item)
                                            FeaturesServices.startTask(
                                                context,
                                                "FALL_DETECTED_ALERT",
                                                emergencyContacts,
                                                selectedSimSubscriptionId,
                                                finalTaskDetails
                                            )
                                            // Optional: Add a toast for fall detection activation
                                        } else {
                                            FeaturesServices.stopService(context)
                                        }
                                    } else {
                                        permissionsLauncher.launch(
                                            permissionsState
                                                .filter {
                                                    !PermissionManager.isPermissionGranted(
                                                        context,
                                                        it
                                                    )
                                                }
                                                .toTypedArray())
                                        switchStates[item.type] = false
                                        alertStateRepository.saveState(item.type, false)
                                    }
                                }

                                ScenarioType.LOW_BATTERY -> {
                                    if (newCheckedState) {
                                        ServiceRegistry.registerBatteryReceiver(
                                            context,
                                            batteryReceiver
                                        )
                                    } else {
                                        ServiceRegistry.unregisterBatteryReceiver(
                                            context,
                                            batteryReceiver
                                        )
                                    }
                                }

                                ScenarioType.SIM_CARD_NETWORK_LOST,
                                ScenarioType.FLOOD_ALERT,
                                ScenarioType.EARTHQUAKE_ALERT -> {
                                    // Handle logic for other scenarios
                                }

                                ScenarioType.PANIC_BUTTON_ASSISTANT -> {
                                    val isPermissionsGranted =
                                        PermissionManager.arePermissionsGranted(
                                            context,
                                            permissionsState
                                        )
                                    if (isPermissionsGranted) {
                                        if (newCheckedState) {
                                            val finalTaskDetails =
                                                viewModel.prepareTaskDetails(item)
                                            FeaturesServices.startTask(
                                                context,
                                                "OFFLINE_ASSISTANT",
                                                emergencyContacts,
                                                selectedSimSubscriptionId,
                                                finalTaskDetails?.copy(startTask = true)
                                            )
                                            Toast.makeText(
                                                context,
                                                text1,
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                text2,
                                                Toast.LENGTH_SHORT
                                            ).show()

                                            val finalTaskDetails =
                                                viewModel.prepareTaskDetails(item)
                                            FeaturesServices.startTask(
                                                context,
                                                "OFFLINE_ASSISTANT",
                                                emergencyContacts,
                                                selectedSimSubscriptionId,
                                                finalTaskDetails?.copy(startTask = false)
                                            )
                                            Toast.makeText(
                                                context,
                                                text1,
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            FeaturesServices.stopService(context)
                                        }
                                    } else {
                                        permissionsLauncher.launch(
                                            permissionsState.filter {
                                                !PermissionManager.isPermissionGranted(context, it)
                                            }.toTypedArray()
                                        )
                                        switchStates[item.type] = false
                                        alertStateRepository.saveState(item.type, false)
                                    }
                                }
                            }
                        },
                        onLongClick = {
                            if (item.configurableOptions.isNotEmpty()) {
                                selectedItemForOptions = item
                                showOptionsDialog = true
                            }
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (showOptionsDialog) {
            LongClickOptionsDialog(
                item = selectedItemForOptions,
                options = alertOptions[selectedItemForOptions?.type] ?: emptyMap(),
                viewModel = viewModel,
                onDismiss = { showOptionsDialog = false }
            )
        }
    }
}

@Composable
fun LongClickOptionsDialog(
    item: AlertItem?,
    options: Map<String, Boolean>,
    viewModel: AlertViewModel,
    onDismiss: () -> Unit
) {
    if (item == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    R.string.dialog_options_title,
                    stringResource(item.titleResId)
                ),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item.configurableOptions.forEach { option ->
                    val isChecked = options[option.key] ?: option.state
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(option.titleResId),
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = isChecked,
                            onCheckedChange = { newCheckedState ->
                                viewModel.updateAlertOption(item.type, option.key, newCheckedState)
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White, contentColor = Color(0xFF74ABD5)
                )
            ) {
                Text(stringResource(R.string.action_done))
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlertRow(
    item: AlertItem,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onLongClick: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val iconTintColor = Color(0xFF4CAF50).copy(alpha = 0.4f)
    val iconTintColor2 = Color(0xFF66BB6A).copy(alpha = 0.8f)

    val itemTitle = stringResource(item.titleResId)
    val itemDescription = stringResource(item.descriptionResId)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(
                    onClick = { isExpanded = !isExpanded },
                    onLongClick = onLongClick
                )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = itemTitle,
                    modifier = Modifier.size(26.dp),
                    tint = if (!isChecked) Color.Black else iconTintColor2
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = itemTitle,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = itemDescription,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        lineHeight = 18.sp
                    )
                }
                Switch(
                    checked = isChecked,
                    onCheckedChange = onCheckedChange,
                    colors = SwitchDefaults.colors(
                        checkedBorderColor = Color.Transparent,
                        uncheckedBorderColor = Color.Transparent,
                        checkedThumbColor = iconTintColor2,
                        checkedTrackColor = iconTintColor,
                    )
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = Color.LightGray.copy(alpha = 0.5f)
                    )
                    AlertDetailsView(details = item.details)
                }
            }
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlertDetailsView(details: AlertDetails) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val greenColor = Color(0xFF66BB6A)
        val blueColor = Color(0xFF42A5F5)
        val redColor = Color(0xFFEF5350)

        if (details.usedSensors.isNotEmpty()) {
            Text(
                stringResource(R.string.details_header_used_sensors),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                details.usedSensors.forEach { DetailChip(text = stringResource(it)) }
            }
        }
        if (details.usedFeatures.isNotEmpty()) {
            Text(
                stringResource(R.string.details_header_used_features),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                details.usedFeatures.forEach { DetailChip(text = stringResource(it)) }
            }
        }
        if (details.requiredPermissions.isNotEmpty()) {
            Text(
                stringResource(R.string.details_header_required_permissions),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                details.requiredPermissions.forEach { DetailChip(text = stringResource(it)) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val batteryUsageText = stringResource(details.batteryUsageResId)
            val contentDesc = stringResource(R.string.content_desc_battery_state)

            when (details.batteryUsageResId) {
                R.string.battery_usage_high -> {
                    Icon(
                        Icons.Default.BatteryStd,
                        contentDescription = contentDesc,
                        tint = redColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(batteryUsageText)
                }

                R.string.battery_usage_medium -> {
                    Icon(
                        Icons.Default.Battery4Bar,
                        contentDescription = contentDesc,
                        tint = blueColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(batteryUsageText)
                }

                R.string.battery_usage_low -> {
                    Icon(
                        Icons.Default.Battery2Bar,
                        contentDescription = contentDesc,
                        tint = greenColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(batteryUsageText)
                }
            }
        }
    }
}

@Composable
fun DetailChip(text: String) {
    Surface(
        color = Color(0xFFE8F5E9), // A light green background
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color(0xFF2E7D32), // A dark green tint
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                color = Color(0xFF2E7D32),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Preview(showBackground = true, device = "id:pixel_6")
@Composable
fun AlertsAndMonitoringScreenPreview() {
    MaterialTheme {
        AlertsAndMonitoringScreen(navController = rememberNavController())
    }
}