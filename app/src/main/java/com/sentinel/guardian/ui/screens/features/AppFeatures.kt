package com.sentinel.guardian.ui.screens.features

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.Looper
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.sentinel.guardian.R
import com.sentinel.guardian.ACTION_SMS_DELIVERED
import com.sentinel.guardian.ACTION_SMS_SENT
//import com.sentinel.guardian.features.CameraManager
import com.sentinel.guardian.CarTripDetails
import com.sentinel.guardian.Contact
import com.sentinel.guardian.features.playSound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.minutes


/**
 * A singleton object to hold all core application features related to
 * location, contacts, messaging, and media recording.
 * This centralizes the business logic of the app.
 */
object AppFeatures {
    private const val TAG = "AppFeatures"

    // Properties to manage state within the feature set
    private var mFusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var liveLocationJob: Job? = null
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null

    /**
     * Executes a specific task based on its name. This is the central hub for triggering actions.
     * Note: Camera actions require a CameraManager instance to be passed in.
     */
    @SuppressLint("MissingPermission")
    fun executeTask(
        context: Context,
        taskName: String,
        associatedContacts: List<Contact>,
        selectedSimSubscriptionId: Int?,
        carTripDetails: CarTripDetails? = null,
//        cameraManager: CameraManager? = null // CameraManager is now a parameter
    ) {
        Log.d(TAG, "Executing task: $taskName")
        when (taskName) {
            "SEND_LIVE_LOCATION_005" -> {
                liveLocationJob?.cancel()
                liveLocationJob = CoroutineScope(Dispatchers.Main).launch {
                    startLiveLocationUpdates(context, 15000) { location ->
                        sendLocationViaSms(
                            context,
                            location,
                            associatedContacts,
                            selectedSimSubscriptionId,
                            "LIVE Location:"
                        )
                    }
                    delay(5.minutes)
                    stopLiveLocationUpdates()
                    Log.d(TAG, "Live location sending task finished after 5 mins.")
                }
            }

            "SEND_LIVE_LOCATION_060" -> {
                liveLocationJob?.cancel()
                liveLocationJob = CoroutineScope(Dispatchers.Main).launch {
                    startLiveLocationUpdates(context, 3600000) { location ->
                        sendLocationViaSms(
                            context,
                            location,
                            associatedContacts,
                            selectedSimSubscriptionId,
                            "LIVE Location:"
                        )
                    }
                    delay(60.minutes)
                    stopLiveLocationUpdates()
                    Log.d(TAG, "Live location sending task finished after 60 mins.")
                }
            }

            "SEND_LAST_SAVED_LOCATION" -> {
                val lastLocation = getSavedLastLocation(context)
                if (lastLocation != null) {
                    sendLocationViaSms(
                        context,
                        lastLocation,
                        associatedContacts,
                        selectedSimSubscriptionId,
                        "Last Saved Location (at ${
                            SimpleDateFormat(
                                "HH:mm dd/MM",
                                Locale.getDefault()
                            ).format(Date(lastLocation.time))
                        }):"
                    )
                } else {
                    sendSmsToMultiple(
                        context,
                        associatedContacts,
                        "Last saved location not available.",
                        selectedSimSubscriptionId
                    )
                }
            }

            "RECORD_AUDIO_30S_SEND" -> {
                startAudioRecording(context, 30000) { audioFile ->
                    if (audioFile != null) {
                        sendSmsToMultiple(
                            context,
                            associatedContacts,
                            "Recorded audio (30s). File: ${audioFile.name}.",
                            selectedSimSubscriptionId
                        )
                    } else {
                        sendSmsToMultiple(
                            context,
                            associatedContacts,
                            "Audio recording failed.",
                            selectedSimSubscriptionId
                        )
                    }
                }
            }

/*
            "TAKE_PHOTO_REAR_SEND" -> {
                cameraManager?.takePicture(isFront = false) { uri ->
                    if (uri != null) {
                        sendSmsToMultiple(
                            context,
                            associatedContacts,
                            "Rear photo taken. URI: $uri.",
                            selectedSimSubscriptionId
                        )
                    } else {
                        sendSmsToMultiple(
                            context,
                            associatedContacts,
                            "Rear photo capture failed.",
                            selectedSimSubscriptionId
                        )
                    }
                } ?: Log.e(TAG, "CameraManager not provided for TAKE_PHOTO_REAR_SEND")
            }
*/

/*
            "TAKE_PHOTO_FRONT_SEND" -> {
                cameraManager?.takePicture(isFront = true) { uri ->
                    if (uri != null) {
                        sendSmsToMultiple(
                            context,
                            associatedContacts,
                            "Front photo taken. URI: $uri.",
                            selectedSimSubscriptionId
                        )
                    } else {
                        sendSmsToMultiple(
                            context,
                            associatedContacts,
                            "Front photo capture failed.",
                            selectedSimSubscriptionId
                        )
                    }
                } ?: Log.e(TAG, "CameraManager not provided for TAKE_PHOTO_FRONT_SEND")
            }
*/

            "LOW_BATTERY_ALERT" -> {
                sendSmsToMultiple(
                    context,
                    associatedContacts,
                    "Warning: My phone battery is very low.",
                    selectedSimSubscriptionId
                )
                getLastKnownLocation(context) { location ->
                    sendLocationViaSms(
                        context,
                        location,
                        associatedContacts,
                        selectedSimSubscriptionId,
                        "My last known location (low battery):"
                    )
                }
            }

            "CAR_TRIP_START_ALERT" -> {
                val message = "Starting a trip. " +
                        "Driver: ${carTripDetails?.vehicleFirstName.orEmpty()} ${carTripDetails?.vehicleLastName.orEmpty()}. " +
                        "Car: ${carTripDetails?.carModel.orEmpty()} (Plate: ${carTripDetails?.licensePlate.orEmpty()}). " +
                        "Route: ${carTripDetails?.tripOrigin.orEmpty()} to ${carTripDetails?.tripDestination.orEmpty()}."
                Log.d(TAG, "Car trip alert message: $message")
                sendSmsToMultiple(context, associatedContacts, message, selectedSimSubscriptionId)
                startAudioRecording(context, 10000) {}
                startLiveLocationUpdates(context, 60000) { location ->
                    sendLocationViaSms(
                        context,
                        location,
                        associatedContacts,
                        selectedSimSubscriptionId,
                        "Trip Update:"
                    )
                }
            }

            "FALL_DETECTED_ALERT" -> {
                sendSmsToMultiple(
                    context,
                    associatedContacts,
                    "Potential fall detected!",
                    selectedSimSubscriptionId
                )
                getLastKnownLocation(context) { location ->
                    sendLocationViaSms(
                        context,
                        location,
                        associatedContacts,
                        selectedSimSubscriptionId,
                        "My location (fall detected):"
                    )
                }
                startAudioRecording(context, 30000) {}
            }

            else -> Log.w(TAG, "Unknown task: $taskName")
        }
    }

    // --- Location Features ---

    @SuppressLint("MissingPermission")
    fun getLastKnownLocation(context: Context, onLocationFetched: (Location?) -> Unit) {
        if (!areLocationPermissionsGranted(context)) {
            Log.w(TAG, "Location permissions not granted.")
            onLocationFetched(null)
            return
        }
        if (mFusedLocationClient == null) mFusedLocationClient =
            com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)

        mFusedLocationClient?.lastLocation?.addOnSuccessListener { location: Location? ->
            onLocationFetched(location)
            location?.let { saveLastLocation(context, it) }
        }?.addOnFailureListener {
            Log.e(TAG, "Failed to get last known location", it)
            onLocationFetched(null)
        }
    }

    @SuppressLint("MissingPermission")
    fun startLiveLocationUpdates(
        context: Context,
        intervalMillis: Long,
        onLocationUpdate: (Location) -> Unit
    ) {
        if (!areLocationPermissionsGranted(context)) {
            Log.w(TAG, "Location permissions not granted.")
            return
        }
        if (mFusedLocationClient == null) mFusedLocationClient =
            com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)

        val locationRequest =
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(intervalMillis / 2)
                .build()
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let {
                    Log.d(TAG, "Live Location: Lat: ${it.latitude}, Lon: ${it.longitude}")
                    onLocationUpdate(it)
                    saveLastLocation(context, it)
                }
            }
        }
        mFusedLocationClient?.requestLocationUpdates(
            locationRequest,
            locationCallback!!,
            Looper.getMainLooper()
        )
        Log.d(TAG, "Live location updates started.")
    }

    fun stopLiveLocationUpdates() {
        locationCallback?.let {
            mFusedLocationClient?.removeLocationUpdates(it)
            Log.d(TAG, "Live location updates stopped.")
        }
        liveLocationJob?.cancel()
        locationCallback = null
    }

    private fun saveLastLocation(context: Context, location: Location) {
        val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        prefs.edit {
            putString("last_known_lat", location.latitude.toString())
            putString("last_known_lon", location.longitude.toString())
            putLong("last_known_loc_time", System.currentTimeMillis())
            apply()
        }
        Log.d(TAG, "Saved last location: Lat ${location.latitude}, Lon ${location.longitude}")
    }

    private fun getSavedLastLocation(context: Context): Location? {
        val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val latString = prefs.getString("last_known_lat", null)
        val lonString = prefs.getString("last_known_lon", null)
        if (latString != null && lonString != null) {
            val location = Location("SavedProvider").apply {
                latitude = latString.toDouble()
                longitude = lonString.toDouble()
                time = prefs.getLong("last_known_loc_time", 0)
            }
            return location
        }
        return null
    }

    private fun areLocationPermissionsGranted(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    // --- SMS & Contact Features ---

    fun sendSms(
        context: Context,
        phoneNumber: String,
        message: String,
        simSubscriptionId: Int? = null
    ) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(context, "SMS permission not granted", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val smsManager: SmsManager =
                if (simSubscriptionId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.getSystemService(SmsManager::class.java)
                            .createForSubscriptionId(simSubscriptionId)
                    } else {
                        SmsManager.getSmsManagerForSubscriptionId(simSubscriptionId)
                    }
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.getSystemService(SmsManager::class.java) as SmsManager
                    } else {
                        SmsManager.getDefault()
                    }
                }

            val sentPI = PendingIntent.getBroadcast(
                context,
                0,
                Intent(ACTION_SMS_SENT),
                PendingIntent.FLAG_IMMUTABLE
            )
            val deliveredPI = PendingIntent.getBroadcast(
                context,
                0,
                Intent(ACTION_SMS_DELIVERED),
                PendingIntent.FLAG_IMMUTABLE
            )

            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                val sentIntents = ArrayList<PendingIntent>()
                val deliveredIntents = ArrayList<PendingIntent>()
                for (i in parts.indices) {
                    sentIntents.add(
                        PendingIntent.getBroadcast(
                            context,
                            i,
                            Intent(ACTION_SMS_SENT),
                            PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                    deliveredIntents.add(
                        PendingIntent.getBroadcast(
                            context,
                            i,
                            Intent(ACTION_SMS_DELIVERED),
                            PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                }
                smsManager.sendMultipartTextMessage(
                    phoneNumber,
                    null,
                    parts,
                    sentIntents,
                    deliveredIntents
                )
            } else {
                smsManager.sendTextMessage(phoneNumber, null, message, sentPI, deliveredPI)
            }
            runBlocking {
                launch {
                    playSound(context, R.raw.sms_sound_1)
                }
            }
            Toast.makeText(context, "SMS to $phoneNumber initiated.", Toast.LENGTH_SHORT).show()
            Log.d(
                TAG,
                "SMS to $phoneNumber initiated using SIM ID: $simSubscriptionId"
            )
        } catch (e: Exception) {
            Toast.makeText(context, "SMS failed: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e(TAG, "SMS failed", e)
        }
    }

    private fun sendSmsToMultiple(
        context: Context,
        contacts: List<Contact>,
        message: String,
        simId: Int?
    ) {
        contacts.forEach { contact ->
            sendSms(context, contact.phoneNumber, message, simId)
        }
    }

    fun sendLocationViaSms(
        context: Context,
        location: Location?,
        toContacts: List<Contact>,
        simId: Int?,
        prefix: String
    ) {
        val message = if (location == null) {
            "$prefix Location not available."
        } else {
            "$prefix https://maps.google.com/?q=${location.latitude},${location.longitude}"
        }
        sendSmsToMultiple(context, toContacts, message, simId)
    }

    @RequiresPermission(Manifest.permission.WRITE_CONTACTS)
    fun addContactToDevice(context: Context, name: String, phoneNumber: String) {
        // ... (The exact same implementation of your addContactToDevice function)
        // For brevity, I'm omitting the full code, but you should copy it here.
        // Make sure to replace `contentResolver` with `context.contentResolver`.
    }

    // --- Media Features ---

    @SuppressLint("MissingPermission")
    fun startAudioRecording(context: Context, durationMillis: Long?, onFinished: (File?) -> Unit) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(context, "Audio recording permission not granted.", Toast.LENGTH_SHORT)
                .show()
            onFinished(null)
            return
        }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        audioFile =
            File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "AUDIO_$timestamp.mp3")
        mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(audioFile!!.absolutePath)
            try {
                prepare()
                start()
                Toast.makeText(context, "Recording started.", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Audio recording started: ${audioFile!!.absolutePath}")
                durationMillis?.let {
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(it)
                        stopAudioRecording(context, onFinished)
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "MediaRecorder prepare() failed", e)
                Toast.makeText(context, "Recording failed to start.", Toast.LENGTH_SHORT).show()
                onFinished(null)
            }
        }
    }

    fun stopAudioRecording(context: Context, onFinished: (File?) -> Unit) {
        mediaRecorder?.apply {
            try {
                stop()
                release()
                Toast.makeText(
                    context,
                    "Recording stopped. Saved to ${audioFile?.name}",
                    Toast.LENGTH_LONG
                ).show()
                Log.d(TAG, "Audio recording stopped. File: ${audioFile?.absolutePath}")
                onFinished(audioFile)
            } catch (e: RuntimeException) {
                Log.e(TAG, "MediaRecorder stop() failed", e)
                // Use the passed context
                Toast.makeText(context, "Failed to properly stop recording.", Toast.LENGTH_SHORT)
                    .show()
                audioFile?.delete()
                onFinished(null)
            }
        }
        mediaRecorder = null
    }
}