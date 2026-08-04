package com.example

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExampleComposeTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun homeScreen_showsWelcomeMessage() {
    composeTestRule.setContent {
      MyApplicationTheme {
        Text("Welcome to CineLog")
      }
    }
    composeTestRule.onNodeWithText("Welcome to CineLog").assertExists()
  }
}
