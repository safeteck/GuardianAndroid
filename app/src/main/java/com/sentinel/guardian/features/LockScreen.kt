package com.sentinel.guardian.features

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.sentinel.guardian.R

/**
 * A repository for managing the storage of the encrypted password.
 * It uses SharedPreferences to persist the password data.
 */
class PasswordRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE)

    fun saveEncryptedPassword(encryptedPassword: ByteArray) {
        val base64Password = Base64.encodeToString(encryptedPassword, Base64.NO_WRAP)
        prefs.edit { putString(KEY_ENCRYPTED_PASSWORD, base64Password) }
    }

    fun loadEncryptedPassword(): ByteArray? {
        val base64Password = prefs.getString(KEY_ENCRYPTED_PASSWORD, null)
        return if (base64Password != null) {
            Base64.decode(base64Password, Base64.NO_WRAP)
        } else {
            null
        }
    }

    companion object {
        // These are internal keys and should not be moved to strings.xml
        private const val PREFS_FILENAME = "auth_prefs"
        private const val KEY_ENCRYPTED_PASSWORD = "encrypted_password"
    }
}

// ViewModel Factory for creating LoginViewModel with dependencies
class LoginViewModelFactory(
    private val application: Application,
    private val cryptoManager: CryptoManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LoginViewModel(application, cryptoManager) as T
        }
        // This is a developer-facing error message, best to leave it in English for crash reporting.
        // It is also included in strings.xml for completeness if you decide to translate it.
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}


class LoginViewModel(
    application: Application,
    private val cryptoManager: CryptoManager
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Loading)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val passwordRepository = PasswordRepository(application)
    private var encryptedPassword: ByteArray? = null

    init {
        encryptedPassword = passwordRepository.loadEncryptedPassword()
        _uiState.value = if (encryptedPassword != null) {
            LoginUiState.Locked
        } else {
            LoginUiState.PasswordNotSet
        }
    }

    fun setPassword(password: String) {
        viewModelScope.launch {
            val newEncryptedPassword = cryptoManager.encrypt(password)
            passwordRepository.saveEncryptedPassword(newEncryptedPassword)
            encryptedPassword = newEncryptedPassword
            _uiState.value = LoginUiState.Unlocked
        }
    }

    fun checkPassword(password: String) {
        viewModelScope.launch {
            if (encryptedPassword != null) {
                try {
                    val decryptedPassword = cryptoManager.decrypt(encryptedPassword!!)
                    if (decryptedPassword == password) {
                        _uiState.value = LoginUiState.Unlocked
                    } else {
                        // CHANGED
                        _uiState.value =
                            LoginUiState.Error(getApplication<Application>().getString(R.string.error_incorrect_password))
                    }
                } catch (e: Exception) {
                    // CHANGED
                    _uiState.value =
                        LoginUiState.Error(getApplication<Application>().getString(R.string.error_decryption_failed))
                }
            } else {
                // CHANGED
                _uiState.value =
                    LoginUiState.Error(getApplication<Application>().getString(R.string.error_no_password_set))
            }
        }
    }

    fun biometricUnlock() {
        _uiState.value = LoginUiState.Unlocked
    }
}

sealed class LoginUiState {
    object Loading : LoginUiState()
    object PasswordNotSet : LoginUiState()
    object Locked : LoginUiState()
    object Unlocked : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}


@Composable
fun LoginScreen(
    onUnlock: () -> Unit,
    showBiometricPrompt: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val cryptoManager = remember { CryptoManager(context) }
    val viewModel: LoginViewModel = viewModel(
        factory = LoginViewModelFactory(application, cryptoManager)
    )
    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        is LoginUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is LoginUiState.PasswordNotSet -> {
            PasswordSetupScreen(onPasswordSet = { viewModel.setPassword(it) })
        }

        is LoginUiState.Unlocked -> {
            LaunchedEffect(Unit) {
                onUnlock()
            }
        }

        is LoginUiState.Locked -> {
            UnlockScreen(
                onUnlock = { viewModel.checkPassword(it) },
                showBiometricPrompt = showBiometricPrompt,
                error = null
            )
        }

        is LoginUiState.Error -> {
            UnlockScreen(
                onUnlock = { viewModel.checkPassword(it) },
                showBiometricPrompt = showBiometricPrompt,
                error = state.message
            )
        }
    }
}

@Composable
fun PasswordSetupScreen(onPasswordSet: (String) -> Unit) {
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val isPasswordValid = newPassword.length >= 6 && newPassword == confirmPassword

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // CHANGED
        Text(
            stringResource(id = R.string.password_setup_title),
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        // CHANGED
        Text(
            stringResource(id = R.string.password_setup_guideline),
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = newPassword,
            onValueChange = { newPassword = it },
            // CHANGED
            label = { Text(stringResource(id = R.string.label_enter_password)) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            // CHANGED
            label = { Text(stringResource(id = R.string.label_confirm_password)) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            isError = confirmPassword.isNotEmpty() && newPassword != confirmPassword
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = { onPasswordSet(newPassword) },
            enabled = isPasswordValid
        ) {
            // CHANGED
            Text(stringResource(id = R.string.button_set_password))
        }
    }
}

@Composable
fun UnlockScreen(
    onUnlock: (String) -> Unit,
    showBiometricPrompt: () -> Unit,
    error: String?
) {
    var enteredPassword by remember { mutableStateOf("") }
    val context = LocalContext.current

    val biometricManager = BiometricManager.from(context)
    val canAuthenticate =
        biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // CHANGED
        Text(
            stringResource(id = R.string.unlock_screen_title),
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedTextField(
            value = enteredPassword,
            onValueChange = { enteredPassword = it },
            // CHANGED
            label = { Text(stringResource(id = R.string.label_enter_password)) },
            visualTransformation = PasswordVisualTransformation(),
            isError = error != null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { onUnlock(enteredPassword) },
            modifier = Modifier.fillMaxWidth()
        ) {
            // CHANGED
            Text(stringResource(id = R.string.button_unlock_with_password))
        }
        if (canAuthenticate) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = { showBiometricPrompt() },
                modifier = Modifier.fillMaxWidth()
            ) {
                // CHANGED
                Text(stringResource(id = R.string.button_unlock_with_biometrics))
            }
        }
    }
}