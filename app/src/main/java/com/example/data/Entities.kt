package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scheduled_sms")
data class ScheduledSms(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val phoneNumber: String,
    val contactName: String,
    val message: String,
    val scheduledTime: Long,
    val status: String, // PENDING, SENT, FAILED, CANCELLED
    val errorMessage: String? = null
)

@Entity(tableName = "sms_messages")
data class SmsMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val phoneNumber: String,
    val contactName: String?,
    val messageText: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isIncoming: Boolean
)
