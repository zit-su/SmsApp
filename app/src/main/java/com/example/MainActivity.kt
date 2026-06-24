package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.data.SmsDatabase
import com.example.data.SmsRepository
import com.example.ui.SmsSchedulerApp
import com.example.ui.SmsViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Initialize database, repository, and view model
    val database = SmsDatabase.getDatabase(applicationContext)
    val repository = SmsRepository(database.smsDao())
    val viewModelFactory = SmsViewModel.Factory(applicationContext, repository)
    val viewModel = ViewModelProvider(this, viewModelFactory)[SmsViewModel::class.java]

    setContent {
      MyApplicationTheme {
        SmsSchedulerApp(viewModel = viewModel)
      }
    }
  }
}
