package com.sentinel.guardian.features

import android.content.Context
import android.media.MediaPlayer
import android.util.Log

// Function to play a sound file in Android using Kotlin
// @param context The context of the application (e.g., Activity or Application context)
// @param soundResId The resource ID of the sound file (e.g., R.raw.your_sound_file)
// Note: The sound file should be placed in the res/raw directory
fun playSound(context: Context, soundResId: Int) {
    try {
        val mediaPlayer = MediaPlayer.create(context, soundResId)
        mediaPlayer.setOnCompletionListener { mp -> mp.release() }
        mediaPlayer.start()
    } catch (e: Exception) {
        Log.e("SoundPlayer", "Error playing sound", e)
    }
}
