package com.sentinel.guardian.vosk

import android.app.Application
import android.content.Intent
import android.os.Build
import android.util.Log

class VoskApplication : Application() {
    override fun onCreate() {
        super.onCreate()
//        startKeywordService()
    }

    private fun startKeywordService() {
        val serviceIntent = Intent(this, VoskService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
            Log.d(TAG, "startKeywordService: startForegroundService")
        } else {
            startService(serviceIntent)
            Log.d(TAG, "startKeywordService: startService")
        }
    }

    companion object {
        private const val TAG = "VoskApplication"
    }
}