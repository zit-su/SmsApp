package com.example.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.data.SmsDatabase
import com.example.data.SmsMessage
import com.example.data.SmsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsSchedulerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val smsId = intent.getIntExtra("sms_id", -1)
        if (smsId == -1) {
            Log.e("SmsSchedulerReceiver", "Received broadcast with invalid sms_id")
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = SmsDatabase.getDatabase(context)
                val repository = SmsRepository(db.smsDao())
                val scheduledSms = repository.getScheduledSmsById(smsId)

                if (scheduledSms == null) {
                    Log.e("SmsSchedulerReceiver", "Scheduled SMS not found for id: $smsId")
                    return@launch
                }

                if (scheduledSms.status != "PENDING") {
                    Log.d("SmsSchedulerReceiver", "SMS $smsId is not PENDING (status: ${scheduledSms.status})")
                    return@launch
                }

                try {
                    // Try to send SMS
                    val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        context.getSystemService(SmsManager::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        SmsManager.getDefault()
                    }

                    // Send SMS
                    smsManager.sendTextMessage(
                        scheduledSms.phoneNumber,
                        null,
                        scheduledSms.message,
                        null,
                        null
                    )

                    // Update scheduled record
                    repository.updateScheduledSms(scheduledSms.copy(status = "SENT"))

                    // Add to message history
                    repository.insertSmsMessage(
                        SmsMessage(
                            phoneNumber = scheduledSms.phoneNumber,
                            contactName = scheduledSms.contactName,
                            messageText = scheduledSms.message,
                            timestamp = System.currentTimeMillis(),
                            isIncoming = false
                        )
                    )

                    // Show notification
                    showNotification(
                        context,
                        "Message Sent",
                        "Scheduled message to ${scheduledSms.contactName.ifEmpty { scheduledSms.phoneNumber }} sent successfully."
                    )
                } catch (e: Exception) {
                    Log.e("SmsSchedulerReceiver", "Error sending text message", e)
                    repository.updateScheduledSms(
                        scheduledSms.copy(
                            status = "FAILED",
                            errorMessage = e.localizedMessage ?: "Unknown error"
                        )
                    )
                    showNotification(
                        context,
                        "Message Sending Failed",
                        "Could not send scheduled message to ${scheduledSms.contactName.ifEmpty { scheduledSms.phoneNumber }}."
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(context: Context, title: String, content: String) {
        val channelId = "sms_scheduler_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "SMS Scheduler Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for scheduled SMS statuses"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
