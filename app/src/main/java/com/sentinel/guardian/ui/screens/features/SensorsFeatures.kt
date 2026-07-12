package com.sentinel.guardian.ui.screens.features

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.LocationManager
import android.util.Log

/**
 * A singleton utility object to check for the availability of various device sensors and features.
 */
object SensorsFeatures {

    private const val TAG = "SensorsFeatures"

    /**
     * Checks if the device has an accelerometer sensor.
     *
     * @param context The application context to access system services.
     * @return `true` if an accelerometer is available, `false` otherwise.
     */
    fun isAccelerometerAvailable(context: Context): Boolean {
        return isSensorAvailable(context, Sensor.TYPE_ACCELEROMETER)
    }

    /**
     * Checks if the device has a gyroscope sensor.
     *
     * @param context The application context to access system services.
     * @return `true` if a gyroscope is available, `false` otherwise.
     */
    fun isGyroscopeAvailable(context: Context): Boolean {
        return isSensorAvailable(context, Sensor.TYPE_GYROSCOPE)
    }

    /**
     * A generic private helper function to check for any sensor type.
     */
    private fun isSensorAvailable(context: Context, sensorType: Int): Boolean {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        if (sensorManager == null) {
            Log.e(TAG, "Could not get SensorManager service.")
            return false
        }
        val sensor = sensorManager.getDefaultSensor(sensorType)
        return sensor != null
    }

    /**
     * Checks if the GPS provider is enabled in the device's settings.
     * This checks if the user has turned GPS on, not just if the hardware exists.
     *
     * @param context The application context to access system services.
     * @return `true` if GPS is enabled, `false` otherwise.
     */
    fun isGpsEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager == null) {
            Log.e(TAG, "Could not get LocationManager service.")
            return false
        }
        return try {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check GPS status", e)
            false
        }
    }
}