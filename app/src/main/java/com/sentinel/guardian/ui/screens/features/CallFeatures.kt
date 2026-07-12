package com.sentinel.guardian.ui.screens.features

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri

object CallFeatures {
    // File: utils/PhoneDialer.kt

    /**
     * A utility class to handle phone call and dial actions.
     * It's created as a class to be easily testable and managed via dependency injection if needed.
     *
     * @param context The Android context, needed to start activities.
     */
//    class PhoneDialer(private val context: Context) {

    /**
     * Opens the phone's dialer app with the specified phone number pre-filled.
     * This action does NOT require the CALL_PHONE permission.
     *
     * @param phoneNumber The phone number to dial.
     */
    fun dial(context: Context, phoneNumber: String) {
        try {
            val intent = Intent(Intent.ACTION_DIAL, "tel:$phoneNumber".toUri())
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.e("PhoneDialer", "No phone dialer app found.", e)
            // Optionally, show a toast or a snackbar to the user
        }
    }

    /**
     * Directly initiates a phone call to the specified number.
     *
     * IMPORTANT: This action REQUIRES the `android.permission.CALL_PHONE` permission.
     * The permission check should be handled by the caller of this function.
     * This function assumes permission has already been granted.
     *
     * @param phoneNumber The phone number to call.
     */
    fun call(context: Context, phoneNumber: String) {
        try {
            val intent = Intent(Intent.ACTION_CALL, "tel:$phoneNumber".toUri())
            context.startActivity(intent)
        } catch (e: SecurityException) {
            Log.e("PhoneDialer", "SecurityException: Missing CALL_PHONE permission.", e)
            // This should not happen if the permission is checked beforehand.
        } catch (e: ActivityNotFoundException) {
            Log.e("PhoneDialer", "No phone calling app found.", e)
        }
    }
//    }
}