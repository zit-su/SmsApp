package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsDao {
    // Scheduled SMS queries
    @Query("SELECT * FROM scheduled_sms ORDER BY scheduledTime ASC")
    fun getAllScheduledSmsFlow(): Flow<List<ScheduledSms>>

    @Query("SELECT * FROM scheduled_sms WHERE status = 'PENDING' ORDER BY scheduledTime ASC")
    suspend fun getPendingScheduledSms(): List<ScheduledSms>

    @Query("SELECT * FROM scheduled_sms WHERE id = :id")
    suspend fun getScheduledSmsById(id: Int): ScheduledSms?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScheduledSms(sms: ScheduledSms): Long

    @Update
    suspend fun updateScheduledSms(sms: ScheduledSms)

    @Query("DELETE FROM scheduled_sms WHERE id = :id")
    suspend fun deleteScheduledSmsById(id: Int)

    // SMS Messages History queries
    @Query("SELECT * FROM sms_messages ORDER BY timestamp DESC")
    fun getAllMessagesFlow(): Flow<List<SmsMessage>>

    @Query("SELECT * FROM sms_messages WHERE phoneNumber = :phoneNumber ORDER BY timestamp ASC")
    fun getMessagesForContactFlow(phoneNumber: String): Flow<List<SmsMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSmsMessage(message: SmsMessage): Long

    @Query("DELETE FROM sms_messages WHERE id = :id")
    suspend fun deleteSmsMessageById(id: Int)

    @Query("DELETE FROM sms_messages")
    suspend fun clearAllMessages()
}
