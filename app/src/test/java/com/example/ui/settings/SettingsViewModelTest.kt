package com.example.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.PreferenceManager
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsViewModelTest {

    private lateinit var preferenceManager: PreferenceManager
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        preferenceManager = PreferenceManager(context)
        preferenceManager.setThemeMode("DARK")
        preferenceManager.setDynamicColorEnabled(false)
        preferenceManager.setTmdbApiKey("")
        viewModel = SettingsViewModel(preferenceManager)
    }

    @Test
    fun testThemeModePersistence() = runBlocking {
        assertEquals(AppThemeMode.DARK, viewModel.themeMode.value)

        viewModel.setThemeMode(AppThemeMode.LIGHT)
        assertEquals(AppThemeMode.LIGHT, viewModel.themeMode.value)
        preferenceManager.themeModeFlow.first { it == "LIGHT" }
        assertEquals("LIGHT", preferenceManager.getThemeMode())

        viewModel.setThemeMode(AppThemeMode.SYSTEM)
        assertEquals(AppThemeMode.SYSTEM, viewModel.themeMode.value)
        preferenceManager.themeModeFlow.first { it == "SYSTEM" }
        assertEquals("SYSTEM", preferenceManager.getThemeMode())
    }

    @Test
    fun testDynamicColorPersistence() = runBlocking {
        assertFalse(viewModel.dynamicColor.value)

        viewModel.setDynamicColor(true)
        assertTrue(viewModel.dynamicColor.value)
        preferenceManager.dynamicColorFlow.first { it }
        assertTrue(preferenceManager.isDynamicColorEnabled())

        viewModel.setDynamicColor(false)
        assertFalse(viewModel.dynamicColor.value)
        preferenceManager.dynamicColorFlow.first { !it }
        assertFalse(preferenceManager.isDynamicColorEnabled())
    }

    @Test
    fun testTmdbApiKeyPersistence() = runBlocking {
        assertEquals("", viewModel.tmdbApiKey.value)

        viewModel.setTmdbApiKey("test_key_123")
        assertEquals("test_key_123", viewModel.tmdbApiKey.value)
        preferenceManager.tmdbApiKeyFlow.first { it == "test_key_123" }
        assertEquals("test_key_123", preferenceManager.getTmdbApiKey())
    }
}
