package com.sentinel.guardian.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadKeyTemplates
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.util.Base64

// -------------------
// 1. DATA MODEL
// -------------------
@Serializable
enum class RecipientOption {
    ALL_EMERGENCY_CONTACTS,
    ONE_EMERGENCY_CONTACT
}

@Serializable
data class AlertSettings(
    val customMessage: String = "این یک پیام اضطراری است. لطفاً کمک کنید.",
    val sendLocation: Boolean = true,
    val playSound: Boolean = true,
    val recipientOption: RecipientOption = RecipientOption.ALL_EMERGENCY_CONTACTS,
    val specificContactId: String? = null // ID مخاطب خاص
)

// -------------------
// 2. CRYPTOGRAPHY MANAGER (Using Google Tink)
// -------------------
class CryptoManager(context: Context) {

    private val keysetName = "alert_settings_keyset"
    private val preferenceFile = "alert_settings_keystore"
    private val masterKeyUri = "android-keystore://alert_settings_master_key"

    private val keysetHandle: KeysetHandle = AndroidKeysetManager.Builder()
        .withSharedPref(context, keysetName, preferenceFile)
        .withKeyTemplate(AeadKeyTemplates.AES256_GCM)
        .withMasterKeyUri(masterKeyUri)
        .build()
        .keysetHandle

    private val aead: Aead = keysetHandle.getPrimitive(Aead::class.java)

    fun encrypt(plainText: String): String {
        try {
            val plainTextBytes = plainText.toByteArray(StandardCharsets.UTF_8)
            val cipherTextBytes = aead.encrypt(plainTextBytes, null)
            return Base64.getEncoder().encodeToString(cipherTextBytes)
        } catch (e: GeneralSecurityException) {
            // در یک اپ واقعی، این خطا را لاگ کنید
            throw IOException("Error during encryption", e)
        }
    }

    fun decrypt(cipherText: String): String {
        try {
            val cipherTextBytes = Base64.getDecoder().decode(cipherText)
            val plainTextBytes = aead.decrypt(cipherTextBytes, null)
            return String(plainTextBytes, StandardCharsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            // در یک اپ واقعی، این خطا را لاگ کنید
            throw IOException("Error during decryption", e)
        }
    }
}

// -------------------
// 3. SETTINGS REPOSITORY (Handles data persistence)
// -------------------
class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("alert_settings_prefs", Context.MODE_PRIVATE)
    private val cryptoManager = CryptoManager(context)
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val KEY_ALERT_SETTINGS = "encrypted_alert_settings"
    }

    fun saveSettings(settings: AlertSettings) {
        try {
            val jsonString = json.encodeToString(settings)
            val encryptedString = cryptoManager.encrypt(jsonString)
            prefs.edit().putString(KEY_ALERT_SETTINGS, encryptedString).apply()
        } catch (e: Exception) {
            // لاگ کردن خطا
            e.printStackTrace()
        }
    }

    fun loadSettings(): AlertSettings {
        return try {
            val encryptedString = prefs.getString(KEY_ALERT_SETTINGS, null)
            if (encryptedString.isNullOrEmpty()) {
                AlertSettings() // بازگرداندن مقدار پیش‌فرض
            } else {
                val jsonString = cryptoManager.decrypt(encryptedString)
                json.decodeFromString<AlertSettings>(jsonString)
            }
        } catch (e: Exception) {
            // در صورت خطا در رمزگشایی یا پارس کردن، مقدار پیش‌فرض را برگردان
            e.printStackTrace()
            AlertSettings()
        }
    }
}

// -------------------
// 4. VIEWMODEL (Handles UI logic and state)
// -------------------
class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AlertSettings())
    val uiState = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _uiState.value = repository.loadSettings()
        }
    }

    private fun saveCurrentSettings() {
        viewModelScope.launch {
            repository.saveSettings(_uiState.value)
        }
    }

    fun onMessageChange(newMessage: String) {
        _uiState.update { it.copy(customMessage = newMessage) }
        saveCurrentSettings()
    }

    fun onSendLocationToggle(isEnabled: Boolean) {
        _uiState.update { it.copy(sendLocation = isEnabled) }
        saveCurrentSettings()
    }

    fun onPlaySoundToggle(isEnabled: Boolean) {
        _uiState.update { it.copy(playSound = isEnabled) }
        saveCurrentSettings()
    }

    fun onRecipientOptionChange(option: RecipientOption) {
        _uiState.update { it.copy(recipientOption = option) }
        saveCurrentSettings()
    }

    fun onSpecificContactSelected(contactId: String) {
        // در این مثال ساده فقط ID ذخیره می‌شود
        // در اپ واقعی، ممکن است اطلاعات بیشتری از مخاطب را نیاز داشته باشید
        _uiState.update { it.copy(specificContactId = contactId) }
        saveCurrentSettings()
    }

    // Factory for creating ViewModel with dependencies
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return SettingsViewModel(SettingsRepository(context.applicationContext)) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

// -------------------
// 5. COMPOSABLE SCREEN
// -------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertSettingsScreen(navController: NavHostController) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(context))
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تنظیمات پیام اضطراری") },
                navigationIcon = {
                    IconButton(onClick = {
                        navController.popBackStack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "test description1"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // بخش پیام سفارشی
            Section(title = "متن پیام") {
                OutlinedTextField(
                    value = uiState.customMessage,
                    onValueChange = viewModel::onMessageChange,
                    label = { Text("متن پیام سفارشی") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }

            // بخش تنظیمات عمومی
            Section(title = "تنظیمات عمومی") {
                SettingsSwitchRow(
                    title = "ارسال موقعیت مکانی",
                    subtitle = "موقعیت فعلی شما به پیام ضمیمه شود.",
                    checked = uiState.sendLocation,
                    onCheckedChange = {
                        // TODO: Check for LOCATION permission before enabling
                        viewModel.onSendLocationToggle(it)
                    }
                )
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                SettingsSwitchRow(
                    title = "پخش صدا",
                    subtitle = "هنگام ارسال هشدار، صدا پخش شود.",
                    checked = uiState.playSound,
                    onCheckedChange = viewModel::onPlaySoundToggle
                )
            }

            // بخش انتخاب مخاطبین
            Section(title = "گیرندگان پیام") {
                // TODO: Check for READ_CONTACTS permission before showing this section
                RecipientOptionRow(
                    text = "ارسال به تمام مخاطبین اضطراری",
                    selected = uiState.recipientOption == RecipientOption.ALL_EMERGENCY_CONTACTS,
                    onClick = { viewModel.onRecipientOptionChange(RecipientOption.ALL_EMERGENCY_CONTACTS) }
                )
                RecipientOptionRow(
                    text = "انتخاب یک مخاطب اضطراری",
                    selected = uiState.recipientOption == RecipientOption.ONE_EMERGENCY_CONTACT,
                    onClick = { viewModel.onRecipientOptionChange(RecipientOption.ONE_EMERGENCY_CONTACT) }
                )

                // نمایش دکمه انتخاب مخاطب فقط در صورت نیاز
                AnimatedVisibility(visible = uiState.recipientOption == RecipientOption.ONE_EMERGENCY_CONTACT) {
                    Button(
                        onClick = {
                            // TODO: Implement contact picker logic
                            // پس از انتخاب، ID مخاطب را به ViewModel پاس دهید
                            // viewModel.onSpecificContactSelected("contact_id_from_picker")
                        },
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        Text(
                            if (uiState.specificContactId == null) "یک مخاطب انتخاب کنید"
                            else "تغییر مخاطب انتخاب شده"
                        )
                    }
                }
            }
        }
    }
}

// -------------------
// HELPER COMPOSABLES
// -------------------
@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(content = content)
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

@Composable
private fun RecipientOptionRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}