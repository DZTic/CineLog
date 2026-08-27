package com.example.ui.components

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LocalSearchBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun localSearchBar_displaysPlaceholderAndAcceptsInput() {
        val queryState = mutableStateOf("")

        composeTestRule.setContent {
            MyApplicationTheme {
                LocalSearchBar(
                    query = queryState.value,
                    onQueryChange = { queryState.value = it },
                    placeholder = "Rechercher dans votre journal...",
                    testTag = "test_search_bar"
                )
            }
        }

        composeTestRule.onNodeWithTag("test_search_bar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Rechercher dans votre journal...").assertIsDisplayed()

        composeTestRule.onNodeWithTag("test_search_bar").performTextInput("Inception")
        assertEquals("Inception", queryState.value)
    }

    @Test
    fun localSearchBar_clearButtonClearsQuery() {
        val queryState = mutableStateOf("Oppenheimer")

        composeTestRule.setContent {
            MyApplicationTheme {
                LocalSearchBar(
                    query = queryState.value,
                    onQueryChange = { queryState.value = it },
                    placeholder = "Rechercher...",
                    testTag = "test_search_bar"
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Effacer la recherche").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Effacer la recherche").performClick()
        assertEquals("", queryState.value)
    }
}
