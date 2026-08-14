package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
class PreferenceManagerTest {

    private lateinit var preferenceManager: PreferenceManager

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        preferenceManager = PreferenceManager(context)
    }

    @Test
    fun testTmdbApiKey() = runTest {
        preferenceManager.updateTmdbApiKey("my_test_tmdb_key")
        assertEquals("my_test_tmdb_key", preferenceManager.tmdbApiKeyFlow.first())
        assertEquals("my_test_tmdb_key", preferenceManager.getTmdbApiKey())
    }

    @Test
    fun testOnboardingDismissal() = runTest {
        preferenceManager.updateHasDismissedOnboarding(true)
        assertTrue(preferenceManager.hasDismissedOnboardingFlow.first())
        assertTrue(preferenceManager.hasDismissedOnboarding())

        preferenceManager.updateHasDismissedOnboarding(false)
        assertFalse(preferenceManager.hasDismissedOnboardingFlow.first())
        assertFalse(preferenceManager.hasDismissedOnboarding())
    }

    @Test
    fun testThemeAndDynamicColors() = runTest {
        preferenceManager.updateThemeMode("LIGHT")
        assertEquals("LIGHT", preferenceManager.themeModeFlow.first())
        assertEquals("LIGHT", preferenceManager.getThemeMode())

        preferenceManager.updateDynamicColorEnabled(true)
        assertTrue(preferenceManager.dynamicColorFlow.first())
        assertTrue(preferenceManager.isDynamicColorEnabled())
    }

    @Test
    fun testLanguage() = runTest {
        preferenceManager.updateAppLanguage("en")
        assertEquals("en", preferenceManager.appLanguageFlow.first())
        assertEquals("en", preferenceManager.getAppLanguage())
    }

    @Test
    fun testHomeViewModeAndCollapsedCategories() = runTest {
        preferenceManager.updateHomeViewMode("GRID")
        assertEquals("GRID", preferenceManager.homeViewModeFlow.first())

        preferenceManager.updateHomeCollapsedCategories(setOf("FILM", "ANIME"))
        val categories = preferenceManager.homeCollapsedCategoriesFlow.first()
        assertEquals(2, categories.size)
        assertTrue(categories.contains("FILM"))
        assertTrue(categories.contains("ANIME"))
    }

    @Test
    fun testWatchlistPreferences() = runTest {
        preferenceManager.updateWatchlistViewMode("LIST")
        assertEquals("LIST", preferenceManager.watchlistViewModeFlow.first())

        preferenceManager.updateWatchlistSort("TITLE_AZ")
        assertEquals("TITLE_AZ", preferenceManager.watchlistSortFlow.first())

        preferenceManager.updateWatchlistTypeFilter("FILM")
        assertEquals("FILM", preferenceManager.watchlistTypeFilterFlow.first())

        preferenceManager.updateWatchlistGenreFilter("Action")
        assertEquals("Action", preferenceManager.watchlistGenreFilterFlow.first())

        preferenceManager.updateWatchlistYearFilter("2024")
        assertEquals("2024", preferenceManager.watchlistYearFilterFlow.first())
    }

    @Test
    fun testSearchHistoryAndPinnedSearches() = runTest {
        val history = listOf("Inception", "Interstellar", "Tenet")
        preferenceManager.updateSearchHistory(history)
        assertEquals(history, preferenceManager.searchHistoryFlow.first())

        val pinned = listOf("Oppenheimer", "Dune")
        preferenceManager.updatePinnedSearches(pinned)
        assertEquals(pinned, preferenceManager.pinnedSearchesFlow.first())
    }
}
