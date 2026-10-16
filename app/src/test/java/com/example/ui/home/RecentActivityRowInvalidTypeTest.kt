package com.example.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.example.data.DbLogEntry
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

// #148: a log imported with titleType = "2019" (Letterboxd diary.csv) used to throw
// IllegalArgumentException while composing Home, crashing the app at every launch.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecentActivityRowInvalidTypeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun logWithUnknownTitleType_rendersAsFilm() {
        val log = DbLogEntry(
            id = 1,
            titleId = "Parasite",
            titleType = "2019",
            titleName = "Parasite",
            titlePosterUrl = null,
            dateVue = 1700000000000L,
            note = 4.5f,
            critique = "",
            revisionnage = false,
            spoiler = false
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                RecentActivityRow(log = log, onTitleClick = {})
            }
        }

        composeTestRule.onNodeWithText("Parasite").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Type : Film").assertExists()
    }
}
