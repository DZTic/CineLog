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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SearchHistoryTest {

    private lateinit var preferenceManager: PreferenceManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        preferenceManager = PreferenceManager(context)
        preferenceManager.setSearchHistory(emptyList())
        preferenceManager.setPinnedSearches(emptyList())
    }

    @Test
    fun searchHistory_addAndCapAtTen() {
        val items = (1..15).map { "Search Query $it" }
        preferenceManager.setSearchHistory(items)
        val saved = preferenceManager.getSearchHistory()

        assertEquals(10, saved.size)
        assertEquals("Search Query 1", saved.first())
        assertEquals("Search Query 10", saved.last())
    }

    @Test
    fun pinnedSearches_addAndRetrieve() {
        val pinned = listOf("Inception", "Interstellar")
        preferenceManager.setPinnedSearches(pinned)
        val saved = preferenceManager.getPinnedSearches()

        assertEquals(2, saved.size)
        assertTrue(saved.contains("Inception"))
        assertTrue(saved.contains("Interstellar"))
    }
}
