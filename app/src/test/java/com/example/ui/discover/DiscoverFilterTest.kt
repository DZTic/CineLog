package com.example.ui.discover

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DiscoverFilterTest {

    private lateinit var database: AppDatabase
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var repository: Repository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        preferenceManager = PreferenceManager(context)
        repository = Repository(
            logDao = database.logDao(),
            watchlistDao = database.watchlistDao(),
            customListDao = database.customListDao(),
            seasonProgressDao = database.seasonProgressDao(),
            collectionCacheDao = database.collectionCacheDao(),
            sagaSizeDao = database.sagaSizeDao(),
            preferenceManager = preferenceManager,
            context = context
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testGetWatchedTitleIdsReturnsCorrectSet() = runBlocking {
        database.logDao().insertLog(
            DbLogEntry(
                titleId = "movie_100",
                titleType = "FILM",
                titleName = "Film 100",
                titlePosterUrl = null,
                dateVue = System.currentTimeMillis(),
                note = 4f,
                critique = "",
                revisionnage = false,
                spoiler = false
            )
        )
        database.logDao().insertLog(
            DbLogEntry(
                titleId = "tv_200",
                titleType = "SERIE",
                titleName = "Serie 200",
                titlePosterUrl = null,
                dateVue = System.currentTimeMillis(),
                note = 5f,
                critique = "",
                revisionnage = false,
                spoiler = false
            )
        )

        val watched = repository.getWatchedTitleIds()
        assertEquals(2, watched.size)
        assertTrue(watched.contains("movie_100"))
        assertTrue(watched.contains("tv_200"))
        assertFalse(watched.contains("movie_300"))
    }

    @Test
    fun testDiscoverPagingSourceFiltersWatchedTitles() = runBlocking {
        // Enregistrer le premier film comme déjà vu
        // (getFallbackFilms() contient movie_101 "Inception", etc.)
        database.logDao().insertLog(
            DbLogEntry(
                titleId = "movie_101",
                titleType = "FILM",
                titleName = "Inception",
                titlePosterUrl = null,
                dateVue = System.currentTimeMillis(),
                note = 5f,
                critique = "",
                revisionnage = false,
                spoiler = false
            )
        )

        val pagingSource = DiscoverPagingSource(repository, TitleType.FILM)
        val result = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = 1,
                loadSize = 20,
                placeholdersEnabled = false
            )
        )

        assertTrue(result is PagingSource.LoadResult.Page)
        val page = result as PagingSource.LoadResult.Page<Int, CineTitle>
        // Aucun élément retourné ne doit être "movie_101"
        assertFalse(page.data.any { it.id == "movie_101" })
    }

    @Test
    fun testGetUnwatchedTrendingOrPopularFiltersAndReplaces() = runBlocking {
        database.logDao().insertLog(
            DbLogEntry(
                titleId = "movie_101",
                titleType = "FILM",
                titleName = "Inception",
                titlePosterUrl = null,
                dateVue = System.currentTimeMillis(),
                note = 5f,
                critique = "",
                revisionnage = false,
                spoiler = false
            )
        )

        val unwatched = repository.getUnwatchedTrendingOrPopular(TitleType.FILM, targetCount = 5)
        assertFalse(unwatched.any { it.id == "movie_101" })
    }
}
