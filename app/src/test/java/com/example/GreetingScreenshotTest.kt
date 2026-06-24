package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.SmsDatabase
import com.example.data.SmsRepository
import com.example.ui.SmsSchedulerApp
import com.example.ui.SmsViewModel
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    
    // Create an in-memory database so tests are completely isolated, fast, and self-contained
    val db = Room.inMemoryDatabaseBuilder(context, SmsDatabase::class.java)
        .allowMainThreadQueries()
        .build()
        
    val repository = SmsRepository(db.smsDao())
    val viewModel = SmsViewModel(context, repository)

    composeTestRule.setContent { 
      MyApplicationTheme { 
        SmsSchedulerApp(viewModel = viewModel) 
      } 
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
    
    db.close()
  }
}
