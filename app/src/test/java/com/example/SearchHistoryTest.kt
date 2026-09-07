package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.PreferenceManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

import kotlinx.coroutines.test.runTest

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SearchHistoryTest {

    private lateinit var preferenceManager: PreferenceManager

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        preferenceManager = PreferenceManager(context)
        preferenceManager.updateSearchHistory(emptyList())
        preferenceManager.updatePinnedSearches(emptyList())
    }

    @Test
    fun searchHistory_addAndCapAtTen() = runTest {
        val items = (1..15).map { "Search Query $it" }
        preferenceManager.updateSearchHistory(items)
        val saved = preferenceManager.getSearchHistoryAsync()

        assertEquals(10, saved.size)
        assertEquals("Search Query 1", saved.first())
        assertEquals("Search Query 10", saved.last())
    }

    @Test
    fun pinnedSearches_addAndRetrieve() = runTest {
        val pinned = listOf("Inception", "Interstellar")
        preferenceManager.updatePinnedSearches(pinned)
        val saved = preferenceManager.getPinnedSearchesAsync()

        assertEquals(2, saved.size)
        assertTrue(saved.contains("Inception"))
        assertTrue(saved.contains("Interstellar"))
    }
}
