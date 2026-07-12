package com.sentinel.guardian.features

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.sentinel.guardian.R

object NotificationHelper {

    const val CHANNEL_ID = "GuardianServiceChannel"
    private const val CHANNEL_NAME = "Guardian Background Service"
    const val NOTIFICATION_ID = 101

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    fun createNotification(context: Context, text: String): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("WARNING App Active")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Use your app's icon
            .setOngoing(true)
            .build()
    }
}