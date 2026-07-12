package com.sentinel.guardian

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationServices
//import com.sentinel.guardian.features.CameraManager
import com.sentinel.guardian.features.FeaturesServices
import com.sentinel.guardian.features.LoginScreen
import com.sentinel.guardian.features.NewActivity
import com.sentinel.guardian.features.SmsReceiver
import com.sentinel.guardian.ui.theme.GuardianTheme
import kotlinx.coroutines.Job
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

// Data Classes
@Serializable
@Parcelize
data class Contact(
    val id: String,
    val name: String,
    val phoneNumber: String,
    var category: ContactCategory = ContactCategory.GENERAL
): Parcelable

@Serializable
enum class ContactCategory {
    PRIVATE, GENERAL, FRIENDS, EMERGENCY
}

data class CarTripDetails(
    val vehicleFirstName: String? = null,
    val vehicleLastName: String? = null,
    val licensePlate: String? = null,
    val carModel: String? = null,
    val tripOrigin: String? = null,
    val tripDestination: String? = null
)

// Constants
const val TAG = "AdvancedSmsApp"
const val ACTION_SMS_SENT = "com.sentinel.guardian.features.SMS_SENT"
const val ACTION_SMS_DELIVERED = "com.sentinel.guardian.features.SMS_DELIVERED"

class MainActivity : AppCompatActivity() {
//    internal lateinit var cameraManager: CameraManager

    private lateinit var sensorManager: SensorManager
    private var gyroscope: Sensor? = null
    private var accelerometer: Sensor? = null
    private var mFusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var liveLocationJob: Job? = null

    private var selectedSimSubscriptionId by mutableStateOf<Int?>(null)
    private val simInfos = mutableStateListOf<SubscriptionInfo>()

        private lateinit var biometricPrompt: BiometricPrompt
        private lateinit var promptInfo: BiometricPrompt.PromptInfo

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
/*
        cameraManager = CameraManager(
            context = this,
            lifecycleOwner = this,
            mainExecutor = ContextCompat.getMainExecutor(this)
        )
*/

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupBiometricPrompt()

        setContent {
            GuardianTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LoginScreen(
                        onUnlock = {
                            startActivity(Intent(this, NewActivity::class.java))
                            finish()
                        },
                        showBiometricPrompt = {
                            biometricPrompt.authenticate(promptInfo)
                        }
                    )
                }
            }
        }

        loadSimInfo()
    }

    private fun setupBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this@MainActivity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                startActivity(Intent(this@MainActivity, NewActivity::class.java))
                finish()
            }
        }

        biometricPrompt = BiometricPrompt(this, executor, callback)

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biometric login")
            .setSubtitle("Log in using your biometric credential")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
    }


    override fun onPause() {
        super.onPause()
        stopLiveLocationUpdates()
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    @SuppressLint("MissingPermission")
    private fun loadSimInfo() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val subscriptionManager =
                getSystemService(TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val activeSubscriptionInfoList: List<SubscriptionInfo>? =
                subscriptionManager.activeSubscriptionInfoList
            simInfos.clear()
            activeSubscriptionInfoList?.forEach { info ->
                simInfos.add(info)
                Log.d(TAG, "Found SIM: ${info.displayName} - ${info.subscriptionId}")
            }
            if (simInfos.isNotEmpty() && selectedSimSubscriptionId == null) {
                selectedSimSubscriptionId = simInfos.first().subscriptionId
            }
        } else {
            Log.w(TAG, "READ_PHONE_STATE permission not granted.")
        }
    }



    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onStart() {
        super.onStart()
        enableReceiveSmsReceiverManifestService()
    }

    private fun enableReceiveSmsReceiverManifestService() {
        packageManager.setComponentEnabledSetting(
            ComponentName(this, SmsReceiver::class.java),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    override fun onStop() {
        super.onStop()
        disableReceiveSmsReceiverManifestService()
    }


    private fun disableReceiveSmsReceiverManifestService() {
        packageManager.setComponentEnabledSetting(
            ComponentName(this, SmsReceiver::class.java),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    override fun onDestroy() {
        super.onDestroy()
    }


    private fun stopLiveLocationUpdates() {
        locationCallback?.let {
            mFusedLocationClient?.removeLocationUpdates(it)
            Log.d(TAG, "Live location updates stopped.")
        }
        liveLocationJob?.cancel()
    }
}

class BatteryStateReceiver(val batteryPercentage: Float = 15.0f) : BroadcastReceiver
    () {
    companion object {
        const val TAG = "BatteryStateReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context != null && intent?.action == Intent.ACTION_BATTERY_CHANGED) {
            val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val batteryPct: Float = level * 100 / scale.toFloat()
            Log.d(TAG, "Battery level: $batteryPct%")

            val status: Int = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging =
                status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

            // Trigger alert if battery is at 15% or less and not charging
            if (batteryPct <= batteryPercentage && !isCharging) {
                val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                val lastAlertTime = prefs.getLong("last_low_battery_alert_time", 0)

                // Throttle the alert to once every 10 minutes
                if (System.currentTimeMillis() - lastAlertTime > 10 * 60 * 1000) {
                    Log.w(
                        TAG,
                        "Battery critically low ($batteryPct%). Triggering low battery task via Service."
                    )

                    // DECOUPLED: Start the service to handle the task
                    FeaturesServices.Companion.startTask(context, "LOW_BATTERY_ALERT")

                    prefs.edit {
                        putLong("last_low_battery_alert_time", System.currentTimeMillis())
                    }
                }
            }
        }
    }
}
