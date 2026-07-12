// file: com.sentinel.guardian.features/ServiceRegistry.kt

package com.sentinel.guardian.ui.screens.features

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.util.Log
import com.sentinel.guardian.ACTION_SMS_DELIVERED
import com.sentinel.guardian.ACTION_SMS_SENT
import com.sentinel.guardian.features.FeaturesServices

/**
 * A singleton object to manage the registration and un-registration of services,
 * receivers, and listeners throughout the app.
 */
object ServiceRegistry {
    private const val TAG = "ServiceRegistry"

    // --- Manifest Component Enabler/Disabler ---

    fun enableManifestComponent(context: Context, componentClass: Class<*>) {
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, componentClass),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
        Log.d(TAG, "${componentClass.simpleName} enabled.")
    }

    fun disableManifestComponent(context: Context, componentClass: Class<*>) {
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, componentClass),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        Log.d(TAG, "${componentClass.simpleName} disabled.")
    }

    // --- Broadcast Receiver Registration ---

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun registerSmsStatusReceiver(context: Context, receiver: BroadcastReceiver) {
        val intentFilter = IntentFilter(ACTION_SMS_SENT).apply { addAction(ACTION_SMS_DELIVERED) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, intentFilter)
        }
        Log.d(TAG, "SmsStatusReceiver registered.")
    }

    fun unregisterSmsStatusReceiver(context: Context, receiver: BroadcastReceiver) {
        try {
            context.unregisterReceiver(receiver)
            Log.d(TAG, "SmsStatusReceiver unregistered.")
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "SmsStatusReceiver was not registered.", e)
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun registerBatteryReceiver(context: Context, receiver: BroadcastReceiver) {
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, intentFilter)
        }
        Log.d(TAG, "BatteryReceiver registered.")
    }

    fun unregisterBatteryReceiver(context: Context, receiver: BroadcastReceiver) {
        try {
            context.unregisterReceiver(receiver)
            Log.d(TAG, "BatteryReceiver unregistered.")
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "BatteryReceiver was not registered.", e)
        }
    }

    // --- Sensor Listener Registration ---

    fun registerSensorListeners(context: Context, listener: SensorEventListener) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        gyroscope?.also { sensor ->
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d(TAG, "Gyroscope listener registered.")
        }
        accelerometer?.also { sensor ->
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d(TAG, "Accelerometer listener registered.")
        }
    }

    fun unregisterSensorListeners(context: Context, listener: SensorEventListener) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager.unregisterListener(listener)
        Log.d(TAG, "All sensor listeners unregistered.")
    }

    // --- Background Service Control ---

    fun startFeaturesService(context: Context) {
        FeaturesServices.startService(context)
    }

    fun stopFeaturesService(context: Context) {
        FeaturesServices.stopService(context)
    }
}