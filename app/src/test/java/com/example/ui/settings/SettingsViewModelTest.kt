package com.example.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.PreferenceManager
import com.example.ui.theme.AppThemeMode
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
    fun testThemeModePersistence() {
        assertEquals(AppThemeMode.DARK, viewModel.themeMode.value)

        viewModel.setThemeMode(AppThemeMode.LIGHT)
        assertEquals(AppThemeMode.LIGHT, viewModel.themeMode.value)
        assertEquals("LIGHT", preferenceManager.getThemeMode())

        viewModel.setThemeMode(AppThemeMode.SYSTEM)
        assertEquals(AppThemeMode.SYSTEM, viewModel.themeMode.value)
        assertEquals("SYSTEM", preferenceManager.getThemeMode())
    }

    @Test
    fun testDynamicColorPersistence() {
        assertFalse(viewModel.dynamicColor.value)

        viewModel.setDynamicColor(true)
        assertTrue(viewModel.dynamicColor.value)
        assertTrue(preferenceManager.isDynamicColorEnabled())

        viewModel.setDynamicColor(false)
        assertFalse(viewModel.dynamicColor.value)
        assertFalse(preferenceManager.isDynamicColorEnabled())
    }

    @Test
    fun testTmdbApiKeyPersistence() {
        assertEquals("", viewModel.tmdbApiKey.value)

        viewModel.setTmdbApiKey("test_key_123")
        assertEquals("test_key_123", viewModel.tmdbApiKey.value)
        assertEquals("test_key_123", preferenceManager.getTmdbApiKey())
    }
}
