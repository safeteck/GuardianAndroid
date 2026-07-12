package com.sentinel.guardian.vosk

import android.Manifest // --- ADDED FOR FLASHLIGHT ---
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager // --- ADDED FOR FLASHLIGHT ---
import android.hardware.camera2.CameraAccessException // --- ADDED FOR FLASHLIGHT ---
import android.hardware.camera2.CameraManager // --- ADDED FOR FLASHLIGHT ---
import android.media.AudioAttributes // --- ADDED FOR FIND DEVICE ---
import android.media.AudioManager // --- ADDED FOR FIND DEVICE ---
import android.media.MediaPlayer // --- ADDED FOR FIND DEVICE ---
import android.media.RingtoneManager // --- ADDED FOR FIND DEVICE ---
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast // Keep or remove Toast as needed
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat // --- ADDED FOR FLASHLIGHT ---
import androidx.core.content.FileProvider
import com.sentinel.guardian.R
import com.sentinel.guardian.features.FeaturesServices
import com.sentinel.guardian.features.MessageTaskDetails
import com.sentinel.guardian.features.TaskDetails
import com.sentinel.guardian.ui.screens.ContactManager
import com.sentinel.guardian.ui.screens.features.AppFeatures
import com.sentinel.guardian.ui.screens.features.ContactsFeatures
import kotlinx.coroutines.runBlocking
import org.json.JSONException
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

class VoskService : Service(), RecognitionListener {
    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var isListening = false // Flag to prevent multiple starts

    // --- ADDED FOR FLASHLIGHT ---
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    private var isFlashlightOn = false // Track flashlight state

    // --- ADDED FOR FIND DEVICE ---
    private lateinit var audioManager: AudioManager
    private var mediaPlayer: MediaPlayer? = null
    private var originalAlarmVolume: Int = -1 // --- ADDED TO STORE ORIGINAL VOLUME ---

    override fun onCreate() {
        super.onCreate()
        Log.d("_____________________", "app started")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        // --- ADDED FOR FLASHLIGHT ---
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
            cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
            try {
                cameraId = cameraManager.cameraIdList[0] // Usually the rear camera is the first
            } catch (e: CameraAccessException) {
                Log.e(TAG, "Error accessing camera list: ${e.message}")
                cameraId = null // Indicate flash is unavailable
            } catch (e: Exception) { // Catch potential NPE or other issues
                Log.e(TAG, "Error initializing camera manager or getting camera ID: ${e.message}")
                cameraId = null
            }
        } else {
            Log.w(TAG, "Device does not have a camera flash.")
            cameraId = null
        }
        // --- END ADDED FOR FLASHLIGHT ---

        // --- ADDED FOR FIND DEVICE ---
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        // --- END ADDED FOR FIND DEVICE ---

        initModel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        Log.d(TAG, "onStartCommand: Received command: ${intent?.action}")

        when (intent?.action) {
            ACTION_START_LISTENING -> {
                if (model == null) {
                    initModel() // اگر مدل هنوز لود نشده، لود و سپس شروع کن
                } else {
                    startListening()
                }
            }

            ACTION_STOP_LISTENING -> {
                stopListening()
                stopSelf() // اگر می‌خواهید سرویس کلا بسته شود
            }
            // اگر سرویس بدون اکشن خاصی استارت شد (رفتار پیش‌فرض قدیمی)
            else -> {
                if (!isListening) initModel()
            }
        }

        return START_STICKY // Restart the service if it gets killed.
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // We don't provide binding.
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: Service destroyed")
        stopListening()
        // --- ADDED FOR FLASHLIGHT ---
        // Ensure flashlight is turned off when service stops
        if (isFlashlightOn) {
            // Check Build version for toggleFlashlight safety
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                toggleFlashlight(false)
            } else {
                Log.w(TAG, "Cannot toggle flashlight off on destroy - API < 23")
            }
        }
        // --- END ADDED FOR FLASHLIGHT ---

        // --- ADDED FOR FIND DEVICE ---
        stopAlarmSound() // Use the new method to ensure cleanup
        // --- END ADDED FOR FIND DEVICE ---
    }

    @Synchronized
    fun initModel() {
        if (model != null) {
            return
        }  // Already initialized

        StorageService.unpack(
            this, "model-small-fa-pe", "model",
            { model: Model? -> // Using lambda syntax for clarity
                this.model = model
                startListening()
            },
            { exception: IOException? -> // Using lambda syntax
                Log.e(TAG, "Failed to unpack the model: ${exception?.message ?: "Unknown error"}")
                stopSelf() // Stop the service if model loading fails
            })
    }


    @Synchronized
    private fun startListening() {
        if (model == null) {
            Log.w(TAG, "startListening called but model is null.")
            // Optionally try initModel again or stop service
            // initModel() // Be careful not to cause infinite loop if init always fails
            stopSelf()
            return
        }
        if (isListening) {
            Log.d(TAG, "startListening called but already listening.")
            return
        }

        try {
            // Check for RECORD_AUDIO permission before starting
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(TAG, "RECORD_AUDIO permission not granted. Cannot start listening.")
                Toast.makeText(this, R.string.mic_permission_missing, Toast.LENGTH_LONG)
                    .show() // Use string resource
                stopSelf() // Stop service if permission is missing
                return
            }

            val rec = Recognizer(model, 16000.0f)
            speechService = SpeechService(rec, 16000.0f)
            speechService!!.startListening(this)
            isListening = true
            Log.i(TAG, "startListening: Started listening successfully.")
        } catch (e: IOException) {
            Log.e(TAG, "startListening: IOException during Recognizer/SpeechService init.", e)
            stopSelf() // Stop if initialization fails
        } catch (e: SecurityException) {
            Log.e(
                TAG,
                "startListening: SecurityException - likely missing RECORD_AUDIO permission.",
                e
            )
            Toast.makeText(this, R.string.mic_permission_missing, Toast.LENGTH_LONG)
                .show() // Use string resource
            stopSelf()
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "startListening: UnsatisfiedLinkError - Vosk native library issue?", e)
            Toast.makeText(this, "Voice recognition library error", Toast.LENGTH_LONG).show()
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "startListening: Unexpected error", e)
            stopSelf()
        }
    }


    @Synchronized
    private fun stopListening() {
        if (speechService != null) {
            Log.d(TAG, "stopListening: Stopping speech service.")
            try {
                speechService?.stop() // Use safe call
                speechService?.shutdown()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping/shutting down speechService", e)
            } finally {
                speechService = null
                isListening = false
                Log.d(TAG, "stopListening: Speech service stopped and nulled.")
            }
        } else {
            Log.d(TAG, "stopListening: Called but speechService was already null.")
        }
        isListening = false // Ensure flag is always reset
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = "Vosk Foreground Service" // More descriptive name
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                channelName,
                NotificationManager.IMPORTANCE_HIGH // Use Importance High or DEFAULT depending on need
            ).apply {
                description = "Channel for Vosk keyword detection service" //Add description
                // Optionally disable sound/vibration if it's just for foreground state
                // setSound(null, null)
                // enableVibration(false)
            }
            // Register the channel with the system
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
                ?: Log.e(TAG, "createNotificationChannel: NotificationManager is null!")
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, VoskActivity::class.java) // Or your main activity
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent =
            PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)


        // Ensure string resources exist or replace with hardcoded strings for testing
        val contentTitle = try {
            getString(R.string.notification_title)
        } catch (e: Exception) {
            "Vosk Service"
        }
        val contentText = try {
            getString(R.string.notification_text)
        } catch (e: Exception) {
            "Listening for keywords..."
        }
        val icon = R.drawable.ic_launcher_foreground // Make sure this drawable exists

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setSmallIcon(icon)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // For pre-Oreo
            .setOngoing(true) // Makes the notification non-dismissible
            .build()
    }

    // --- RecognitionListener Methods ---

    override fun onResult(hypothesis: String) {
        Log.d(TAG, "onResult Raw: $hypothesis")
        checkKeywordAndLaunch(hypothesis)
    }

    override fun onFinalResult(hypothesis: String) {
        Log.d(TAG, "onFinalResult Raw: $hypothesis")
        checkKeywordAndLaunch(hypothesis)
    }

    override fun onPartialResult(hypothesis: String) {
//        Log.d(TAG, "onPartialResult Raw: $hypothesis")
        // Generally avoid triggering actions on partial results unless specifically needed
        // checkKeywordAndLaunch(hypothesis)
    }

    override fun onError(e: Exception) {
        Log.e(TAG, "RecognitionListener onError: " + e.message, e) // Log the full stack trace
        // Avoid immediate restart loops. Consider a backoff strategy or stopping.
        // Maybe stop and require manual restart? Or restart after a delay?
         stopListening() // Stop listening on error for now
        // Consider stopping the service if errors persist?
         stopSelf()
    }

    override fun onTimeout() {
        Log.w(TAG, "RecognitionListener onTimeout: Speech Recognition timed out.")
        // Restart listening after a timeout, but ensure clean state first.
        if (isListening) { // Check if we should be listening
            Log.d(TAG, "Timeout occurred while listening, attempting restart.")
            stopListening() // Ensure clean state before restart
            startListening()
        } else {
            Log.d(TAG, "Timeout occurred but service was not actively listening.")
        }
    }

    // --- Keyword Actions ---

    @Synchronized
    fun checkKeywordAndLaunch(rawHypothesis: String) {
        val text = try {
            val jsonObject = JSONObject(rawHypothesis)
            when {
                jsonObject.has("text") -> jsonObject.getString("text") // Final result likely uses "text"
                jsonObject.has("partial") -> jsonObject.getString("partial") // Partial result
                jsonObject.has("result") -> { // Handle pocketsphinx-style result array? { "result" : [ { "conf":1.0, "end":1.86, "start":1.44, "word":"..." } ], "text":"..." }
                    val resultArray = jsonObject.getJSONArray("result")
                    if (resultArray.length() > 0) {
                        // Reconstruct text from words, or just use the top-level "text" if available
                        jsonObject.optString("text", "") // Prefer top-level text if present
                    } else {
                        ""
                    }
                }

                else -> ""
            }
        } catch (e: JSONException) {
            Log.w(TAG, "Could not parse hypothesis JSON: $rawHypothesis. Treating as plain text.")
            rawHypothesis // Fallback: Treat the whole string as text if JSON parsing fails
        }.trim().lowercase() // Convert to lowercase for case-insensitive matching

        if (text.isEmpty()) {
            Log.d(TAG, "checkKeywordAndLaunch: Parsed text is empty.")
            return
        }

        Log.i("check keyword", "Detected text: \"$text\"") // Log the processed text

        // Check for stop alarm keywords FIRST if alarm might be playing
        when {
            text.contains("کمک") -> {//TODO -> improve
                //send custom message
                runBlocking {
                    val contacts = ContactManager(this@VoskService).loadContacts()
                    if (contacts.isEmpty())
                    //TODO -> should add contact and navigate to contacts screen
                        TAG
                    AppFeatures.sendSms(
                        this@VoskService,
                        contacts.first().phoneNumber,
                        "app features send message"
                    )
                    //send last known location
                    AppFeatures.getLastKnownLocation(this@VoskService) {
                        it
                        AppFeatures.sendLocationViaSms(
                            this@VoskService,
                            it,
                            contacts,
                            prefix = "my last location: ",
                            simId = null
                        )
                    }
                    //start custom task service
                    FeaturesServices.startTask(
                        this@VoskService,
                        "Optional",
                        contacts,
                        taskDetails = TaskDetails(
                            "optional",
                            messageTaskDetails = MessageTaskDetails(
                                contacts,
                                "hello message",
                                System.currentTimeMillis() + 10_000,
                                "message 2",
                                10.seconds.inWholeMilliseconds
                            )
                        )
                    )
                }
            }

            text.contains("سگ") -> {
                //TODO for Dog Danger (wild dog)
            }


            text.contains("کافیه") || text.contains("کافیه") || text.contains("پیدات کردم") || text.contains("کافی است") -> {
                Log.i(TAG, "Keyword detected: Stop Alarm")
                if (mediaPlayer != null && mediaPlayer!!.isPlaying) {
                    stopAlarmSound()
                    Log.i(TAG, "Alarm stopped by keyword.")
                    Toast.makeText(this, "Alarm stopped", Toast.LENGTH_SHORT).show()
                } else {
                    Log.d(TAG, "Stop alarm keyword detected, but alarm wasn't playing.")
                }
                // Decide if you want to 'return' here or allow other keywords to also match
                // return // Usually you process one command per utterance

            }

            text.contains("اینترنت") -> {
                Log.i(TAG, "Keyword detected: Internet")
                launchBrowser()
                // return

            }

            text.contains("یه سوال") || text.contains("یک سوال") -> {
                Log.i(TAG, "Keyword detected: Simple Chat")
                launchSimpleChat()
                // return

            }

            text.contains("چراغ") || text.contains("لامپ") || text.contains("نور") -> {
                Log.i(TAG, "Keyword detected: Flashlight")
                // Check Build version for safety
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    toggleFlashlight(!isFlashlightOn) // Toggle the current state
                } else {
                    Log.w(TAG, "Flashlight control requires Android M (API 23) or higher.")
                    Toast.makeText(
                        this,
                        "Flashlight control not supported on this device version",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                // return

            }

            text.contains("کجایی") || text.contains("کجا") -> {
                Log.i(TAG, "Keyword detected: Find Device")
                playMaxVolumeSound()
                // return

            }

            text.contains(KEYWORD.lowercase()) -> { // Compare with lowercase keyword
                Log.i(TAG, "Keyword detected: $KEYWORD")
                val intent = Intent(this, VoskActivity::class.java) // Or your main activity
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting VoskActivity", e)
                    Toast.makeText(this, "Error opening main app", Toast.LENGTH_SHORT).show()
                }
                // return
            }
        }
    }


    private fun launchMusicPlayer() {
        val assetFileName = "my_music.mp3" // Ensure this file exists in your assets folder
        val tempFile = File(cacheDir, assetFileName)

        try {
            if (!cacheDir.exists()) cacheDir.mkdirs()
            if (tempFile.exists()) tempFile.delete()

            assets.open(assetFileName).use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "Copied asset $assetFileName to ${tempFile.absolutePath}")

            if (!tempFile.exists() || tempFile.length() == 0L) {
                throw IOException("Failed to create or write to temporary file.")
            }

            val uri: Uri = FileProvider.getUriForFile(
                applicationContext,
                "${applicationContext.packageName}.fileprovider",
                tempFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "audio/*")
                setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }


            startActivity(intent)
            Log.i(TAG, "launchMusicPlayer: Music player launch intent sent with URI: $uri")

        } catch (e: IOException) {
            Log.e(TAG, "Error copying or accessing audio file '$assetFileName': ${e.message}", e)
            Toast.makeText(this, "Error preparing music file", Toast.LENGTH_SHORT).show()
        } catch (e: IllegalArgumentException) {
            Log.e(
                TAG,
                "Error getting FileProvider URI: ${e.message}. Check authority ('${applicationContext.packageName}.fileprovider') and filepaths.xml.",
                e
            )
            Toast.makeText(this, "File Provider configuration error", Toast.LENGTH_SHORT).show()
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "launchMusicPlayer: No activity found to handle audio VIEW intent.")
            Toast.makeText(this, "No music player app found", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Log.e(
                TAG,
                "launchMusicPlayer: Security exception trying to launch player. Check permissions or URI grant.",
                e
            )
            Toast.makeText(this, "Permission error launching player", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error launching music player", e)
            Toast.makeText(this, "Error launching music player", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchBrowser() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setData(Uri.parse("https://www.google.com"))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
            Log.i(TAG, "launchBrowser: Browser launch intent sent.")
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "No browser app found")
            Toast.makeText(this, "No browser app found", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error launching browser", e)
            Toast.makeText(this, "Error launching browser", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchSimpleChat() {
        val packageName = "com.example.simplechat" // Make sure this is correct
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                startActivity(launchIntent)
                Log.i(TAG, "launchSimpleChat: Launched $packageName")
            } catch (e: Exception) { // Catch broad Exception
                Log.e(TAG, "Error launching SimpleChat app ($packageName).", e)
                Toast.makeText(this, "Could not launch SimpleChat", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.e(TAG, "SimpleChat app ($packageName) not found")
            Toast.makeText(this, "SimpleChat app not installed", Toast.LENGTH_SHORT).show()
            // Optionally try to open play store listing
            try {
                val marketIntent =
                    Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
                marketIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(marketIntent)
            } catch (anfe: ActivityNotFoundException) {
                try {
                    val webIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                    )
                    webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(webIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "Could not open Play Store for $packageName", e)
                }
            }
        }
    }

    // --- ADDED FOR FLASHLIGHT ---
    @RequiresApi(Build.VERSION_CODES.M) // This function requires API 23+
    @Synchronized
    private fun toggleFlashlight(enable: Boolean) {
        if (cameraId == null) {
            Log.w(TAG, "toggleFlashlight: Camera ID is null, cannot control flash.")
            Toast.makeText(this, "Flashlight feature not available", Toast.LENGTH_SHORT).show()
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "toggleFlashlight: CAMERA permission not granted.")
            Toast.makeText(this, "Camera permission needed for flashlight", Toast.LENGTH_SHORT)
                .show()
            // Maybe send broadcast to Activity to request permission?
            return
        }

        try {
            cameraManager.setTorchMode(cameraId!!, enable)
            isFlashlightOn = enable
            Log.i(TAG, "Flashlight turned ${if (enable) "ON" else "OFF"}")
            // Toast.makeText(this, "Flashlight ${if (enable) "ON" else "OFF"}", Toast.LENGTH_SHORT).show() // Optional confirmation
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Error toggling flashlight (CameraAccessException): ${e.message}", e)
            Toast.makeText(this, "Error controlling flashlight", Toast.LENGTH_SHORT).show()
            isFlashlightOn = false // Reset state on error
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error toggling flashlight: ${e.message}", e)
            Toast.makeText(this, "Flashlight control error", Toast.LENGTH_SHORT).show()
            isFlashlightOn = false // Reset state on error
        }
    }
    // --- END ADDED FOR FLASHLIGHT ---

    // --- FIND DEVICE SOUND ---

    @Synchronized
    private fun playMaxVolumeSound() {
        // Stop any currently playing sound first (uses the new method)
        stopAlarmSound() // Ensure clean state before starting new sound

        val soundUri: Uri? = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE) // Fallback to ringtone
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) // Fallback to notification

        if (soundUri == null) {
            Log.e(
                TAG,
                "Cannot play sound, no default alarm, ringtone, or notification sound found."
            )
            Toast.makeText(this, "No sound found to play", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val streamType = AudioManager.STREAM_ALARM
            originalAlarmVolume = audioManager.getStreamVolume(streamType) // Store original volume
            val maxVolume = audioManager.getStreamMaxVolume(streamType)

            Log.d(
                TAG,
                "playMaxVolumeSound: Stored original volume: $originalAlarmVolume, Max volume: $maxVolume"
            )

            // Set volume to maximum
            audioManager.setStreamVolume(streamType, maxVolume, 0) // Flag 0 means no UI change

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setLegacyStreamType(streamType)
                        .build()
                )
                setDataSource(applicationContext, soundUri)
                isLooping = true // Loop the sound until stopped
                prepareAsync()
                setOnPreparedListener { mp ->
                    Log.i(TAG, "MediaPlayer prepared, starting playback at max volume (Looping).")
                    mp.start()
                    Toast.makeText(
                        applicationContext,
                        "Playing find device sound...",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                // Use the generic stopAlarmSound for completion/error cleanup
                setOnCompletionListener {
                    Log.w(TAG, "MediaPlayer playback completed unexpectedly (was looping).")
                    stopAlarmSound() // Cleanup even if loop stops somehow
                }
                setOnErrorListener { mp, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    Toast.makeText(applicationContext, "Error playing sound", Toast.LENGTH_SHORT)
                        .show()
                    stopAlarmSound() // Cleanup on error
                    true // Indicate error was handled
                }
            }

        } catch (e: Exception) { // Catch broader exceptions during setup
            Log.e(TAG, "Error setting up or playing sound: ${e.message}", e)
            Toast.makeText(this, "Error playing sound", Toast.LENGTH_SHORT).show()
            stopAlarmSound() // Ensure cleanup if setup fails
        }
    }

    // --- ADDED: Method to stop the alarm sound cleanly ---
    @Synchronized
    private fun stopAlarmSound() {
        if (mediaPlayer != null) {
            Log.d(TAG, "stopAlarmSound: Stopping media player.")
            if (mediaPlayer!!.isPlaying) {
                try {
                    mediaPlayer?.stop()
                } catch (e: IllegalStateException) {
                    Log.w(TAG, "MediaPlayer stop() called in invalid state.", e)
                }
            }
            try {
                mediaPlayer?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing mediaPlayer", e)
            }
            mediaPlayer = null
            Log.d(TAG, "stopAlarmSound: MediaPlayer stopped and released.")

            // Restore original volume only if it was successfully stored
            if (originalAlarmVolume != -1) {
                try {
                    val streamType = AudioManager.STREAM_ALARM
                    audioManager.setStreamVolume(streamType, originalAlarmVolume, 0)
                    Log.d(
                        TAG,
                        "stopAlarmSound: Restored volume for $streamType to $originalAlarmVolume"
                    )
                    originalAlarmVolume = -1 // Reset after restoring
                } catch (e: Exception) {
                    Log.e(TAG, "Error restoring original volume: ${e.message}", e)
                    // Volume might be stuck at max, maybe try setting to a default medium?
                }
            } else {
                Log.w(TAG, "stopAlarmSound: Original volume was not set (-1), cannot restore.")
            }
        } else {
            Log.d(TAG, "stopAlarmSound: Called but mediaPlayer was already null.")
            // Ensure volume is restored even if mediaPlayer is somehow null but volume wasn't reset
            if (originalAlarmVolume != -1) {
                Log.w(TAG, "stopAlarmSound: MediaPlayer null, but attempting volume restore.")
                try {
                    val streamType = AudioManager.STREAM_ALARM
                    audioManager.setStreamVolume(streamType, originalAlarmVolume, 0)
                    Log.d(
                        TAG,
                        "stopAlarmSound: Restored volume for $streamType to $originalAlarmVolume"
                    )
                    originalAlarmVolume = -1 // Reset after restoring
                } catch (e: Exception) {
                    Log.e(TAG, "Error restoring original volume: ${e.message}", e)
                }
            }
        }
    }
    // --- END ADDED ---

    companion object {
        // Use class name for TAG consistently
        internal val TAG: String = VoskService::class.java.simpleName
        private const val CHANNEL_ID = "VoskKeywordChannel"
        private const val NOTIFICATION_ID = 1 // Must be > 0
        internal const val KEYWORD = "روشن" // The primary keyword

        const val ACTION_START_LISTENING = "com.sentinel.guardian.action.START_LISTENING"
        const val ACTION_STOP_LISTENING = "com.sentinel.guardian.action.STOP_LISTENING"

    }
}