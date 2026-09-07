package com.example.ui.watchlist

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.ui.WatchlistSortOrder
import com.example.ui.components.GroupedDisplay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WatchlistViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var database: AppDatabase
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var repository: Repository
    private lateinit var viewModel: WatchlistViewModel

    @Before
    fun setup() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        preferenceManager = PreferenceManager(context)
        preferenceManager.updateWatchlistTypeFilter("")
        preferenceManager.updateWatchlistGenreFilter("")
        preferenceManager.updateWatchlistYearFilter("")
        preferenceManager.updateWatchlistSort("DATE_ADDED")
        advanceUntilIdle()

        repository = Repository(
            logDao = database.logDao(),
            watchlistDao = database.watchlistDao(),
            customListDao = database.customListDao(),
            seasonProgressDao = database.seasonProgressDao(),
            collectionCacheDao = database.collectionCacheDao(),
            sagaSizeDao = database.sagaSizeDao(),
            titleMetaCacheDao = database.titleMetaCacheDao(),
            preferenceManager = preferenceManager,
            context = context
        )
        viewModel = WatchlistViewModel(
            repository = repository,
            preferenceManager = preferenceManager,
            defaultDispatcher = testDispatcher,
            sharingStarted = SharingStarted.Eagerly
        )
        advanceUntilIdle()
    }

    @After
    fun tearDown() = runTest(testDispatcher) {
        preferenceManager.updateWatchlistTypeFilter("")
        preferenceManager.updateWatchlistGenreFilter("")
        preferenceManager.updateWatchlistYearFilter("")
        preferenceManager.updateWatchlistSort("DATE_ADDED")
        advanceUntilIdle()
        database.close()
        Dispatchers.resetMain()
    }

    private fun createWatchlistEntry(
        id: String,
        name: String,
        type: String = "FILM",
        year: String? = "2024",
        genres: String? = "Action, Sci-Fi",
        voteAverage: Float? = 8.0f,
        dateAdded: Long = System.currentTimeMillis(),
        collectionId: Int? = null,
        collectionName: String? = null,
        collectionPosterUrl: String? = null
    ): DbWatchlist {
        return DbWatchlist(
            titleId = id,
            titleName = name,
            titleType = type,
            titlePosterUrl = "https://image.tmdb.org/$id.jpg",
            dateAdded = dateAdded,
            titleYear = year,
            titleGenres = genres,
            titleVoteAverage = voteAverage,
            collectionId = collectionId,
            collectionName = collectionName,
            collectionPosterUrl = collectionPosterUrl
        )
    }

    @Test
    fun testInitialUiStateWithItems() = runTest(testDispatcher) {
        val movie1 = createWatchlistEntry("m1", "Inception", "FILM", "2010", "Action, Sci-Fi", 8.8f, 1000L)
        val movie2 = createWatchlistEntry("m2", "Interstellar", "FILM", "2014", "Adventure, Drama, Sci-Fi", 8.7f, 2000L)
        val serie1 = createWatchlistEntry("s1", "Breaking Bad", "SERIE", "2008", "Crime, Drama", 9.5f, 3000L)

        database.watchlistDao().insertWatchlist(movie1)
        database.watchlistDao().insertWatchlist(movie2)
        database.watchlistDao().insertWatchlist(serie1)
        advanceUntilIdle()

        val state = withTimeout(5_000) {
            viewModel.uiState.filter { !it.isLoading && it.totalUnwatchedCount == 3 }.first()
        }
        assertEquals(3, state.totalUnwatchedCount)
        assertEquals(3, state.filteredCount)
        assertFalse(state.isWatchlistEmpty)
        assertFalse(state.isFilteredEmpty)

        assertEquals(2, state.categoryCounts[TitleType.FILM])
        assertEquals(1, state.categoryCounts[TitleType.SERIE])
        assertEquals(0, state.categoryCounts[TitleType.ANIME] ?: 0)
    }

    @Test
    fun testWatchedTitlesAreExcludedFromUiState() = runTest(testDispatcher) {
        val movie1 = createWatchlistEntry("m1", "Inception")
        val movie2 = createWatchlistEntry("m2", "Interstellar")
        database.watchlistDao().insertWatchlist(movie1)
        database.watchlistDao().insertWatchlist(movie2)

        database.logDao().insertLog(
            DbLogEntry(
                id = 1,
                titleId = "m1",
                titleType = "FILM",
                titleName = "Inception",
                titlePosterUrl = null,
                dateVue = System.currentTimeMillis(),
                note = 5.0f,
                critique = "Chef d'oeuvre",
                revisionnage = false,
                spoiler = false
            )
        )
        advanceUntilIdle()

        val state = withTimeout(5_000) {
            viewModel.uiState.filter {
                !it.isLoading && viewModel.allWatchlist.value.size == 2 && viewModel.allLogs.value.size == 1 && it.totalUnwatchedCount == 1
            }.first()
        }
        assertEquals(1, state.totalUnwatchedCount)
        assertEquals(1, state.filteredCount)
        assertEquals("m2", state.unwatchedEntries.first().titleId)
    }

    @Test
    fun testSagaGroupingAndBackfillFromCache() = runTest(testDispatcher) {
        val hp1 = createWatchlistEntry("hp1", "Harry Potter 1", "FILM", collectionId = null)
        val hp2 = createWatchlistEntry("hp2", "Harry Potter 2", "FILM", collectionId = null)
        database.watchlistDao().insertWatchlist(hp1)
        database.watchlistDao().insertWatchlist(hp2)

        database.collectionCacheDao().upsert(DbCollectionCache("hp1", 124, "Harry Potter Collection", "https://image.tmdb.org/hp.jpg"))
        database.collectionCacheDao().upsert(DbCollectionCache("hp2", 124, "Harry Potter Collection", "https://image.tmdb.org/hp.jpg"))
        advanceUntilIdle()

        val state = withTimeout(5_000) {
            viewModel.uiState.filter {
                !it.isLoading && (it.displayItemsByType[TitleType.FILM]?.size ?: 0) == 1
            }.first()
        }

        val filmItems = state.displayItemsByType[TitleType.FILM]
        assertNotNull(filmItems)
        assertEquals(1, filmItems!!.size)

        val grouped = filmItems.first() as GroupedDisplay.Grouped
        assertEquals(124, grouped.group.collectionId)
        assertEquals("Harry Potter Collection", grouped.group.collectionName)
        assertEquals(2, grouped.group.items.size)
    }

    @Test
    fun testSearchQueryFiltering() = runTest(testDispatcher) {
        val movie1 = createWatchlistEntry("m1", "The Dark Knight", "FILM")
        val movie2 = createWatchlistEntry("m2", "Spider-Man", "FILM")
        val anime1 = createWatchlistEntry("a1", "Attack on Titan", "ANIME")
        database.watchlistDao().insertWatchlist(movie1)
        database.watchlistDao().insertWatchlist(movie2)
        database.watchlistDao().insertWatchlist(anime1)
        advanceUntilIdle()

        withTimeout(5_000) {
            viewModel.uiState.filter { !it.isLoading && it.totalUnwatchedCount == 3 }.first()
        }

        viewModel.setSearchQuery("dark")
        advanceUntilIdle()
        val searchState = withTimeout(5_000) {
            viewModel.uiState.filter { it.filteredCount == 1 }.first()
        }
        assertEquals(1, searchState.filteredCount)
        assertEquals(3, searchState.totalUnwatchedCount)

        val items = searchState.displayItemsByType[TitleType.FILM]
        assertEquals(1, items?.size)
        val single = items?.first() as GroupedDisplay.Single
        assertEquals("The Dark Knight", single.item.titleName)
    }

    @Test
    fun testFilterByTypeGenreAndYear() = runTest(testDispatcher) {
        val film2020 = createWatchlistEntry("f1", "Film 2020", "FILM", year = "2020", genres = "Comedy")
        val film2024 = createWatchlistEntry("f2", "Film 2024", "FILM", year = "2024", genres = "Horror, Sci-Fi")
        val serie2024 = createWatchlistEntry("s1", "Serie 2024", "SERIE", year = "2024", genres = "Drama")
        database.watchlistDao().insertWatchlist(film2020)
        database.watchlistDao().insertWatchlist(film2024)
        database.watchlistDao().insertWatchlist(serie2024)
        advanceUntilIdle()

        withTimeout(5_000) {
            viewModel.uiState.filter { !it.isLoading && it.totalUnwatchedCount == 3 }.first()
        }

        viewModel.setWatchlistTypeFilter(TitleType.FILM)
        advanceUntilIdle()
        val filmState = withTimeout(5_000) {
            viewModel.uiState.filter { it.filteredCount == 2 }.first()
        }
        assertEquals(listOf("Comedy", "Horror", "Sci-Fi"), filmState.availableGenres)
        assertEquals(listOf("2024", "2020"), filmState.availableYears)

        viewModel.setWatchlistGenreFilter("Comedy")
        advanceUntilIdle()
        val comedyState = withTimeout(5_000) {
            viewModel.uiState.filter { it.filteredCount == 1 }.first()
        }
        assertEquals(1, comedyState.filteredCount)

        viewModel.setWatchlistYearFilter("2020")
        advanceUntilIdle()
        val yearState = withTimeout(5_000) {
            viewModel.uiState.filter { it.filteredCount == 1 }.first()
        }
        assertEquals(1, yearState.filteredCount)

        viewModel.setWatchlistYearFilter("2024")
        advanceUntilIdle()
        val emptyState = withTimeout(5_000) {
            viewModel.uiState.filter { it.filteredCount == 0 }.first()
        }
        assertTrue(emptyState.isFilteredEmpty)
        assertFalse(emptyState.isWatchlistEmpty)
    }

    @Test
    fun testSortOrders() = runTest(testDispatcher) {
        val filmA = createWatchlistEntry("fa", "Alpha", "FILM", year = "2010", voteAverage = 6.0f, dateAdded = 100L)
        val filmB = createWatchlistEntry("fb", "Beta", "FILM", year = "2024", voteAverage = 9.0f, dateAdded = 500L)
        val filmC = createWatchlistEntry("fc", "Gamma", "FILM", year = "2018", voteAverage = 7.5f, dateAdded = 300L)

        database.watchlistDao().insertWatchlist(filmA)
        database.watchlistDao().insertWatchlist(filmB)
        database.watchlistDao().insertWatchlist(filmC)
        advanceUntilIdle()

        withTimeout(5_000) {
            viewModel.uiState.filter { !it.isLoading && it.totalUnwatchedCount == 3 }.first()
        }

        // 1. DATE_ADDED (descending): Beta (500), Gamma (300), Alpha (100)
        viewModel.setWatchlistSort(WatchlistSortOrder.DATE_ADDED)
        advanceUntilIdle()
        val dateSortedState = withTimeout(5_000) {
            viewModel.uiState.filter {
                val films = it.displayItemsByType[TitleType.FILM]
                films?.size == 3 && (films.first() as? GroupedDisplay.Single)?.item?.titleName == "Beta"
            }.first()
        }
        val dateSorted = dateSortedState.displayItemsByType[TitleType.FILM]!!
            .map { (it as GroupedDisplay.Single).item.titleName }
        assertEquals(listOf("Beta", "Gamma", "Alpha"), dateSorted)

        // 2. TITLE_AZ (ascending): Alpha, Beta, Gamma
        viewModel.setWatchlistSort(WatchlistSortOrder.TITLE_AZ)
        advanceUntilIdle()
        val azSortedState = withTimeout(5_000) {
            viewModel.uiState.filter {
                val films = it.displayItemsByType[TitleType.FILM]
                films?.size == 3 && (films.first() as? GroupedDisplay.Single)?.item?.titleName == "Alpha"
            }.first()
        }
        val azSorted = azSortedState.displayItemsByType[TitleType.FILM]!!
            .map { (it as GroupedDisplay.Single).item.titleName }
        assertEquals(listOf("Alpha", "Beta", "Gamma"), azSorted)

        // 3. RELEASE_YEAR (descending): Beta (2024), Gamma (2018), Alpha (2010)
        viewModel.setWatchlistSort(WatchlistSortOrder.RELEASE_YEAR)
        advanceUntilIdle()
        val yearSortedState = withTimeout(5_000) {
            viewModel.uiState.filter {
                val films = it.displayItemsByType[TitleType.FILM]
                films?.size == 3 && (films.first() as? GroupedDisplay.Single)?.item?.titleName == "Beta" &&
                    (films.last() as? GroupedDisplay.Single)?.item?.titleName == "Alpha"
            }.first()
        }
        val yearSorted = yearSortedState.displayItemsByType[TitleType.FILM]!!
            .map { (it as GroupedDisplay.Single).item.titleName }
        assertEquals(listOf("Beta", "Gamma", "Alpha"), yearSorted)

        // 4. COMMUNITY_RATING (descending): Beta (9.0), Gamma (7.5), Alpha (6.0)
        viewModel.setWatchlistSort(WatchlistSortOrder.COMMUNITY_RATING)
        advanceUntilIdle()
        val ratingSortedState = withTimeout(5_000) {
            viewModel.uiState.filter {
                val films = it.displayItemsByType[TitleType.FILM]
                films?.size == 3 && (films.first() as? GroupedDisplay.Single)?.item?.titleName == "Beta" &&
                    (films.last() as? GroupedDisplay.Single)?.item?.titleName == "Alpha"
            }.first()
        }
        val ratingSorted = ratingSortedState.displayItemsByType[TitleType.FILM]!!
            .map { (it as GroupedDisplay.Single).item.titleName }
        assertEquals(listOf("Beta", "Gamma", "Alpha"), ratingSorted)
    }

    @Test
    fun testClearFiltersRestoresFullList() = runTest(testDispatcher) {
        val film1 = createWatchlistEntry("f1", "Film 1", "FILM", year = "2020", genres = "Action")
        val film2 = createWatchlistEntry("f2", "Film 2", "FILM", year = "2024", genres = "Comedy")
        database.watchlistDao().insertWatchlist(film1)
        database.watchlistDao().insertWatchlist(film2)
        advanceUntilIdle()

        withTimeout(5_000) {
            viewModel.uiState.filter { !it.isLoading && it.totalUnwatchedCount == 2 }.first()
        }

        viewModel.setSearchQuery("Film 1")
        viewModel.setWatchlistTypeFilter(TitleType.FILM)
        viewModel.setWatchlistGenreFilter("Action")
        viewModel.setWatchlistYearFilter("2020")
        advanceUntilIdle()

        val filtered = withTimeout(5_000) {
            viewModel.uiState.filter { it.filteredCount == 1 }.first()
        }
        assertEquals(1, filtered.filteredCount)

        viewModel.clearWatchlistFilters()
        advanceUntilIdle()
        val resetState = withTimeout(5_000) {
            viewModel.uiState.filter { it.filteredCount == 2 }.first()
        }
        assertEquals(2, resetState.filteredCount)
    }
}
