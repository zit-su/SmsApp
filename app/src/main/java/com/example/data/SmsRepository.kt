package com.example.data

import kotlinx.coroutines.flow.Flow

class SmsRepository(private val smsDao: SmsDao) {

    // Scheduled SMS
    val allScheduledSms: Flow<List<ScheduledSms>> = smsDao.getAllScheduledSmsFlow()

    suspend fun getPendingScheduledSms(): List<ScheduledSms> = smsDao.getPendingScheduledSms()

    suspend fun getScheduledSmsById(id: Int): ScheduledSms? = smsDao.getScheduledSmsById(id)

    suspend fun insertScheduledSms(sms: ScheduledSms): Long = smsDao.insertScheduledSms(sms)

    suspend fun updateScheduledSms(sms: ScheduledSms) = smsDao.updateScheduledSms(sms)

    suspend fun deleteScheduledSmsById(id: Int) = smsDao.deleteScheduledSmsById(id)

    // SMS Messages history
    val allMessages: Flow<List<SmsMessage>> = smsDao.getAllMessagesFlow()

    fun getMessagesForContact(phoneNumber: String): Flow<List<SmsMessage>> = 
        smsDao.getMessagesForContactFlow(phoneNumber)

    suspend fun insertSmsMessage(message: SmsMessage): Long = smsDao.insertSmsMessage(message)

    suspend fun deleteSmsMessageById(id: Int) = smsDao.deleteSmsMessageById(id)

    suspend fun clearAllMessages() = smsDao.clearAllMessages()
}
