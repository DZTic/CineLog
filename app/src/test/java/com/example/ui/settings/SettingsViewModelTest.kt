package com.example.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.PreferenceManager
import com.example.ui.theme.AppThemeMode
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        preferenceManager = PreferenceManager(context)
        preferenceManager.updateThemeMode("DARK")
        preferenceManager.updateDynamicColorEnabled(false)
        preferenceManager.updateTmdbApiKey("")
        advanceUntilIdle()
        viewModel = SettingsViewModel(preferenceManager, ioDispatcher = testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testThemeModePersistence() = runTest(testDispatcher) {
        assertEquals(AppThemeMode.DARK, viewModel.themeMode.value)

        viewModel.setThemeMode(AppThemeMode.LIGHT).join()
        advanceUntilIdle()
        assertEquals(AppThemeMode.LIGHT, viewModel.themeMode.value)
        assertEquals("LIGHT", preferenceManager.themeModeFlow.first())
        assertEquals("LIGHT", preferenceManager.getThemeModeAsync())

        viewModel.setThemeMode(AppThemeMode.SYSTEM).join()
        advanceUntilIdle()
        assertEquals(AppThemeMode.SYSTEM, viewModel.themeMode.value)
        assertEquals("SYSTEM", preferenceManager.themeModeFlow.first())
        assertEquals("SYSTEM", preferenceManager.getThemeModeAsync())
    }

    @Test
    fun testDynamicColorPersistence() = runTest(testDispatcher) {
        assertFalse(viewModel.dynamicColor.value)

        viewModel.setDynamicColor(true).join()
        advanceUntilIdle()
        assertTrue(viewModel.dynamicColor.value)
        assertTrue(preferenceManager.dynamicColorFlow.first())
        assertTrue(preferenceManager.isDynamicColorEnabledAsync())

        viewModel.setDynamicColor(false).join()
        advanceUntilIdle()
        assertFalse(viewModel.dynamicColor.value)
        assertFalse(preferenceManager.dynamicColorFlow.first())
        assertFalse(preferenceManager.isDynamicColorEnabledAsync())
    }

    @Test
    fun testTmdbApiKeyPersistence() = runTest(testDispatcher) {
        assertEquals("", viewModel.tmdbApiKey.value)

        viewModel.setTmdbApiKey("test_key_123").join()
        advanceUntilIdle()
        assertEquals("test_key_123", viewModel.tmdbApiKey.value)
        assertEquals("test_key_123", preferenceManager.tmdbApiKeyFlow.first())
        assertEquals("test_key_123", preferenceManager.getTmdbApiKeyAsync())
    }
}
