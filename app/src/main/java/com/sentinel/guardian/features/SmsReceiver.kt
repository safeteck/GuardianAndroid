package com.sentinel.guardian.features

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            Log.d("SmsReceiver", "New SMS broadcast received. Parts count: ${messages.size}")

            // Group messages by sender. This handles multipart messages, which arrive in the same broadcast.
            val messagesBySender = messages.groupBy { it.displayOriginatingAddress }

            CoroutineScope(Dispatchers.IO).launch {
                val newStoredMessages = mutableListOf<StoredMessage>()
                messagesBySender.forEach { (sender, parts) ->
                    // Concatenate all message parts to get the full body
                    val fullMessageBody = parts.joinToString("") { it.messageBody }
                    val timestamp =
                        parts.first().timestampMillis // Use the timestamp of the first part

                    if (sender != null) {
                        Log.d("SmsReceiver", "Assembled message from $sender: $fullMessageBody")
                        newStoredMessages.add(
                            StoredMessage(
                                sender = sender,
                                body = fullMessageBody,
                                timestamp = timestamp
                            )
                        )
                    }
                }

                if (newStoredMessages.isNotEmpty()) {
                    // Load existing, append new, save all
                    val existingMessages = MessageRepository.loadMessages(context).toMutableList()
                    existingMessages.addAll(0, newStoredMessages) // Add new messages to the top
                    MessageRepository.saveMessages(context, existingMessages)

                    // Provide feedback to the user on the main thread
                    launch(Dispatchers.Main) {
                        val senderName = newStoredMessages.first().sender
                        Toast.makeText(
                            context,
                            "New message from $senderName saved.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }
}

object MessageRepository {
    private const val FILENAME = "received_messages.msgs"
    private const val TAG = "MessageRepository"

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun saveMessages(context: Context, messages: List<StoredMessage>) {
        try {
            val file = File(context.filesDir, FILENAME)
            val jsonString = json.encodeToString(messages)
            file.writeText(jsonString)
            Log.d(TAG, "Successfully saved ${messages.size} messages to $FILENAME")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving messages to file", e)
        }
    }

    fun loadMessages(context: Context): List<StoredMessage> {
        return try {
            val file = File(context.filesDir, FILENAME)
            if (!file.exists()) {
                Log.d(TAG, "Message file does not exist yet. Returning empty list.")
                return emptyList()
            }
            val jsonString = file.readText()
            if (jsonString.isBlank()) {
                return emptyList()
            }
            val messages = json.decodeFromString<List<StoredMessage>>(jsonString)
            Log.d(TAG, "Successfully loaded ${messages.size} messages from $FILENAME")
            messages
        } catch (e: Exception) {
            Log.e(TAG, "Error loading messages from file", e)
            emptyList()
        }
    }

    fun clearMessages(context: Context) {
        try {
            val file = File(context.filesDir, FILENAME)
            if (file.exists()) {
                file.delete()
                Log.d(TAG, "Message file deleted.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting message file", e)
        }
    }
}

@Serializable
data class StoredMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: String,
    val body: String,
    val timestamp: Long
)
