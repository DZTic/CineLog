package com.example.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.PreferenceManager
import com.example.ui.settings.SettingsViewModel
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocaleHelperTest {

    private lateinit var preferenceManager: PreferenceManager
    private lateinit var viewModel: SettingsViewModel
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        preferenceManager = PreferenceManager(context)
        preferenceManager.setAppLanguage("system")
        viewModel = SettingsViewModel(preferenceManager)
    }

    @Test
    fun testLanguageSelectionAndPersistence() {
        assertEquals("system", viewModel.appLanguage.value)

        viewModel.setAppLanguage("en")
        assertEquals("en", viewModel.appLanguage.value)
        assertEquals("en", preferenceManager.getAppLanguage())

        viewModel.setAppLanguage("fr")
        assertEquals("fr", viewModel.appLanguage.value)
        assertEquals("fr", preferenceManager.getAppLanguage())
    }

    @Test
    fun testLocaleHelperApplyLanguage() {
        val enContext = LocaleHelper.applyLanguage(context, "en")
        assertEquals("en", enContext.resources.configuration.locales.get(0).language)

        val frContext = LocaleHelper.applyLanguage(context, "fr")
        assertEquals("fr", frContext.resources.configuration.locales.get(0).language)
    }
}
