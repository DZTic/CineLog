package com.example.ui.home

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.DbLogEntry
import com.example.data.PreferenceManager
import com.example.data.Repository
import com.example.util.FakeNetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
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
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var database: AppDatabase
    private lateinit var repository: Repository
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var fakeNetworkMonitor: FakeNetworkMonitor

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
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
            titleMetaCacheDao = database.titleMetaCacheDao(),
            preferenceManager = preferenceManager,
            context = context
        )
        fakeNetworkMonitor = FakeNetworkMonitor(initialOnline = true)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    private fun createTestLogEntry(id: Int = 1, titleId: String = "film_1"): DbLogEntry {
        return DbLogEntry(
            id = id,
            titleId = titleId,
            titleType = "FILM",
            titleName = "Inception",
            titlePosterUrl = "/inception.jpg",
            dateVue = 1704067200000L,
            note = 4.5f,
            critique = "Chef d'oeuvre",
            revisionnage = false,
            spoiler = false
        )
    }

    @Test
    fun init_doesNotLoadSuggestionsAutomatically() = runTest(testDispatcher) {
        val viewModel = HomeViewModel(repository, preferenceManager, fakeNetworkMonitor)
        advanceUntilIdle()

        // Au démarrage, même avec une base vide, les suggestions ne doivent pas être chargées automatiquement
        assertTrue(viewModel.trendingFilms.value.isEmpty())
        assertTrue(viewModel.trendingSeries.value.isEmpty())
        assertFalse(viewModel.isLoadingSuggestions.value)
    }

    @Test
    fun loadSuggestionsIfNeeded_withNonEmptyLogs_doesNotLoadSuggestions() = runTest(testDispatcher) {
        database.logDao().insertLog(createTestLogEntry())

        val viewModel = HomeViewModel(repository, preferenceManager, fakeNetworkMonitor)
        advanceUntilIdle()

        viewModel.loadSuggestionsIfNeeded()
        advanceUntilIdle()

        // L'utilisateur ayant déjà des visionnages, aucune suggestion ne doit être chargée
        assertTrue(viewModel.trendingFilms.value.isEmpty())
        assertTrue(viewModel.trendingSeries.value.isEmpty())
        assertFalse(viewModel.isLoadingSuggestions.value)
    }

    @Test
    fun networkReconnect_doesNotTriggerSuggestions_whenLogsNotEmpty() = runTest(testDispatcher) {
        database.logDao().insertLog(createTestLogEntry())

        fakeNetworkMonitor.setOnline(false)
        val viewModel = HomeViewModel(repository, preferenceManager, fakeNetworkMonitor)
        advanceUntilIdle()

        // Simulation d'une reconnexion réseau
        fakeNetworkMonitor.setOnline(true)
        advanceUntilIdle()

        assertTrue(viewModel.trendingFilms.value.isEmpty())
        assertTrue(viewModel.trendingSeries.value.isEmpty())
        assertFalse(viewModel.isLoadingSuggestions.value)
    }

    @Test
    fun loadSuggestionsIfNeeded_withEmptyLogs_attemptsLoadAndResetsLoadingState() = runTest(testDispatcher) {
        val viewModel = HomeViewModel(repository, preferenceManager, fakeNetworkMonitor)
        advanceUntilIdle()

        viewModel.loadSuggestionsIfNeeded()
        advanceUntilIdle()

        // Le chargement paresseux s'est exécuté et l'indicateur d'attente s'est terminé
        assertFalse(viewModel.isLoadingSuggestions.value)
    }
}
