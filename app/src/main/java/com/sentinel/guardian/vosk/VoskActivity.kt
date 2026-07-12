package com.sentinel.guardian.vosk

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.sentinel.guardian.R
import com.sentinel.guardian.ui.theme.GuardianTheme
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.SpeechStreamService
import org.vosk.android.StorageService
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

// Changed from Activity to ComponentActivity
class VoskActivity : ComponentActivity(), RecognitionListener {

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var speechStreamService: SpeechStreamService? = null

    // --- State Management for Jetpack Compose ---
    // These MutableState objects hold the UI's state. When their values change,
    // Compose automatically redraws the parts of the UI that use them.
    private val uiState = mutableIntStateOf(STATE_START)
    private val resultText = mutableStateOf("")
    private val isListeningPaused = mutableStateOf(true) // Replaces ToggleButton state

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Replaces setContentView(R.layout.main)
        setContent {
            // Your app's theme. You can create a new one or use a default.
            GuardianTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // The main composable for your screen
                    VoskScreen(
                        uiState = uiState.intValue,
                        resultText = resultText.value,
                        isListeningPaused = isListeningPaused.value,
                        onRecognizeFile = ::recognizeFile,
                        onRecognizeMicrophone = ::recognizeMicrophone,
                        onPause = ::pause
                    )
                }
            }
        }

        // --- The rest of your logic remains largely the same ---
        LibVosk.setLogLevel(LogLevel.INFO)
        checkPermissionsAndInit()
    }

    private fun checkPermissionsAndInit() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PERMISSIONS_REQUEST_RECORD_AUDIO
            )
        } else {
            initModel()
        }
    }

    @SuppressLint("ImplicitSamInstance")
    private fun initModel() {
        setUiState(STATE_START)
        // Set initial text
        resultText.value = getString(R.string.preparing)
        try {
            val modelPath = "model-small-fa-pe" //TODO SET IT IN THE ASSET RESOURCES FOLDER
            StorageService.unpack(applicationContext, modelPath, "model", { model: Model? ->
                this.model = model
                setUiState(STATE_READY)
                Log.i(TAG, "Vosk model loaded successfully")
            }) { exception: IOException? ->
                Log.e(TAG, "Failed to unpack the model in VoskActivity.kt file in line 113: ${exception?.message}")
                setErrorState(getString(R.string.model_load_error))
                stopService(Intent(applicationContext, VoskService::class.java))
                finish()
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to initialize model: ${e.message}")
            setErrorState(getString(R.string.model_load_error))
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initModel()
            } else {
                setErrorState(getString(R.string.microphone_permission_denied))
                Toast.makeText(this, R.string.microphone_permission_denied, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    // --- RecognitionListener Callbacks ---
    // These now update the MutableState `resultText` instead of a TextView.
    override fun onResult(hypothesis: String?) {
        val cleanHypothesis = hypothesis?.drop(14)?.dropLast(3) ?: ""
        checkKeywordAndLaunch(cleanHypothesis)
        if (cleanHypothesis.contains("روشن")) {
            Toast.makeText(this, "روشن شدم\n", Toast.LENGTH_LONG).show()
            resultText.value += "rowshan detected\n"
        } else {
            Log.d("******************", cleanHypothesis)
            resultText.value += "$hypothesis\n"
        }
    }

    override fun onFinalResult(hypothesis: String?) {
        resultText.value += "$hypothesis\n"
        setUiState(STATE_DONE)
        speechStreamService?.let {
//            it.stop()
            speechStreamService = null
        }
    }

    override fun onPartialResult(hypothesis: String?) {
        // You could update a separate state for partial results if needed
        // resultText.value = hypothesis ?: ""
    }



    override fun onError(e: Exception) {
        setErrorState(e.message)
    }

    override fun onTimeout() {
        setUiState(STATE_DONE)
    }

    // --- UI State Management ---
    // These functions now update the state variables, triggering recomposition.
    private fun setUiState(state: Int) {
        uiState.intValue = state
        when (state) {
            STATE_START -> resultText.value = getString(R.string.preparing)
            STATE_READY -> resultText.value = getString(R.string.ready)
            STATE_FILE -> resultText.value = getString(R.string.starting)
            STATE_MIC -> resultText.value = getString(R.string.say_something)
            STATE_DONE -> isListeningPaused.value = true // Reset pause button
        }
    }

    private fun setErrorState(message: String?) {
        uiState.intValue = STATE_START // A general error state
        resultText.value = message ?: "Unknown error"
    }

    // --- Action Functions ---
    // These are called by the Compose UI event handlers.
    private fun recognizeFile() {
        if (speechStreamService != null) {
            setUiState(STATE_DONE)
            speechStreamService!!.stop()
            speechStreamService = null
        } else {
            if (model == null) {
                setErrorState(getString(R.string.model_not_ready))
                return
            }
            setUiState(STATE_FILE)
            try {
                val rec = Recognizer(model, 16000f)
                val ais: InputStream = assets.open("10001-90210-01803.wav")
                if (ais.skip(44) != 44L) throw IOException("File too short")

                speechStreamService = SpeechStreamService(rec, ais, 16000f)
                speechStreamService!!.start(this)
            } catch (e: IOException) {
                setErrorState(e.message)
            }
        }
    }

    private fun recognizeMicrophone() {
        if (model == null) {
            setErrorState(getString(R.string.model_not_ready))
            return
        }

        if (speechService != null) {
            setUiState(STATE_DONE)
            speechService!!.stop()
            speechService!!.shutdown()
            speechService = null
        } else {
            setUiState(STATE_MIC)
            try {
                val rec = Recognizer(model, 16000.0f)
                speechService = SpeechService(rec, 16000.0f)
                speechService!!.startListening(this)
                isListeningPaused.value = false // Start in listening mode
            } catch (e: IOException) {
                setErrorState(e.message)
            }
        }
    }

    private fun pause(isPaused: Boolean) {
        isListeningPaused.value = isPaused
        speechService?.setPause(isPaused)
    }

    override fun onDestroy() {
        super.onDestroy()
        speechService?.stop()
        speechService?.shutdown()
        speechStreamService?.stop()
        model?.close()
    }

    // Companion object and other functions (checkKeywordAndLaunch, etc.) remain the same
    companion object {
         const val STATE_START = 0
        const val STATE_READY = 1
        const val STATE_DONE = 2
        const val STATE_FILE = 3
        const val STATE_MIC = 4
        private const val PERMISSIONS_REQUEST_RECORD_AUDIO = 1
        private const val TAG = "VoskActivity"
    }

    // --- Keep your keyword and intent launching logic as is ---
    @Synchronized
    fun checkKeywordAndLaunch(text: String) {
        Log.d("******************", "text: '$text'")
        if (text.contains("آهنگ") || text.contains("موزیک")) {
            launchMusicPlayer()
            Log.d("*******************", "player starting")
        } else if (text.contains("اینترنت")) {
            Log.d("*********************", "internet starting")
            launchBrowser()
        } else if (text.contains("یه سوال") || text.contains("یک سوال")) {
            Log.d("*********************", "gemini starting")
        } else if (text.contains(VoskService.Companion.KEYWORD)) {
            val intent = Intent(this, VoskActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
    }

    // ... launchMusicPlayer, launchBrowser, launchSimpleChat methods ...
    // These remain unchanged.
    private fun launchMusicPlayer() {
        val assetFileName = "my_music.mp3"
        val tempFile = File(cacheDir, assetFileName)
        try {
            assets.open(assetFileName).use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: IOException) {
            Log.e(VoskService.TAG, "Error copying audio file: ${e.message}")
            return
        }

        val uri: Uri = FileProvider.getUriForFile(
            applicationContext,
            "org.vosk.demo.fileprovider",
            tempFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "audio/*")
            setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
            Log.i(VoskService.TAG, "launchMusicPlayer: Music player launched")
        } else {
            Log.e(VoskService.TAG, "launchMusicPlayer: No music player found")
        }
    }

    private fun launchBrowser() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setData("http://www.google.com".toUri())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            Log.e(VoskService.TAG, "No browser found")
        }
    }

}


// --- The Composable UI ---
@Composable
fun VoskScreen(
    uiState: Int,
    resultText: String,
    isListeningPaused: Boolean,
    onRecognizeFile: () -> Unit,
    onRecognizeMicrophone: () -> Unit,
    onPause: (Boolean) -> Unit
) {
    // Determine button states and texts based on the overall UI state
    val isReady = uiState == VoskActivity.STATE_READY || uiState == VoskActivity.STATE_DONE
    val isRecognizingFile = uiState == VoskActivity.STATE_FILE
    val isRecognizingMic = uiState == VoskActivity.STATE_MIC

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Result Text - equivalent to the first TextView
        Text(
            text = resultText,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // Takes up all available vertical space
                .verticalScroll(rememberScrollState()), // Makes it scrollable
            textAlign = TextAlign.Start
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Action Buttons
        Column {
            // Button Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Recognize File Button
                Button(
                    onClick = onRecognizeFile,
                    enabled = isReady || isRecognizingFile,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = if (isRecognizingFile) stringResource(R.string.stop_file) else stringResource(
                        R.string.recognize_file))
                }

                // Recognize Microphone Button
                Button(
                    onClick = onRecognizeMicrophone,
                    enabled = isReady || isRecognizingMic,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = if (isRecognizingMic) stringResource(R.string.stop_microphone) else stringResource(
                        R.string.recognize_microphone))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Pause/Listen Button (replaces ToggleButton)
            Button(
                onClick = { onPause(!isListeningPaused) }, // Toggles the state
                enabled = isRecognizingMic, // Only enabled when mic is active
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = if (isListeningPaused) "Listening" else "Paused")
            }
        }
    }
}