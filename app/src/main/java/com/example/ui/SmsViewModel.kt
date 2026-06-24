package com.example.ui

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.Contact
import com.example.data.ContactLoader
import com.example.data.ScheduledSms
import com.example.data.SmsMessage
import com.example.data.SmsRepository
import com.example.receiver.SmsSchedulerReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SmsViewModel(
    private val context: Context,
    val repository: SmsRepository
) : ViewModel() {

    // Scheduled SMS Flow
    val scheduledSmsList: StateFlow<List<ScheduledSms>> = repository.allScheduledSms
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Message History Flow
    val messageHistory: StateFlow<List<SmsMessage>> = repository.allMessages
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Contacts state
    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    private val _isPermissionGranted = MutableStateFlow(false)
    val isPermissionGranted: StateFlow<Boolean> = _isPermissionGranted.asStateFlow()

    init {
        loadContacts()
    }

    fun setPermissionStatus(granted: Boolean) {
        _isPermissionGranted.value = granted
        loadContacts()
    }

    fun loadContacts() {
        viewModelScope.launch(Dispatchers.IO) {
            val deviceContacts = ContactLoader.loadDeviceContacts(context)
            if (deviceContacts.isEmpty()) {
                // If permission is not granted or contacts are empty, use high-quality simulated contacts
                _contacts.value = ContactLoader.simulatedContacts
            } else {
                _contacts.value = deviceContacts
            }
        }
    }

    // Schedule SMS
    fun scheduleSms(
        phoneNumber: String,
        contactName: String,
        message: String,
        scheduledTimeMs: Long
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val sms = ScheduledSms(
                phoneNumber = phoneNumber,
                contactName = contactName,
                message = message,
                scheduledTime = scheduledTimeMs,
                status = "PENDING"
            )
            // Insert in DB
            val smsId = repository.insertScheduledSms(sms).toInt()

            // Schedule the AlarmManager
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(context, SmsSchedulerReceiver::class.java).apply {
                    putExtra("sms_id", smsId)
                }
                
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    smsId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        scheduledTimeMs,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        scheduledTimeMs,
                        pendingIntent
                    )
                }
                Log.d("SmsViewModel", "Successfully scheduled SMS with ID $smsId at $scheduledTimeMs")
            } catch (e: Exception) {
                Log.e("SmsViewModel", "Failed to schedule alarm", e)
                // Update DB state to FAILED
                repository.updateScheduledSms(
                    sms.copy(id = smsId, status = "FAILED", errorMessage = "Alarm scheduling failed: ${e.localizedMessage}")
                )
            }
        }
    }

    // Cancel Scheduled SMS
    fun cancelScheduledSms(smsId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val sms = repository.getScheduledSmsById(smsId)
            if (sms != null && sms.status == "PENDING") {
                // Cancel Alarm Manager
                try {
                    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    val intent = Intent(context, SmsSchedulerReceiver::class.java)
                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        smsId,
                        intent,
                        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                    )
                    if (pendingIntent != null) {
                        alarmManager.cancel(pendingIntent)
                        pendingIntent.cancel()
                    }
                } catch (e: Exception) {
                    Log.e("SmsViewModel", "Failed to cancel alarm", e)
                }

                // Update database
                repository.updateScheduledSms(sms.copy(status = "CANCELLED"))
            }
        }
    }

    // Delete Scheduled SMS from log
    fun deleteScheduledSms(smsId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            cancelScheduledSms(smsId)
            repository.deleteScheduledSmsById(smsId)
        }
    }

    // Send SMS immediately
    fun sendSmsImmediately(phoneNumber: String, contactName: String?, messageText: String, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }

                smsManager.sendTextMessage(phoneNumber, null, messageText, null, null)

                // Save to history
                repository.insertSmsMessage(
                    SmsMessage(
                        phoneNumber = phoneNumber,
                        contactName = contactName,
                        messageText = messageText,
                        timestamp = System.currentTimeMillis(),
                        isIncoming = false
                    )
                )

                onComplete(true, null)
            } catch (e: Exception) {
                Log.e("SmsViewModel", "Error sending SMS immediately", e)
                // Still insert to local message history, but maybe we can note it failed if needed
                // For simplicity, we just notify the UI of failure
                onComplete(false, e.localizedMessage ?: "Unknown error")
            }
        }
    }

    // Simulate Receiving an SMS (perfect for local testing in AI Studio emulator!)
    fun simulateReceivedSms(phoneNumber: String, contactName: String?, messageText: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertSmsMessage(
                SmsMessage(
                    phoneNumber = phoneNumber,
                    contactName = contactName,
                    messageText = messageText,
                    timestamp = System.currentTimeMillis(),
                    isIncoming = true
                )
            )
            Log.d("SmsViewModel", "Simulated incoming SMS from $phoneNumber: $messageText")
        }
    }

    // Clear Message History
    fun clearMessageHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAllMessages()
        }
    }

    // Factory to construct ViewModel with context and repo dependencies
    class Factory(
        private val context: Context,
        private val repository: SmsRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SmsViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return SmsViewModel(context, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
