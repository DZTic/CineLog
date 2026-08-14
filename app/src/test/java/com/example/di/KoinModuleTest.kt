package com.example.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.PreferenceManager
import com.example.data.Repository
import com.example.ui.home.HomeViewModel
import com.example.ui.settings.SettingsViewModel
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.get
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class KoinModuleTest : KoinTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        stopKoin()
        context = ApplicationProvider.getApplicationContext<Context>()
        startKoin {
            androidContext(context)
            modules(appModules)
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun testDatabaseModuleDependencies() {
        val database: AppDatabase = get()
        assertNotNull(database)

        val preferenceManager: PreferenceManager = get()
        assertNotNull(preferenceManager)
    }

    @Test
    fun testRepositoryModuleDependencies() {
        val repository: Repository = get()
        assertNotNull(repository)
    }

    @Test
    fun testViewModelModuleDependencies() {
        val homeViewModel: HomeViewModel = get()
        assertNotNull(homeViewModel)

        val settingsViewModel: SettingsViewModel = get()
        assertNotNull(settingsViewModel)
    }
}
