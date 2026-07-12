package com.sentinel.guardian.ui.screens

import android.app.Application
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.sentinel.guardian.ContactCategory
import com.sentinel.guardian.features.FeaturesServices
import com.sentinel.guardian.ui.screens.features.AppFeatures
import com.sentinel.guardian.ui.screens.features.PermissionManager
import com.sentinel.guardian.ui.screens.features.PermissionManager.FOREGROUND_SERVICE_LOCATION
import com.sentinel.guardian.ui.screens.navigation.Screen
import com.sentinel.guardian.ui.theme.GuardianTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// --- THEME & COLORS (Creative Blue Theme) ---
object AppColors {
    val PrimaryBlue = Color(0xFF0D6EFD)
    val PrimaryBlueDark = Color(0xFF0A58CA)
    val Background = Color(0xFFF4F7FC)
    val Surface = Color.White
    val TextPrimary = Color(0xFF212529)
    val TextSecondary = Color(0xFF6C757D)
    val Border = Color(0xFFDEE2E6)
    val Blue = Color(0xE8426CE7)
    val Red = Color(0xFFDC3545)
}

// --- DATA MODELS ---
data class EmergencyContact(
    val id: String, val name: String, val phone: String, val initials: String,
    val avatarColor: Color, val avatarIcon: ImageVector? = null
)

// --- VIEWMODEL STATE & EVENTS ---

/**
 * Represents the entire state of the EmergencySmsScreen.
 */
data class EmergencySmsUiState(
    val message: String = "", // Default message is now set in ViewModel's init
    val isLocationSharingOn: Boolean = false,
    val selectedContactIds: Set<String> = emptySet(),
    val manualNumberInput: String = "",
    val manualNumbers: Set<String> = emptySet(),
    val isPredefinedContactsExpanded: Boolean = false,
    val allContacts: List<EmergencyContact> = emptyList(),
    // Derived state for convenience in the UI
    val areAllPredefinedSelected: Boolean = false,
    val totalRecipients: Int = 0
)

/**
 * Defines all possible user actions (events) on the screen.
 */
sealed interface EmergencySmsEvent {
    data class MessageChanged(val message: String) : EmergencySmsEvent
    data object LocationSharingToggled : EmergencySmsEvent
    data class PredefinedContactToggled(val contactId: String) : EmergencySmsEvent
    data object SelectAllPredefinedClicked : EmergencySmsEvent
    data object ClearAllPredefinedClicked : EmergencySmsEvent
    data class ManualNumberInputChanged(val number: String) : EmergencySmsEvent
    data object ManualNumberAdded : EmergencySmsEvent
    data class ManualNumberRemoved(val number: String) : EmergencySmsEvent
    data object PredefinedListToggled : EmergencySmsEvent
    data class SendAlert(val context: Context) : EmergencySmsEvent
    data class InitialContactsLoaded(val contacts: List<Contact>) : EmergencySmsEvent
}

// --- VIEWMODEL ---

class EmergencySmsViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(EmergencySmsUiState())
    val uiState: StateFlow<EmergencySmsUiState> = _uiState.asStateFlow()

    private val contactManager = ContactManager(getApplication())

    private val _emergencyContacts = MutableStateFlow<List<Contact>>(emptyList())
    val emergencyContact = _emergencyContacts

    init {
        loadEmergencyContacts()
        // Initialize default message from string resources
        _uiState.update {
            it.copy(message = application.getString(R.string.emergency_sms_default_message))
        }
    }

    fun onEvent(event: EmergencySmsEvent) {
        when (event) {
            is EmergencySmsEvent.InitialContactsLoaded -> loadEmergencyContacts()
            is EmergencySmsEvent.MessageChanged -> _uiState.update { it.copy(message = event.message) }
            is EmergencySmsEvent.LocationSharingToggled -> _uiState.update {
                it.copy(
                    isLocationSharingOn = !it.isLocationSharingOn
                )
            }

            is EmergencySmsEvent.PredefinedContactToggled -> togglePredefinedContact(event.contactId)
            is EmergencySmsEvent.SelectAllPredefinedClicked -> selectAllPredefined()
            is EmergencySmsEvent.ClearAllPredefinedClicked -> clearAllPredefined()
            is EmergencySmsEvent.ManualNumberInputChanged -> _uiState.update {
                it.copy(
                    manualNumberInput = event.number
                )
            }

            is EmergencySmsEvent.ManualNumberAdded -> addManualNumber()
            is EmergencySmsEvent.ManualNumberRemoved -> removeManualNumber(event.number)
            is EmergencySmsEvent.PredefinedListToggled -> _uiState.update {
                it.copy(
                    isPredefinedContactsExpanded = !it.isPredefinedContactsExpanded
                )
            }

            is EmergencySmsEvent.SendAlert -> sendAlert(event.context)
        }
    }

    private fun loadEmergencyContacts() {
        viewModelScope.launch {
            val contacts = contactManager.loadContacts()
                .filter { it.category == ContactCategory.EMERGENCY }
                .mapIndexed { index, contact ->
                    EmergencyContact(
                        id = contact.id,
                        name = contact.name,
                        phone = contact.phoneNumber,
                        initials = contact.name.take(2).uppercase(),
                        avatarColor = Color.Green
                    )
                }
            _uiState.update { it.copy(allContacts = contacts) }
            updateDerivedState()
        }
    }

    private fun togglePredefinedContact(contactId: String) {
        val currentSelected = _uiState.value.selectedContactIds
        val newSelected = if (contactId in currentSelected) {
            currentSelected - contactId
        } else {
            currentSelected + contactId
        }
        _uiState.update { it.copy(selectedContactIds = newSelected) }
        updateDerivedState()
    }

    private fun selectAllPredefined() {
        val allIds = _uiState.value.allContacts.map { it.id }.toSet()
        _uiState.update { it.copy(selectedContactIds = allIds) }
        updateDerivedState()
    }

    private fun clearAllPredefined() {
        _uiState.update { it.copy(selectedContactIds = emptySet()) }
        updateDerivedState()
    }

    private fun addManualNumber() {
        if (_uiState.value.manualNumberInput.isNotBlank()) {
            val newManualNumbers =
                _uiState.value.manualNumbers + _uiState.value.manualNumberInput.trim()
            _uiState.update {
                it.copy(
                    manualNumbers = newManualNumbers,
                    manualNumberInput = "" // Clear input field
                )
            }
            updateDerivedState()
        }
    }

    private fun removeManualNumber(number: String) {
        val newManualNumbers = _uiState.value.manualNumbers - number
        _uiState.update { it.copy(manualNumbers = newManualNumbers) }
        updateDerivedState()
    }

    private fun updateDerivedState() {
        _uiState.update { currentState ->
            val areAllSelected = currentState.allContacts.isNotEmpty() &&
                    currentState.selectedContactIds.size == currentState.allContacts.size
            val totalRecipients =
                currentState.selectedContactIds.size + currentState.manualNumbers.size
            currentState.copy(
                areAllPredefinedSelected = areAllSelected,
                totalRecipients = totalRecipients
            )
        }
    }

    private fun sendAlert(context: Context) {
        viewModelScope.launch {
            val state = _uiState.value

            // Get phone numbers from selected predefined contacts
            val selectedPredefinedNumbers = state.allContacts
                .filter { it.id in state.selectedContactIds }
                .map { it.phone }

            // Combine with manual numbers, using a Set to handle duplicates automatically
            val allRecipientNumbers = (selectedPredefinedNumbers + state.manualNumbers).toSet()

            if (allRecipientNumbers.isEmpty()) {
                Toast.makeText(
                    context,
                    context.getString(R.string.emergency_sms_error_no_recipients), // Using string resource
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            var finalMessage = state.message
            if (state.isLocationSharingOn) {
                finalMessage += context.getString(R.string.emergency_sms_location_appended_message) // Using string resource
            }

            // IMPORTANT: Ensure <uses-permission android:name="android.permission.SEND_SMS" /> in AndroidManifest.xml
            try {
                var sentCount = 0
                allRecipientNumbers.forEach { number ->
                    if (number.isNotBlank()) {
                        AppFeatures.sendSms(context, number, finalMessage)
                        sentCount++
                    }
                }
                if (_uiState.value.isLocationSharingOn) {
                    FeaturesServices.startTask(
                        context,
                        "SEND_LAST_SAVED_LOCATION",
                        allRecipientNumbers.map {
                            Contact(
                                phoneNumber = it,
                                id = "null",
                                name = "null"
                            )
                        })
                }
                val toastMessage = context.getString(
                    R.string.emergency_sms_alert_sent_toast,
                    sentCount
                ) // Using formatted string resource
                Toast.makeText(context, toastMessage, Toast.LENGTH_LONG).show()

            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    context.getString(R.string.emergency_sms_error_send_failed), // Using string resource
                    Toast.LENGTH_LONG
                ).show()
                e.printStackTrace()
            }
        }
    }
}


// --- MAIN SCREEN COMPOSABLE ---
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmergencySmsScreen(
    viewModel: EmergencySmsViewModel = viewModel(),
    navController: NavHostController
) {
    // Collect state from the ViewModel
    val uiState by viewModel.uiState.collectAsState()
    val emergencyContact by viewModel.emergencyContact.collectAsState()
    val context = LocalContext.current

    // Load initial contacts into the ViewModel one time
    LaunchedEffect(key1 = emergencyContact) {
        viewModel.onEvent(EmergencySmsEvent.InitialContactsLoaded(emergencyContact))
    }

    val permissionsState = remember {
        mutableStateListOf(
            PermissionManager.SEND_SMS,
        )
    }
    val permissionsLocationState = remember {
        mutableStateListOf(
            PermissionManager.ACCESS_FINE_LOCATION,
            PermissionManager.ACCESS_COARSE_LOCATION,
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                add(FOREGROUND_SERVICE_LOCATION)
            }
        }
    }

    val text = stringResource(R.string.emergency_sms_permissions_updated_toast)

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // After the user responds, update our state list to reflect the changes.
        // If a permission group requires no specific permissions (e.g., Notifications on old OS), consider it granted.
        PermissionManager.arePermissionsGranted(
            context,
            permissionsState.filter { PermissionManager.isPermissionGranted(context, it) })
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    val permissionsLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // After the user responds, update our state list to reflect the changes.
        // If a permission group requires no specific permissions (e.g., Notifications on old OS), consider it granted.
        PermissionManager.arePermissionsGranted(
            context,
            permissionsLocationState.filter { PermissionManager.isPermissionGranted(context, it) })
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            EmergencyTopBar(onBackClick = {
                navController.popBackStack()
            })
        },
        bottomBar = {
            EmergencyFooter(
                totalRecipients = uiState.totalRecipients,
                onSendAlertClick = {
                    val isPermissionsGranted =
                        PermissionManager.arePermissionsGranted(context, permissionsState)
                    if (
                        isPermissionsGranted
                    ) {
                        viewModel.onEvent(EmergencySmsEvent.SendAlert(context))
                    } else {
                        permissionsLauncher.launch(
                            permissionsState
                                .filter { !PermissionManager.isPermissionGranted(context, it) }
                                .toTypedArray())
                    }
                }
            )
        },
        containerColor = AppColors.Background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            // SECTION 2: MESSAGE AND QUICK ACTIONSf
            item {
                MessageAndActionsCard(
                    viewModel = viewModel,
                    message = uiState.message,
                    onMessageChange = { viewModel.onEvent(EmergencySmsEvent.MessageChanged(it)) },
                    areAllPredefinedSelected = uiState.areAllPredefinedSelected,
                    onSelectAll = {
                        uiState.allContacts.ifEmpty {
                            Toast.makeText(
                                context,
                                R.string.null_contacts_toast_message,
                                Toast.LENGTH_SHORT
                            ).show()
                            navController.navigate(Screen.Contacts.route)
                        }
                        viewModel.onEvent(EmergencySmsEvent.SelectAllPredefinedClicked)
                    },
                    onClearAll = {
                        viewModel.onEvent(EmergencySmsEvent.ClearAllPredefinedClicked)
                    },
                    onToggle = {
                        val isPermissionsGranted =
                            PermissionManager.arePermissionsGranted(
                                context,
                                permissionsLocationState
                            )
                        if (
                            isPermissionsGranted
                        ) {
                            viewModel.onEvent(EmergencySmsEvent.LocationSharingToggled)
                        } else {
                            permissionsLocationLauncher.launch(
                                permissionsLocationState
                                    .filter { !PermissionManager.isPermissionGranted(context, it) }
                                    .toTypedArray())
                        }
                    },
                )
            }
            // SECTION 1: RECIPIENTS
            item {
                ManualRecipientSection(
                    manualNumberInput = uiState.manualNumberInput,
                    onManualNumberChange = {
                        viewModel.onEvent(
                            EmergencySmsEvent.ManualNumberInputChanged(
                                it
                            )
                        )
                    },
                    manualNumbers = uiState.manualNumbers,
                    onAddManualNumber = { viewModel.onEvent(EmergencySmsEvent.ManualNumberAdded) },
                    onRemoveManualNumber = {
                        viewModel.onEvent(
                            EmergencySmsEvent.ManualNumberRemoved(
                                it
                            )
                        )
                    }
                )
            }

            item {
                PredefinedContactsCard(
                    contacts = uiState.allContacts,
                    selectedContactIds = uiState.selectedContactIds,
                    isExpanded = uiState.isPredefinedContactsExpanded,
                    onToggle = { viewModel.onEvent(EmergencySmsEvent.PredefinedListToggled) },
                    onContactSelected = { contactId ->
                        viewModel.onEvent(
                            EmergencySmsEvent.PredefinedContactToggled(
                                contactId
                            )
                        )
                    }
                )
            }

        }
    }
}

// --- SUB-COMPONENTS ---

// MODIFIED COMPONENT
@Composable
fun MessageAndActionsCard(
    viewModel: EmergencySmsViewModel,
    message: String,
    onMessageChange: (String) -> Unit,
    areAllPredefinedSelected: Boolean,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    onToggle: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = message,
                onValueChange = onMessageChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 150.dp),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = AppColors.Background,
                    unfocusedContainerColor = AppColors.Background,
                    focusedBorderColor = AppColors.PrimaryBlue,
                    unfocusedBorderColor = Color.Transparent,
                )
            )
            Spacer(Modifier.height(16.dp))
            Divider()
            Spacer(Modifier.height(16.dp))

            // COMBINED BUTTON LOGIC
            // Shows a different button based on whether all contacts are selected.
            if (areAllPredefinedSelected) {
                // "Clear All" button, styled with red to indicate a clearing action.
                OutlinedButton(
                    onClick = onClearAll,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AppColors.Red),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = AppColors.Red
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.ClearAll,
                        contentDescription = stringResource(R.string.content_description_clear_all_icon)
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.action_clear_all))
                }
            } else {
                // "Add All" button, styled as a primary action.
                Button(
                    onClick = onSelectAll,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.PrimaryBlue.copy(alpha = 0.1f),
                        contentColor = AppColors.PrimaryBlue
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Groups,
                        contentDescription = stringResource(R.string.content_description_add_all_icon)
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.action_add_all))
                }
            }

            Spacer(Modifier.size(16.dp))

            SendLocationCard(
                isLocationSharingOn = uiState.isLocationSharingOn,
                onToggle = { onToggle() }
            )

        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyTopBar(onBackClick: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                stringResource(R.string.emergency_sms_screen_title),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    stringResource(R.string.content_description_back)
                )
            }
        },
        actions = { Spacer(Modifier.width(48.dp)) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = AppColors.Background,
            titleContentColor = AppColors.TextPrimary,
            navigationIconContentColor = AppColors.TextPrimary
        ),
        modifier = Modifier.shadow(2.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManualRecipientSection(
    manualNumberInput: String,
    onManualNumberChange: (String) -> Unit,
    manualNumbers: Set<String>,
    onAddManualNumber: () -> Unit,
    onRemoveManualNumber: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = manualNumberInput,
            onValueChange = onManualNumberChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.emergency_sms_manual_contact_label)) },
            placeholder = { Text(stringResource(R.string.emergency_sms_manual_contact_placeholder)) },
            leadingIcon = {
                Icon(
                    Icons.Default.PersonAdd,
                    contentDescription = null,
                    tint = AppColors.TextSecondary
                )
            },
            trailingIcon = {
                if (manualNumberInput.isNotBlank()) {
                    IconButton(onClick = onAddManualNumber) {
                        Icon(
                            Icons.Default.AddCircle,
                            stringResource(R.string.content_description_add_number),
                            tint = AppColors.PrimaryBlue
                        )
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = AppColors.Surface,
                unfocusedContainerColor = AppColors.Surface,
            )
        )
        if (manualNumbers.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                manualNumbers.forEach { number ->
                    InputChip(
                        selected = false, onClick = { onRemoveManualNumber(number) },
                        label = { Text(number) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Cancel,
                                stringResource(R.string.content_description_remove),
                                Modifier.size(18.dp)
                            )
                        },
                        colors = InputChipDefaults.inputChipColors(
                            containerColor = AppColors.PrimaryBlue.copy(
                                alpha = 0.1f
                            )
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun PredefinedContactsCard(
    contacts: List<EmergencyContact>, selectedContactIds: Set<String>, isExpanded: Boolean,
    onToggle: () -> Unit, onContactSelected: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AppColors.Border.copy(alpha = 0.7f)),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${stringResource(R.string.emergency_sms_predefined_contacts_title)} (${selectedContactIds.size})")
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = stringResource(if (isExpanded) R.string.content_description_collapse else R.string.content_description_expand)
                )
            }
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(tween(300)),
                exit = shrinkVertically(tween(300))
            ) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Divider(Modifier.padding(bottom = 8.dp))
                    if (contacts.isEmpty()) {
                        Text(
                            stringResource(R.string.emergency_sms_no_predefined_contacts),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.TextSecondary,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        contacts.forEach { contact ->
                            ContactItem(
                                contact = contact,
                                isSelected = contact.id in selectedContactIds,
                                onToggleSelection = { onContactSelected(contact.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SendLocationCard(isLocationSharingOn: Boolean, onToggle: () -> Unit) {
    val cardColor by animateColorAsState(
        targetValue = if (isLocationSharingOn) AppColors.Blue else AppColors.TextSecondary.copy(
            alpha = 0.7f
        ),
        animationSpec = spring(),
        label = "LocationCardColor"
    )
    val icon by remember(isLocationSharingOn) { mutableStateOf(if (isLocationSharingOn) Icons.Filled.MyLocation else Icons.Filled.LocationOff) }
    val text =
        stringResource(if (isLocationSharingOn) R.string.location_sharing_on else R.string.location_sharing_off)
    val elevation by animateDpAsState(
        targetValue = if (isLocationSharingOn) 8.dp else 2.dp,
        animationSpec = spring(),
        label = "LocationCardElevation"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor, contentColor = Color.White),
        onClick = onToggle
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier) {
                Text(
                    text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
//                Text("Your location will be appended", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun ContactItem(
    contact: EmergencyContact,
    isSelected: Boolean,
    onToggleSelection: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onToggleSelection() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(contact)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                contact.name,
                style = MaterialTheme.typography.bodyLarge,
                color = AppColors.TextPrimary
            )
            Text(
                contact.phone,
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary
            )
        }
        Checkbox(
            checked = isSelected,
            onCheckedChange = null, // The whole row is clickable
            colors = CheckboxDefaults.colors(checkedColor = AppColors.PrimaryBlue)
        )
    }
}

@Composable
fun Avatar(contact: EmergencyContact) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(contact.avatarColor),
        contentAlignment = Alignment.Center
    ) {
        if (contact.avatarIcon != null) {
            Icon(
                contact.avatarIcon,
                contact.name,
                tint = AppColors.PrimaryBlue,
                modifier = Modifier.size(24.dp)
            )
        } else {
            Text(contact.initials, color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun EmergencyFooter(totalRecipients: Int, onSendAlertClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shadowElevation = 8.dp, color = AppColors.Surface) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.emergency_sms_total_recipients_label),
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppColors.TextSecondary
                )
                Text(
                    "$totalRecipients",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.PrimaryBlue
                )
            }
            Button(
                onClick = onSendAlertClick,
                enabled = totalRecipients > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.PrimaryBlue,
                    disabledContainerColor = AppColors.TextSecondary.copy(alpha = 0.5f)
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Icon(Icons.Default.Send, null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(
                    stringResource(R.string.action_send_alert),
                    style = MaterialTheme.typography.labelLarge,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

// --- PREVIEW ---
@Preview(showBackground = true, showSystemUi = true)
@Composable
fun EmergencySmsScreenPreview() {
    // A dummy list of contacts for the preview
    val sampleContacts = listOf(
        Contact("1", "Jane Doe", "555-1234"),
        Contact("2", "John Smith", "555-5678"),
        Contact("3", "Police Department", "911")
    )
    GuardianTheme {
        // The preview now correctly works with the ViewModel pattern
        EmergencySmsScreen(navController = rememberNavController())
    }
}