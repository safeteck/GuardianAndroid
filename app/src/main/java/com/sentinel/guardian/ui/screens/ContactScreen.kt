package com.sentinel.guardian.ui.screens

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.sentinel.guardian.R
import com.sentinel.guardian.Contact
import com.sentinel.guardian.ContactCategory
import com.sentinel.guardian.ui.screens.features.ContactsFeatures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.SortedMap
import java.util.UUID

// =================================================================================
// 3. DATA LAYER (بدون تغییر)
// =================================================================================
//For Saved Contacts in the file
class ContactManager(private val context: Context) {
    companion object {
        private const val FILE_NAME = "emergency_contacts.json.encrypted"
        private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    // We need a reference to the plain File object to delete it
    private val contactFile = File(context.filesDir, FILE_NAME)

    private val encryptedFile = EncryptedFile.Builder(
        context,
        contactFile, // Use the same File object
        masterKey,
        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
    ).build()

    suspend fun saveContacts(contacts: List<Contact>) {
        withContext(Dispatchers.IO) {
            try {
                if (contactFile.exists()) {
                    contactFile.delete()
                }
                val jsonString = json.encodeToString(contacts)
                encryptedFile.openFileOutput().use { it.write(jsonString.toByteArray()) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun loadContacts(): List<Contact> {
        return withContext(Dispatchers.IO) {
            try {
                if (!contactFile.exists()) return@withContext emptyList()
                val jsonString =
                    encryptedFile.openFileInput().bufferedReader().use { it.readText() }
                if (jsonString.isBlank()) return@withContext emptyList()
                json.decodeFromString<List<Contact>>(jsonString)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }
}

// =================================================================================
// 4. VIEWMODEL (نسخه اصلاح شده)
// =================================================================================
class ContactsViewModel(private val application: Application) : AndroidViewModel(application) {
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

    suspend fun addContact(name: String, phone: String, category: ContactCategory) {
        val newContact = Contact(
            id = UUID.randomUUID().toString(),
            name = name,
            phoneNumber = phone,
            category = category
        )
        val updatedList = _contacts.value + newContact
        _contacts.value = updatedList
        contactManager.saveContacts(updatedList) // Save immediately
    }

    suspend fun updateContactCategory(contactToUpdate: Contact, newCategory: ContactCategory) {
        val updatedList = _contacts.value.map {
            if (it.id == contactToUpdate.id) it.copy(category = newCategory) else it
        }
        _contacts.value = updatedList
        contactManager.saveContacts(updatedList) // Save immediately
    }

    suspend fun deleteContact(contactToDelete: Contact) {
        val updatedList = _contacts.value.filterNot { it.id == contactToDelete.id }
        _contacts.value = updatedList
        contactManager.saveContacts(updatedList) // Save immediately
    }

    suspend fun addImportedContacts(contactsToImport: List<Contact>) {
        val currentContacts = _contacts.value
        val existingNumbers = currentContacts.map { it.phoneNumber }.toSet()
        val newUniqueContacts =
            contactsToImport.filterNot { existingNumbers.contains(it.phoneNumber) }
        if (newUniqueContacts.isNotEmpty()) {
            val updatedList = currentContacts + newUniqueContacts
            _contacts.value = updatedList
            contactManager.saveContacts(updatedList) // Save immediately
            val message = application.resources.getQuantityString(
                R.plurals.snackbar_new_contacts_imported,
                newUniqueContacts.size,
                newUniqueContacts.size
            )
            _snackbarMessage.emit(message)
        } else {
            _snackbarMessage.emit(application.getString(R.string.snackbar_contacts_already_exist_or_empty))
        }
    }

    fun showPermissionDeniedMessage() {
        viewModelScope.launch {
            _snackbarMessage.emit(application.getString(R.string.snackbar_permission_denied))
        }
    }
}


// =================================================================================
// 5. UI LAYER / COMPOSABLES (نسخه کامل و اصلاح شده)
// =================================================================================

// --- Custom Colors ---
val LightGray = Color(0xFFF0F0F0)
val SearchBarGray = Color(0xFFF5F5F5)
val BrandBlue = Color(0xFF0D6EFE)
val LightRed = Color(0xFFFFEBEE)
val DarkRed = Color(0xFFE57373)
val FriendIconYellow = Color(0xFFFBC02D)
val TextGray = Color(0xFF8A8A8E)

// --- Main Screen Composable ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(viewModel: ContactsViewModel = viewModel()) {
    val contacts by viewModel.contacts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("all") }
    var showAddContactDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope() // Scope for launching suspend functions from UI events

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            showImportDialog = true
        } else {
            viewModel.showPermissionDeniedMessage()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    if (showAddContactDialog) {
        AddContactDialog(
            onDismiss = { showAddContactDialog = false },
            onAddContact = { name, phone, category ->
                scope.launch {
                    viewModel.addContact(name, phone, category)
                }
                showAddContactDialog = false
            }
        )
    }

    if (showImportDialog) {
        ImportContactsDialog(
            onDismiss = { showImportDialog = false },
            onImportConfirmed = { selectedContacts ->
                scope.launch {
                    viewModel.addImportedContacts(selectedContacts)
                }
                showImportDialog = false
            }
        )
    }

    Scaffold(
        containerColor = Color.White,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            ScreenHeader(
                onAddClick = { showAddContactDialog = true },
                onImportClick = {
                    when (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_CONTACTS
                    )) {
                        PackageManager.PERMISSION_GRANTED -> {
                            showImportDialog = true
                        }

                        else -> {
                            permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                        }
                    }
                }
            )
            Spacer(modifier = Modifier.height(24.dp))
            SearchBar(query = searchQuery, onQueryChange = { searchQuery = it })
            Spacer(modifier = Modifier.height(24.dp))

            InfoCardSimple()

            Spacer(modifier = Modifier.height(24.dp))
            FilterChips(selectedFilter = selectedFilter, onFilterSelected = { selectedFilter = it })
            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                val contactsToDisplay = remember(contacts, selectedFilter, searchQuery) {
                    val filteredByCategory = if (selectedFilter == "all") {
                        contacts
                    } else {
                        contacts.filter { it.category.name.lowercase() == selectedFilter }
                    }
                    if (searchQuery.isBlank()) {
                        filteredByCategory
                    } else {
                        filteredByCategory.filter {
                            it.name.contains(searchQuery, ignoreCase = true) ||
                                    it.phoneNumber.contains(searchQuery, ignoreCase = true)
                        }
                    }
                }

                val groupedContacts = contactsToDisplay
                    .groupBy { it.category }
                    .toSortedMap(compareBy { it.ordinal })

                ContactList(
                    groupedContacts = groupedContacts,
                    onUpdateCategory = { contact, category ->
                        scope.launch { viewModel.updateContactCategory(contact, category) }
                    },
                    onDeleteContact = { contact ->
                        scope.launch { viewModel.deleteContact(contact) }
                    }
                )
            }
        }
    }
}


// --- Reusable UI Components (نسخه اصلاح شده با منابع رشته) ---

@Composable
fun InfoCardSimple() {
    Card(
        modifier = Modifier
            .fillMaxWidth() // تمام عرض صفحه را اشغال کند
            .padding(horizontal = 16.dp), // فاصله‌گذاری از لبه‌های صفحه
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface) // استفاده از رنگ‌های تم
    ) {
        // استفاده از Row برای قرار دادن آیکون و متن در کنار هم
        Row(
            modifier = Modifier.padding(16.dp), // یک فاصله‌گذاری یکپارچه برای محتوای کارت
            verticalAlignment = Alignment.CenterVertically // تراز کردن عمودی آیکون و متن
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "اطلاعات", // توضیحات معنادار برای دسترسی‌پذیری
                tint = MaterialTheme.colorScheme.primary // استفاده از رنگ اصلی تم برای تاکید
            )

            // ایجاد فاصله افقی بین آیکون و متن
            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = stringResource(R.string.contacts_info_text_content),
                style = MaterialTheme.typography.bodyMedium, // استفاده از استایل متن استاندارد
                color = MaterialTheme.colorScheme.onSurface // رنگ متنی که روی سطح کارت خوانا باشد
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportContactsDialog(
    onDismiss: () -> Unit,
    onImportConfirmed: (List<Contact>) -> Unit
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var phoneContacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var selectedContacts by remember { mutableStateOf<Set<Contact>>(emptySet()) }

    LaunchedEffect(Unit) {
        isLoading = true
        withContext(Dispatchers.IO) {
            phoneContacts = ContactsFeatures.getAllContacts(context)
        }
        isLoading = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(id = R.string.import_contacts_dialog_title)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(id = R.string.button_close)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            },
            floatingActionButton = {
                if (!isLoading && phoneContacts.isNotEmpty()) {
                    ExtendedFloatingActionButton(
                        text = {
                            Text(
                                pluralStringResource(
                                    id = R.plurals.button_import_n_contacts,
                                    count = selectedContacts.size,
                                    formatArgs = arrayOf(selectedContacts.size)
                                )
                            )
                        },
                        icon = { Icon(Icons.Default.Download, contentDescription = null) },
                        onClick = { onImportConfirmed(selectedContacts.toList()) },
                        containerColor = if (selectedContacts.isEmpty()) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (selectedContacts.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            },
            floatingActionButtonPosition = FabPosition.Center
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator()
                } else if (phoneContacts.isEmpty()) {
                    Text(
                        stringResource(id = R.string.import_dialog_no_contacts_found),
                        color = TextGray
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(phoneContacts) { contact ->
                            SelectableContactItem(
                                contact = contact,
                                isSelected = contact in selectedContacts,
                                onToggle = {
                                    selectedContacts = if (it in selectedContacts) {
                                        selectedContacts - it
                                    } else {
                                        selectedContacts + it
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SelectableContactItem(
    contact: Contact,
    isSelected: Boolean,
    onToggle: (Contact) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(contact) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle(contact) }
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(contact.name, fontWeight = FontWeight.SemiBold)
            Text(contact.phoneNumber, color = TextGray)
        }
    }
}

@Composable
fun ScreenHeader(onAddClick: () -> Unit, onImportClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            stringResource(id = R.string.contacts_screen_title),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
        Row {
            IconButton(onClick = onImportClick) {
                Icon(
                    Icons.Filled.GroupAdd,
                    stringResource(id = R.string.cd_import_contacts),
                    tint = BrandBlue,
                    modifier = Modifier.size(32.dp)
                )
            }
            IconButton(onClick = onAddClick) {
                Icon(
                    Icons.Filled.Add,
                    stringResource(id = R.string.cd_add_contact),
                    tint = BrandBlue,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(query: String, onQueryChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text(
                stringResource(id = R.string.search_bar_placeholder),
                color = TextGray
            )
        },
        leadingIcon = {
            Icon(
                Icons.Filled.Search,
                stringResource(id = R.string.cd_search_icon),
                tint = TextGray
            )
        },
        shape = RoundedCornerShape(12.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = SearchBarGray,
            unfocusedContainerColor = SearchBarGray,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = BrandBlue
        )
    )
}

@Composable
fun getCategoryDisplayName(categoryKey: String): String {
    return when (categoryKey.lowercase()) {
        "all" -> stringResource(id = R.string.filter_all)
        "emergency" -> stringResource(id = R.string.category_emergency)
        "friends" -> stringResource(id = R.string.category_friends)
        "private" -> stringResource(id = R.string.category_private)
        "general" -> stringResource(id = R.string.category_general)
        else -> categoryKey.replaceFirstChar { it.uppercase() } // Fallback
    }
}

@Composable
fun FilterChips(selectedFilter: String, onFilterSelected: (String) -> Unit) {
    val filters =
        ContactCategory.entries.map { it.name.lowercase() }.toMutableList().apply { add(0, "all") }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(filters) { filter ->
            val isSelected = selectedFilter == filter
            val isEmergency = filter == ContactCategory.EMERGENCY.name.lowercase()
            val backgroundColor = when {
                isEmergency && !isSelected -> LightRed
                isSelected -> BrandBlue
                else -> LightGray
            }
            val textColor = when {
                isEmergency && !isSelected -> DarkRed
                isSelected -> Color.White
                else -> Color.Black
            }
            TextButton(
                onClick = { onFilterSelected(filter) },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.textButtonColors(containerColor = backgroundColor),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    getCategoryDisplayName(filter),
                    color = textColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun ContactList(
    groupedContacts: SortedMap<ContactCategory, List<Contact>>,
    onUpdateCategory: (Contact, ContactCategory) -> Unit,
    onDeleteContact: (Contact) -> Unit
) {
    if (groupedContacts.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(id = R.string.contacts_list_empty),
                color = TextGray,
                fontSize = 16.sp
            )
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            groupedContacts.forEach { (category, contacts) ->
                item(key = "header_${category.name}") {
                    ContactListHeader(getContactAvatar(category.name))
                }
                items(contacts, key = { it.id }) { contact ->
                    ContactItem(
                        contact = contact,
                        onUpdateCategory = onUpdateCategory,
                        onDeleteContact = onDeleteContact
                    )
                }
            }
        }
    }
}

fun getContactAvatar(category: String): ContactAvatar {
    return when (category.uppercase()) {
        "EMERGENCY" -> ContactAvatar.EMERGENCY
        "FRIENDS" -> ContactAvatar.FRIENDS
        "PRIVATE" -> ContactAvatar.PRIVATE
        else -> ContactAvatar.GENERAL
    }
}

enum class ContactAvatar(
    @StringRes val displayNameResId: Int,
    val icon: ImageVector,
    val iconColor: Color
) {
    EMERGENCY(R.string.category_emergency, Icons.Outlined.Info, DarkRed),
    FRIENDS(R.string.category_friends, Icons.Filled.People, FriendIconYellow),
    GENERAL(R.string.category_general, Icons.Default.Person, TextGray),
    PRIVATE(R.string.category_private, Icons.Default.Lock, Color.DarkGray)
}

@Composable
fun ContactListHeader(category: ContactAvatar) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    ) {
        Icon(
            category.icon,
            contentDescription = stringResource(id = category.displayNameResId),
            tint = category.iconColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(id = category.displayNameResId),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
    }
}

@Composable
fun ContactItem(
    contact: Contact,
    onUpdateCategory: (Contact, ContactCategory) -> Unit,
    onDeleteContact: (Contact) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* TODO: Handle click to view details */ }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(LightGray),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = contact.name.firstOrNull()?.toString()?.uppercase() ?: "",
                fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Color.DarkGray
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                contact.name,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(contact.phoneNumber, fontSize = 15.sp, color = TextGray)
        }
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    Icons.Filled.MoreVert,
                    stringResource(id = R.string.cd_more_options_for_contact, contact.name),
                    tint = TextGray
                )
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                if (contact.category == ContactCategory.EMERGENCY) {
                    DropdownMenuItem(
                        text = { Text(stringResource(id = R.string.menu_item_remove_from_emergency)) },
                        onClick = {
                            onUpdateCategory(contact, ContactCategory.GENERAL)
                            showMenu = false
                        }
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text(stringResource(id = R.string.menu_item_add_to_emergency)) },
                        onClick = {
                            onUpdateCategory(contact, ContactCategory.EMERGENCY)
                            showMenu = false
                        }
                    )
                }
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(id = R.string.button_delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = {
                        onDeleteContact(contact)
                        showMenu = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddContactDialog(
    onDismiss: () -> Unit,
    onAddContact: (name: String, phone: String, category: ContactCategory) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var isNameError by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf(ContactCategory.GENERAL) }
    var isCategoryDropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.add_contact_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; isNameError = it.isBlank() },
                    label = { Text(stringResource(id = R.string.textfield_label_name)) },
                    singleLine = true,
                    isError = isNameError,
                    modifier = Modifier.fillMaxWidth()
                )
                if (isNameError) {
                    Text(
                        stringResource(id = R.string.error_name_cannot_be_empty),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                OutlinedTextField(
                    value = phone,
                    onValueChange = { it ->
                        // Filter non-digit characters
                        // Check if the new value contains only digits
                        if (it.all { it.isDigit() }) phone = it
                    },
                    label = { Text(stringResource(id = R.string.textfield_label_phone_number)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),

                )
                ExposedDropdownMenuBox(
                    expanded = isCategoryDropdownExpanded,
                    onExpandedChange = { isCategoryDropdownExpanded = !isCategoryDropdownExpanded },
                ) {
                    OutlinedTextField(
                        value = getCategoryDisplayName(selectedCategory.name.lowercase()),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(id = R.string.textfield_label_category)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isCategoryDropdownExpanded) },
                        colors = ExposedDropdownMenuDefaults.textFieldColors(),
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = isCategoryDropdownExpanded,
                        onDismissRequest = { isCategoryDropdownExpanded = false }
                    ) {
                        ContactCategory.entries.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(getCategoryDisplayName(category.name.lowercase())) },
                                onClick = {
                                    selectedCategory = category
                                    isCategoryDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onAddContact(name, phone, selectedCategory)
                    } else {
                        isNameError = true
                    }
                }
            ) { Text(stringResource(id = R.string.button_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(id = R.string.button_cancel)) }
        }
    )
}

// --- Preview Composable ---
@Preview(showBackground = true, widthDp = 375, heightDp = 812)
@Composable
fun ContactsScreenPreview() {
    // Note: Previews won't show the localized strings correctly unless you configure them to.
    // The main purpose is to check layout. The app on a device will show the correct language.
    ContactsScreen()
}