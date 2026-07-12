package com.sentinel.guardian.ui.screens.services// src/main/java/com/your/package/MyApplication.kt

import android.app.Application
import com.google.crypto.tink.aead.AeadConfig
import java.security.GeneralSecurityException

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize Tink
        try {
            AeadConfig.register()
        } catch (e: GeneralSecurityException) {
            // Handle exception (e.g., log it)
            throw RuntimeException(e)
        }
    }
}