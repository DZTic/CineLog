package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SearchScreenOnboardingTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun preferenceManager_onboardingDismissState() = kotlinx.coroutines.test.runTest {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val pm = com.example.data.PreferenceManager(context)
        pm.updateHasDismissedOnboarding(false)
        org.junit.Assert.assertFalse(pm.hasDismissedOnboardingAsync())
        pm.updateHasDismissedOnboarding(true)
        org.junit.Assert.assertTrue(pm.hasDismissedOnboardingAsync())
        // Reset pour les tests suivants
        pm.updateHasDismissedOnboarding(false)
    }
}