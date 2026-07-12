package com.sentinel.guardian.vosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat.startForegroundService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Received BOOT_COMPLETED, starting service")
//            val serviceIntent = Intent(context, KeywordService::class.java)
//            startForegroundService(VoskApplication(),serviceIntent) // Use startForegroundService
//            context.startForegroundService(serviceIntent) // Use startForegroundService
        }
    }
}