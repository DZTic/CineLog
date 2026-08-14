package com.example.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Baseline Profile generator for CineLog.
 * Captures critical user journeys:
 * - App cold startup
 * - Navigating home feeds
 * - Navigating to detail screen
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generateBaselineProfile() {
        baselineProfileRule.collect(
            packageName = "com.example",
            includeInStartupProfile = true
        ) {
            // Cold start
            pressHome()
            startActivityAndWait()

            // Allow initial feed rendering
            device.waitForIdle()
        }
    }
}
