package com.example.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.data.SmsDatabase
import com.example.data.SmsMessage
import com.example.data.SmsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) {
                Log.e("SmsReceiver", "No messages found in intent")
                return
            }

            // Group parts of multipart message from same sender
            val sender = messages[0].displayOriginatingAddress ?: "Unknown"
            val bodyBuilder = StringBuilder()
            for (msg in messages) {
                bodyBuilder.append(msg.displayMessageBody)
            }
            val body = bodyBuilder.toString()

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = SmsDatabase.getDatabase(context)
                    val repository = SmsRepository(db.smsDao())

                    // Insert to message history
                    repository.insertSmsMessage(
                        SmsMessage(
                            phoneNumber = sender,
                            contactName = null, // Will try to look up in UI/state if matches
                            messageText = body,
                            timestamp = System.currentTimeMillis(),
                            isIncoming = true
                        )
                    )

                    // Show notification
                    showNotification(context, sender, body)
                } catch (e: Exception) {
                    Log.e("SmsReceiver", "Error saving received SMS", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun showNotification(context: Context, sender: String, content: String) {
        val channelId = "sms_receiver_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Incoming SMS Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for incoming SMS messages"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("New SMS from $sender")
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
