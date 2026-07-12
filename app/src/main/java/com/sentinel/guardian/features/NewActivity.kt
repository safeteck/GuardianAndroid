package com.sentinel.guardian.features

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.sentinel.guardian.BatteryStateReceiver
import com.sentinel.guardian.ui.screens.features.ServiceRegistry
import com.sentinel.guardian.ui.screens.navigation.MainScreen
import com.sentinel.guardian.ui.theme.GuardianTheme

class NewActivity : ComponentActivity() {
    val batteryStateReceiver = BatteryStateReceiver()

    override fun onStart() {
        super.onStart()
        ServiceRegistry.registerBatteryReceiver(this, batteryStateReceiver)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GuardianTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen( this, this)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ServiceRegistry.unregisterBatteryReceiver(this,batteryStateReceiver)
    }
}
