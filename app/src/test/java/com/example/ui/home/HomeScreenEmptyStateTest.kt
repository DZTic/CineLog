package com.example.ui.home

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.ui.components.EmptyState
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScreenEmptyStateTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun emptyState_displaysTitleMessageAndAction() {
        composeTestRule.setContent {
            MyApplicationTheme {
                EmptyState(
                    title = "Votre journal est vide",
                    message = "Aucun visionnage récent dans votre journal.",
                    action = {
                        Text("Qu'avez-vous regardé récemment ?")
                    }
                )
            }
        }

        composeTestRule.onNodeWithText("Votre journal est vide").assertIsDisplayed()
        composeTestRule.onNodeWithText("Aucun visionnage récent dans votre journal.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Qu'avez-vous regardé récemment ?").assertIsDisplayed()
    }
}