package com.sentinel.guardian.features

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.HandlerThread
import android.os.IBinder
import android.os.Parcelable
import android.telephony.SmsManager
import android.util.Log
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.sentinel.guardian.CarTripDetails
import com.sentinel.guardian.Contact
import com.sentinel.guardian.ContactCategory
import com.sentinel.guardian.R
import com.sentinel.guardian.ui.screens.ContactManager
import com.sentinel.guardian.ui.screens.features.CallFeatures
import com.sentinel.guardian.ui.screens.features.ServiceRegistry
import com.sentinel.guardian.vosk.VoskService
import com.sentinel.guardian.vosk.VoskActivity.Companion.STATE_DONE
import com.sentinel.guardian.vosk.VoskActivity.Companion.STATE_FILE
import com.sentinel.guardian.vosk.VoskActivity.Companion.STATE_MIC
import com.sentinel.guardian.vosk.VoskActivity.Companion.STATE_READY
import com.sentinel.guardian.vosk.VoskActivity.Companion.STATE_START
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.SpeechService
import org.vosk.android.SpeechStreamService
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.seconds

class FeaturesServices : Service(), SensorEventListener {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    // --- Service components ---
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var mediaRecorder: MediaRecorder? = null

    // --- State Management ---
    private var isSendingLiveLocation = false
    private var currentAudioFile: File? = null // FIX: Replaced reflection with a direct reference

    // ENHANCEMENT: Dedicated thread for reliable background location updates
    private var locationHandlerThread: HandlerThread? = null

    fun stopSensors() {
        sensorManager.unregisterListener(this)
    }

    companion object {
        const val TAG = "FeaturesService"
        const val ACTION_START_SERVICE = "ACTION_START_SERVICE"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
        const val ACTION_EXECUTE_TASK = "ACTION_EXECUTE_TASK"
        const val EXTRA_TASK_NAME = "EXTRA_TASK_NAME"
        const val EXTRA_CONTACTS = "EXTRA_CONTACTS"
        const val EXTRA_SIM_ID = "EXTRA_SIM_ID"
        const val EXTRA_TASK_DETAILS = "EXTRA_TASK_DETAILS"

        // ENHANCEMENT: Public flag for the UI to check service status
        @Volatile
        var isServiceRunning = false

        var isGyroscopeRunning = false
        var isAccelerometerRunning = false
        var isLocationRunning = false
        var isMicrophoneRunning = false
        var isCameraRunning = false
        var isPlayingSound = false
        @Volatile
        var isOfflineAssistantRunning = false
        private var model: Model? = null
        private var speechService: SpeechService? = null
        private var speechStreamService: SpeechStreamService? = null
        private val uiState = mutableIntStateOf(STATE_START)
        private val resultText = mutableStateOf("")
        private val isListeningPaused = mutableStateOf(true) // Replaces ToggleButton state
        private val voskService = VoskService()

        // --- Public methods to control the service ---
        fun startService(context: Context) {
            val startIntent = Intent(context, FeaturesServices::class.java).apply {
                action = ACTION_START_SERVICE
            }
            ContextCompat.startForegroundService(context, startIntent)
        }

        fun stopService(context: Context) {
            val stopIntent = Intent(context, FeaturesServices::class.java).apply {
                action = ACTION_STOP_SERVICE
            }

            // Use startForegroundService for stop actions as well, the service will handle the action
            ContextCompat.startForegroundService(context, stopIntent)
        }

        fun startTask(
            context: Context,
            taskName: String,
            contacts: List<Contact>? = null,
            simId: Int? = null,
            taskDetails: TaskDetails? = null,
            carTripDetails: CarTripDetails? = null
        ) {
            val intent = Intent(context, FeaturesServices::class.java).apply {
                action = ACTION_EXECUTE_TASK
                putExtra(EXTRA_TASK_NAME, taskName)
                val phoneNumbers = contacts?.map { it.phoneNumber }?.toTypedArray()
                putExtra(EXTRA_CONTACTS, phoneNumbers)
                putExtra(EXTRA_SIM_ID, simId)
                putExtra(EXTRA_TASK_DETAILS, taskDetails)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun enableReceiveSmsReceiverManifestService(context: Context) {
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context, SmsReceiver::class.java),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        }

        fun disableReceiveSmsReceiverManifestService(context: Context) {
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context, SmsReceiver::class.java),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        NotificationHelper.createNotificationChannel(this)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // ENHANCEMENT: Start the handler thread for the location looper
        locationHandlerThread = HandlerThread("LocationProviderThread")
        locationHandlerThread?.start()

//        startSensorListeners()
        Log.d(TAG, "FeaturesService created and running.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SERVICE -> {
                Log.d(TAG, "Service started or re-started.")
                val notification =
                    NotificationHelper.createNotification(this, "Monitoring for events...")
                startForeground(NotificationHelper.NOTIFICATION_ID, notification)
            }

            ACTION_STOP_SERVICE -> {
                Log.d(TAG, "Stopping service via action.")
                stopSelf() // This will trigger onDestroy for cleanup
                return START_NOT_STICKY // Don't restart after it's been explicitly stopped
            }

            ACTION_EXECUTE_TASK -> {
                // Ensure service is in foreground mode before executing a task
                val notification =
                    NotificationHelper.createNotification(this, "Executing a task...")
                startForeground(NotificationHelper.NOTIFICATION_ID, notification)

                val taskName = intent.getStringExtra(EXTRA_TASK_NAME)
                val phoneNumbers = intent.getStringArrayExtra(EXTRA_CONTACTS)
                val simId = intent.getIntExtra(EXTRA_SIM_ID, -1).let { if (it == -1) null else it }
                val contacts = phoneNumbers?.map { Contact("", "", it) } ?: emptyList()

                val taskDetails: TaskDetails? =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(EXTRA_TASK_DETAILS, TaskDetails::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(EXTRA_TASK_DETAILS)
                    }

                taskName?.let {
                    Log.d(TAG, "Executing task: $it on ${contacts.size} contacts")
                    serviceScope.launch { startTaskInternal(it, contacts, simId, taskDetails) }
                }
            }
        }
        return START_STICKY
    }

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

    private fun startTaskInternal(
        taskName: String,
        contacts: List<Contact>,
        simId: Int?,
        taskDetails: TaskDetails? = null,
        carTripDetails: CarTripDetails? = null
    ) {
        Log.d(TAG, "Executing internal task: $taskName")

        when (taskName) {
            "OFFLINE_ASSISTANT" -> {
                if (taskDetails?.startTask == true) {
                    isOfflineAssistantRunning = true
                    updateNotification("Offline assistant is active...")
                    Log.d(TAG, "Starting VoskService for offline assistant.")

                    // Create an Intent to command VoskService to start.
                    val intent = Intent(this, VoskService::class.java).apply {
                        action = VoskService.ACTION_START_LISTENING
                    }
                    // Use startService. VoskService will manage its own foreground state.
                    startService(intent)
                } else {
                    if (isOfflineAssistantRunning) {
                        isOfflineAssistantRunning = false
                        resetNotificationToDefault()
                        Log.d(TAG, "Stopping VoskService for offline assistant.")

                        // Create an Intent to command VoskService to stop.
                        val intent = Intent(this, VoskService::class.java).apply {
                            action = VoskService.ACTION_STOP_LISTENING
                        }
                        startService(intent)
                    }
                }
            }

            "SEND_LIVE_LOCATION" -> {
                if (isSendingLiveLocation) {
                    sensorManager.unregisterListener(this)
                    Log.d(TAG, "Live location is already being sent. Ignoring request.")
                    return
                }
                isSendingLiveLocation = true
                isLocationRunning = true
                updateNotification("Sharing live location for 30 min...")

                startLiveLocationUpdates(
                    taskDetails?.locationTask?.interval?.toLong() ?: 60.seconds.inWholeMilliseconds
                ) { location ->
                    Log.d("_____", "${location.latitude} lt and ${location.longitude} lg")

//                    serviceScope.launch { playSound(this@FeaturesService, R.raw.sms_sound_1) }

                    sendLocationViaSms(location, contacts, "LIVE Location:", simId)
                }
                serviceScope.launch {
                    delay(taskDetails?.locationTask?.duration ?: (30 * 60 * 1000))
                    if (isSendingLiveLocation) {
                        Log.d(TAG, "Live location sending task finished after 5 mins.")
                        stopService(this@FeaturesServices.baseContext)
                        stopLiveLocationTask()
                    }
                }
            }

            "SEND_LAST_SAVED_LOCATION" -> {
                updateNotification("Sending last location...")
                getLastKnownLocation { location ->
                    Log.d(
                        "_____",
                        "Last Location ${location?.latitude} lt and ${location?.longitude} lg"
                    )
                    sendLocationViaSms(location, contacts, "Last Saved Location:", simId)
//                    stopLiveLocationTask()
//                    stopLiveLocationUpdates()
                    isLocationRunning = true
                    serviceScope.launch {
                        delay(3.seconds)
                        isLocationRunning = false
                        stopService(this@FeaturesServices)
                    }
                }
                resetNotificationToDefault()
            }

            "RECORD_AUDIO_30S_SEND" -> {
                updateNotification("Recording 30s of audio...")
                startAudioRecording(30000) { audioFile ->
                    val message = if (audioFile != null) {
                        // NOTE: Sending files via SMS is not possible.
                        // This message informs the user that an audio file was created on the device.
                        "Recorded audio (30s). File: ${audioFile.name}."
                    } else {
                        "Audio recording failed."
                    }
                    sendSmsToMultiple(contacts, message, simId)
                    startAudioRecording(10000) {}
                    startLiveLocationUpdates(60000) { location ->
                        sendLocationViaSms(location, contacts, "Trip Update:", simId)
                    }

                    resetNotificationToDefault()
                }
            }

            "CAR_TRIP_START_ALERT" -> {
                updateNotification("Car trip mode activated...")
                val message =
                    "Starting a trip. " + "Driver: ${carTripDetails?.vehicleFirstName.orEmpty()} ${carTripDetails?.vehicleLastName.orEmpty()}. " + "Car: ${carTripDetails?.carModel.orEmpty()} (Plate: ${carTripDetails?.licensePlate.orEmpty()}). " + "Route: ${carTripDetails?.tripOrigin.orEmpty()} to ${carTripDetails?.tripDestination.orEmpty()}."

                Log.d(com.sentinel.guardian.TAG, "Car trip alert message: $message")

                if (taskDetails?.messageTaskDetails != null) {
                    val messageText =
                        if (carTripDetails != null) "Starting a trip. Will send location updates."
                        else message
                    sendSmsToMultiple(
                        taskDetails.messageTaskDetails.contacts ?: contacts,
                        taskDetails.messageTaskDetails.messageContent ?: messageText,
                        simId
                    )
                }

                if (taskDetails?.recordAudio != null) {
                    startAudioRecording(taskDetails.recordAudio.duration ?: 10000L) {}
                }

                if (taskDetails?.locationTask != null) {

                    if (isSendingLiveLocation) {
                        Log.d(TAG, "Stopping previous live location task to start trip monitoring.")
                        stopLiveLocationTask()
                    }

                    isSendingLiveLocation = true
                    serviceScope.launch {
                        startLiveLocationUpdates(60000) { location -> // Update every minute
                            sendLocationViaSms(location, contacts, "Trip Update:", simId)
                        }
                    }
                }
            }

            "LOW_BATTERY_ALERT" -> {
                var emergencyContacts: List<Contact>
                this.serviceScope.launch {
                    emergencyContacts = ContactManager(this@FeaturesServices).loadContacts()

                    sendSmsToMultiple(
                        emergencyContacts, "Warning: My phone battery is very low.", simId
                    )
                    getLastKnownLocation { location ->
                        sendLocationViaSms(
                            location,
                            emergencyContacts,
                            "My last known location (low battery):",
                            simId
                        )
                    }

                }
            }

            "FALL_DETECTED_ALERT" -> {
//                var emergencyContacts: List<Contact>

                this.serviceScope.launch {
                    if (taskDetails != null) {
//                        emergencyContacts = ContactManager(this@FeaturesService).loadContacts()
                        val emergencyContacts = taskDetails.messageTaskDetails?.contacts
                            ?: ContactManager(this@FeaturesServices).loadContacts()

                        if (taskDetails.messageTaskDetails != null) {
                            sendSmsToMultiple(emergencyContacts, "Potential fall detected!", simId)
                        }

                        if (taskDetails.locationTask != null) {
                            getLastKnownLocation { location ->
                                sendLocationViaSms(
                                    location,
                                    emergencyContacts,
                                    "My location (fall detected):",
                                    simId
                                )
                            }
                        }

                        if (taskDetails.callTaskDetails != null) {
//                            taskDetails.callTaskDetails.contacts.forEach { contact ->
                            emergencyContacts.first().let {
//                                CallFeatures.call(this@FeaturesService, it.phoneNumber)
                            }
                        }

                        ServiceRegistry.registerSensorListeners(
                            this@FeaturesServices,
                            this@FeaturesServices
                        )

                        // Also record audio after a fall
                        if (taskDetails.recordAudio != null) {
                            startAudioRecording(taskDetails.recordAudio.duration) {}
                        }
                    }

                }
            }

            else -> {
                Log.w(TAG, "Unknown or unhandled task in service: $taskName")
                resetNotificationToDefault() // Reset notification after any short task
            }
        }
    }

    // --- SENSOR LOGIC ---
    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val acceleration = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
//                Log.d(TAG, "acceleration detected : ${acceleration}g")
                if (acceleration > 3.5) { // Threshold for potential fall
                    Log.d(TAG, "High acceleration detected (potential fall): ${acceleration}g")

                    serviceScope.launch {
                        CallFeatures.call(
                            this@FeaturesServices,
                            ContactManager(this@FeaturesServices).loadContacts()
                                .first { it.category == ContactCategory.EMERGENCY }.phoneNumber
                        )
                    }
//                    startTaskInternal(
//                        "FALL_DETECTED_ALERT",
//                        getEmergencyContactsFromPrefs(),
//                        null
//                    )
                    // Debounce to prevent multiple triggers
                    stopSensorListeners()
                    //                serviceScope.launch { delay(10000); startSensorListeners() }
                }
            }

            Sensor.TYPE_GYROSCOPE -> {
                val values = event.values

                values?.let { v ->
                    if (v.size >= 3) {
                        val x = v[0]
                        val y = v[1]
                        val z = v[2]

                        val sqrt = x * x + y * y + z * z
                        val angularSpeed = sqrt(sqrt)
                        if (angularSpeed > 10.0) {
                            Log.d(
                                com.sentinel.guardian.TAG,
                                "Vigorous motion detected (Gyroscope): $angularSpeed"
                            )
                            sendSmsToMultiple(
                                getEmergencyContactsFromPrefs(),
                                "Vigorous motion detected",
                                null
                            )
                        } else if (angularSpeed < 0.5) {
                            Log.d(
                                com.sentinel.guardian.TAG,
                                "Lower than 0.5 (Gyroscope): $angularSpeed"
                            )
                        }
                    } else {
                        Log.e(
                            com.sentinel.guardian.TAG,
                            "Gyroscope event.values has less than 3 elements."
                        )
                    }
                } ?: run {
                    Log.e(com.sentinel.guardian.TAG, "Gyroscope event.values is null.")
                }

            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun startSensorListeners() {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
        ) { // No separate permission needed before Q
            accelerometer?.also { sensor ->
                sensorManager.registerListener(
                    this, sensor, SensorManager.SENSOR_DELAY_NORMAL
                )
            }
        }
    }

    private fun stopSensorListeners() {
        sensorManager.unregisterListener(this)
        Log.d("EMERGENCY_SERVICE", "STOP SENSOR LISTENER")
    }

    // --- LOCATION LOGIC ---
    @SuppressLint("MissingPermission")
    private fun getLastKnownLocation(onLocationFetched: (Location?) -> Unit) {
        if (!areLocationPermissionsGranted()) {
            onLocationFetched(null); return
        }
        mFusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location == null) {
                startLiveLocationUpdates(10_000L, { location ->
                    onLocationFetched(location)
                    stopLiveLocationUpdates()
                    stopLiveLocationTask()
                })
            } else
                onLocationFetched(location)
            stopLiveLocationUpdates()
            stopLiveLocationTask()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLiveLocationUpdates(
        intervalMillis: Long, onLocationUpdate: (Location) -> Unit
    ) {
        serviceScope.launch {
            soundPlaying(R.raw.location_finding_sound)
        }

        if (!areLocationPermissionsGranted()) {
            Log.w(TAG, "Location permissions not granted.")
            stopLiveLocationTask()
            return
        }
        // FIX: Use the looper from our dedicated background thread for reliability
        val looper = locationHandlerThread?.looper ?: run {
            Log.e(TAG, "Location HandlerThread not available!")
            stopLiveLocationTask()
            return
        }

        val locationRequest =
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis).build()
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let(onLocationUpdate)
            }
        }
        mFusedLocationClient.requestLocationUpdates(locationRequest, locationCallback!!, looper)
        Log.d(TAG, "Live location updates started on background thread.")
    }

    private fun stopLiveLocationUpdates() {
        locationCallback?.let {
            mFusedLocationClient.removeLocationUpdates(it)
            locationCallback = null
            Log.d(TAG, "Live location updates stopped.")
        }
    }

    // ENHANCEMENT: A single method to gracefully stop the live location task and reset state
    private fun stopLiveLocationTask() {
        if (!isSendingLiveLocation) return
        isSendingLiveLocation = false
        isLocationRunning = false
        stopLiveLocationUpdates()
        resetNotificationToDefault()
        Log.d(TAG, "Live location task has been fully stopped.")
    }

    // --- MEDIA LOGIC ---
    @SuppressLint("MissingPermission")
    private fun startAudioRecording(
        durationMillis: Long? = null,
        onFinished: (File?) -> Unit = {}
    ) {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            onFinished(null); return
        }
        if (mediaRecorder != null) {
            Log.w(TAG, "Audio recording was already in progress. Stopping it first.")
            stopAudioRecording { /* do nothing with the old file */ }
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        // FIX: Store the file in a member variable so it can be accessed in stopAudioRecording
        currentAudioFile =
            File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "AUDIO_$timestamp.mp3")

        mediaRecorder =
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else MediaRecorder()).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(currentAudioFile!!.absolutePath)
                try {
                    prepare()
                    start()
                    Log.d(TAG, "Audio recording started: ${currentAudioFile!!.absolutePath}")
                    durationMillis?.let {
                        serviceScope.launch {
                            delay(it)
                            stopAudioRecording(onFinished)
                        }
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "MediaRecorder prepare() failed", e)
                    currentAudioFile = null
                    onFinished(null)
                }
            }
    }

    private fun stopAudioRecording(onFinished: (File?) -> Unit = {}) {
        mediaRecorder?.let {
            try {
                it.stop()
                it.release()
                Log.d(TAG, "MediaRecorder stopped successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping media recorder", e)
                currentAudioFile?.delete() // Clean up potentially corrupt file
                currentAudioFile = null
            }
        }
        mediaRecorder = null
        // FIX: Return the file from the member variable, then clear the reference
        onFinished(currentAudioFile)
        currentAudioFile = null
    }

    // --- SMS LOGIC ---
    private fun sendSmsToMultiple(contacts: List<Contact>, message: String, simId: Int?) {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        contacts.forEach { contact -> sendSms(contact.phoneNumber, message, simId) }
    }

    private fun sendSms(phoneNumber: String, message: String, simSubscriptionId: Int?) {
        serviceScope.launch {
            soundPlaying(R.raw.sms_sound_1)
        }
        try {
            val smsManager: SmsManager =
                if (simSubscriptionId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    SmsManager.getSmsManagerForSubscriptionId(simSubscriptionId)
                } else {
                    SmsManager.getDefault()
                }
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            Log.d(TAG, "SMS to $phoneNumber initiated using SIM ID: $simSubscriptionId")
        } catch (e: Exception) {
            Log.e(TAG, "SMS failed", e)
        }
    }

    private fun sendLocationViaSms(
        location: Location?, toContacts: List<Contact>, prefix: String, simId: Int?
    ) {
        serviceScope.launch {
            soundPlaying(R.raw.sms_sound_1)
        }
        val message = if (location == null) {
            "$prefix Location not available."
        } else {
            "$prefix https://maps.google.com/?q=${location.latitude},${location.longitude}"
        }
        sendSmsToMultiple(toContacts, message, simId)
    }

    // --- HELPERS ---
    private fun areLocationPermissionsGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun getEmergencyContactsFromPrefs(): List<Contact> {
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        val phoneNumbers = prefs.getStringSet("emergency_contacts", emptySet()) ?: emptySet()
        return phoneNumbers.map { Contact("", "", it) }
    }

    // --- NOTIFICATION HELPERS ---
    private fun updateNotification(text: String) {
        val notification = NotificationHelper.createNotification(this, text)
        startForeground(NotificationHelper.NOTIFICATION_ID, notification)
    }

    private fun resetNotificationToDefault() {
        if (!isSendingLiveLocation) { // Don't reset if the main long-running task is still active
            updateNotification("Monitoring for events...")
        }
    }

    private suspend fun soundPlaying(soundResources: Int) {
        if (!isPlayingSound) {
            isPlayingSound = true
            playSound(this@FeaturesServices, soundResources)
            delay(2.seconds)
            isPlayingSound = false
        } else {
            delay(2.seconds)
            soundPlaying(soundResources)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        isPlayingSound = false
        stopForeground(true) // Remove the notification
        stopSensorListeners()
        stopLiveLocationTask() // Gracefully stop location updates
        stopAudioRecording() // Clean up any running recording
        serviceJob.cancel()
        locationHandlerThread?.quitSafely() // Clean up the handler thread
        Log.d(TAG, "FeaturesService destroyed.")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

@Parcelize
data class TaskDetails(
    val name: String,
    val startTask: Boolean? = null,
    val note: String? = null,
    val interval: Int? = null,
    val delay: Long? = null,
    val repeatTime: Int? = null,
    val duration: Long? = null,
    val startTime: Long? = null,
    val endTime: Long? = null,
    val batteryChargePercent: Int? = null,
    val isRunning: Boolean = false,
    val recordAudio: AudioRecordTask? = null,
    val cameraTask: CameraTaskDetails? = null,
    val locationTask: LocationTaskDetails? = null,
    val sensors: List<SensorTaskDetails>? = null,
    val messageTaskDetails: MessageTaskDetails? = null,
    val callTaskDetails: CallTaskDetails? = null,
//    val taskDetails: TaskDetails? = null
) : Parcelable

@Parcelize
data class CallTaskDetails(
    val contacts: List<Contact>? = null,
    val startTime: Long? = null,
) : Parcelable

@Parcelize
data class MessageTaskDetails(
    val contacts: List<Contact>? = null,
    val messageContent: String? = null,
    val sendTime: Long? = null,
    val secondMessage: String? = null,
    val spaceTimeSendDuration: Long? = null
) : Parcelable

enum class SensorType {
    ACCELEROMETER,
    GYROSCOPE
}

@Parcelize
data class SensorTaskDetails(
    val sensorType: SensorType,
    val turnOn: Boolean,
) : Parcelable

@Parcelize
class CameraTaskDetails(
    val cameraType: String,
    val startTime: Long?,
    val duration: Long?,
) : Parcelable

@Parcelize
class AudioRecordTask(
    val startTime: Long?,
    val duration: Long?
) : Parcelable

enum class LocationTypeTracking {
    LIVE,
    ONCE,
    LAST_SAVED_TIME
}

@Parcelize
data class LocationTaskDetails(
    val typeTracking: LocationTypeTracking,
    val interval: Int? = null,
    val duration: Long? = null
) : Parcelable