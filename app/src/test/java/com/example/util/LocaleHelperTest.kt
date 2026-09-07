package com.example.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.PreferenceManager
import com.example.ui.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocaleHelperTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var viewModel: SettingsViewModel
    private lateinit var context: Context

    @Before
    fun setup() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext<Context>()
        preferenceManager = PreferenceManager(context)
        preferenceManager.updateAppLanguage("system")
        advanceUntilIdle()
        viewModel = SettingsViewModel(preferenceManager, ioDispatcher = testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testLanguageSelectionAndPersistence() = runTest(testDispatcher) {
        assertEquals("system", viewModel.appLanguage.value)

        viewModel.setAppLanguage("en").join()
        advanceUntilIdle()
        assertEquals("en", viewModel.appLanguage.value)
        assertEquals("en", preferenceManager.appLanguageFlow.first())
        assertEquals("en", preferenceManager.getAppLanguageAsync())

        viewModel.setAppLanguage("fr").join()
        advanceUntilIdle()
        assertEquals("fr", viewModel.appLanguage.value)
        assertEquals("fr", preferenceManager.appLanguageFlow.first())
        assertEquals("fr", preferenceManager.getAppLanguageAsync())
    }

    @Test
    fun testLocaleHelperApplyLanguage() {
        val enContext = LocaleHelper.applyLanguage(context, "en")
        assertEquals("en", enContext.resources.configuration.locales.get(0).language)

        val frContext = LocaleHelper.applyLanguage(context, "fr")
        assertEquals("fr", frContext.resources.configuration.locales.get(0).language)
    }
}
