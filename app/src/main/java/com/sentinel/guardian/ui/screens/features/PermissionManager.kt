package com.sentinel.guardian.ui.screens.features

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat

/**
 * A specialized singleton object to manage all permission-related logic for the app.
 * This centralizes permission definitions, checks, and request logic for better organization and maintenance.
 */
object PermissionManager {

    // Define individual permissions as constants for type safety and readability
    const val SEND_SMS = Manifest.permission.SEND_SMS
    const val RECEIVE_SMS = Manifest.permission.RECEIVE_SMS
    const val READ_CONTACTS = Manifest.permission.READ_CONTACTS
    const val WRITE_CONTACTS = Manifest.permission.WRITE_CONTACTS
    const val ACCESS_FINE_LOCATION = Manifest.permission.ACCESS_FINE_LOCATION
    const val ACCESS_COARSE_LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION
    const val RECORD_AUDIO = Manifest.permission.RECORD_AUDIO
    const val CAMERA = Manifest.permission.CAMERA
    const val READ_PHONE_STATE = Manifest.permission.READ_PHONE_STATE
    const val CALL_PHONE = Manifest.permission.CALL_PHONE // Added for completeness

    // Version-specific permissions
    @JvmField
    val ACTIVITY_RECOGNITION =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Manifest.permission.ACTIVITY_RECOGNITION else "android.permission.ACTIVITY_RECOGNITION"

    @JvmField
    val POST_NOTIFICATIONS =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else "android.permission.POST_NOTIFICATIONS"
    const val WRITE_EXTERNAL_STORAGE =
        Manifest.permission.WRITE_EXTERNAL_STORAGE // Below API 29
    const val READ_EXTERNAL_STORAGE =
        Manifest.permission.READ_EXTERNAL_STORAGE // Below API 29

    @JvmField
    val FOREGROUND_SERVICE_LOCATION =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) Manifest.permission.FOREGROUND_SERVICE_LOCATION else "android.permission.FOREGROUND_SERVICE_LOCATION"

    @JvmField
    val FOREGROUND_SERVICE_MICROPHONE =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) Manifest.permission.FOREGROUND_SERVICE_MICROPHONE else "android.permission.FOREGROUND_SERVICE_MICROPHONE"


    /**
     * A comprehensive list of all permissions required by the app, dynamically adjusted for the device's API level.
     */
    val allRequiredPermissions: List<String> by lazy {
        mutableListOf(
            SEND_SMS,
            RECEIVE_SMS,
            READ_CONTACTS,
            WRITE_CONTACTS,
            ACCESS_FINE_LOCATION,
            ACCESS_COARSE_LOCATION,
            RECORD_AUDIO,
            CAMERA,
            READ_PHONE_STATE,
            CALL_PHONE
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(ACTIVITY_RECOGNITION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(POST_NOTIFICATIONS)
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                add(WRITE_EXTERNAL_STORAGE)
                add(READ_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                add(FOREGROUND_SERVICE_LOCATION)
                add(FOREGROUND_SERVICE_MICROPHONE)
            }
        }.toList() // Return an immutable list
    }

    /**
     * Checks if a specific permission is granted.
     *
     * @param context The application context.
     * @param permission The permission string to check.
     * @return True if the permission is granted, false otherwise.
     */
    fun isPermissionGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * A specialized check for location permissions, as either FINE or COARSE is often sufficient.
     *
     * @param context The application context.
     * @return True if either fine or coarse location permission is granted.
     */
    fun hasLocationPermission(context: Context): Boolean {
        return isPermissionGranted(context, ACCESS_FINE_LOCATION) || isPermissionGranted(
            context,
            ACCESS_COARSE_LOCATION
        )
    }

    /**
     * A specialized check for contact read permission.
     * @param context The application context.
     * @return True if READ_CONTACTS permission is granted.
     */
    fun hasReadContactsPermission(context: Context): Boolean {
        return isPermissionGranted(context, READ_CONTACTS)
    }

    /**
     * A specialized check for contact write permission.
     * @param context The application context.
     * @return True if WRITE_CONTACTS permission is granted.
     */
    fun hasWriteContactsPermission(context: Context): Boolean {
        return isPermissionGranted(context, WRITE_CONTACTS)
    }

    /**
     * A specialized check for SMS sending permission.
     * @param context The application context.
     * @return True if SEND_SMS permission is granted.
     */
    fun hasSmsPermission(context: Context): Boolean {
        return isPermissionGranted(context, SEND_SMS)
    }

    /**
     * A specialized check for audio recording permission.
     * @param context The application context.
     * @return True if RECORD_AUDIO permission is granted.
     */
    fun hasRecordAudioPermission(context: Context): Boolean {
        return isPermissionGranted(context, RECORD_AUDIO)
    }

    /**
     * A specialized check for phone state reading permission.
     * @param context The application context.
     * @return True if READ_PHONE_STATE permission is granted.
     */
    fun hasReadPhoneStatePermission(context: Context): Boolean {
        return isPermissionGranted(context, READ_PHONE_STATE)
    }

    /**
     * Determines which of the required permissions are not yet granted.
     *
     * @param context The application context.
     * @return An array of permission strings that still need to be requested from the user.
     */
    private fun getUngrantedPermissions(context: Context): Array<String> {
        return allRequiredPermissions.filter {
            !isPermissionGranted(context, it)
        }.toTypedArray()
    }

    /**
     * The main function to initiate the permission check and request flow.
     * It identifies missing permissions and uses the provided launcher to request them.
     *
     * @param activity The component activity that is requesting the permissions.
     * @param launcher The ActivityResultLauncher responsible for handling the permission request result.
     * @param onAllPermissionsGranted A lambda to be executed if all permissions are already granted.
     */
    fun checkAndRequestPermissions(
        activity: ComponentActivity,
        launcher: ActivityResultLauncher<Array<String>>,
        onAllPermissionsGranted: () -> Unit
    ) {
        val permissionsToRequest = getUngrantedPermissions(activity)
        if (permissionsToRequest.isNotEmpty()) {
            launcher.launch(permissionsToRequest)
        } else {
            // All permissions are already granted, execute the callback
            onAllPermissionsGranted()
        }
    }


    /**
     * Checks if all permissions in a given list are granted.
     * @param context The application context.
     * @param permissions The list of permission strings to check.
     * @return True if all permissions in the list are granted, false otherwise.
     */
    fun arePermissionsGranted(context: Context, permissions: List<String>): Boolean {
        return permissions.all { isPermissionGranted(context, it) }
    }

}