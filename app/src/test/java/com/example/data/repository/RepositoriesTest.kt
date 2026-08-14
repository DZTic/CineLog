package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.DbCustomList
import com.example.data.DbLogEntry
import com.example.data.DbWatchlist
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
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
class RepositoriesTest {

    private lateinit var database: AppDatabase
    private lateinit var logRepository: LogRepository
    private lateinit var watchlistRepository: WatchlistRepository
    private lateinit var customListRepository: CustomListRepository
    private lateinit var backupRepository: BackupRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        logRepository = LogRepository(database.logDao())
        watchlistRepository = WatchlistRepository(database.watchlistDao())
        customListRepository = CustomListRepository(database.customListDao())
        backupRepository = BackupRepository(
            database.logDao(),
            database.watchlistDao(),
            database.customListDao(),
            database.seasonProgressDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testLogRepositoryOperations() = runBlocking {
        val entry = DbLogEntry(
            id = 1,
            titleId = "movie_1",
            titleType = "FILM",
            titleName = "Inception",
            titlePosterUrl = null,
            dateVue = System.currentTimeMillis(),
            note = 4.5f,
            critique = "Chef d'oeuvre",
            revisionnage = false,
            spoiler = false
        )

        logRepository.saveLogEntry(entry)
        val logs = logRepository.getLogsStream().first()
        assertEquals(1, logs.size)
        assertEquals("Inception", logs[0].titleName)

        val logsForTitle = logRepository.getLogsForTitle("movie_1").first()
        assertEquals(1, logsForTitle.size)

        logRepository.deleteLogById(1)
        val logsAfterDelete = logRepository.getLogsStream().first()
        assertTrue(logsAfterDelete.isEmpty())
    }

    @Test
    fun testWatchlistRepositoryOperations() = runBlocking {
        val item = DbWatchlist(
            titleId = "movie_2",
            titleType = "FILM",
            titleName = "Interstellar",
            titlePosterUrl = null
        )

        assertFalse(watchlistRepository.isInWatchlistStream("movie_2").first())
        watchlistRepository.addToWatchlist(item)
        assertTrue(watchlistRepository.isInWatchlistStream("movie_2").first())

        val list = watchlistRepository.getWatchlistStream().first()
        assertEquals(1, list.size)

        watchlistRepository.removeFromWatchlist("movie_2")
        assertFalse(watchlistRepository.isInWatchlistStream("movie_2").first())
    }

    @Test
    fun testCustomListRepositoryOperations() = runBlocking {
        val listId = customListRepository.createCustomList("SF Favorites", "Best Sci-Fi")
        val lists = customListRepository.getCustomListsStream().first()
        assertEquals(1, lists.size)
        assertEquals("SF Favorites", lists[0].name)

        customListRepository.addTitleToList(listId.toInt(), "movie_1", "FILM", "Inception", null)
        val titles = customListRepository.getTitlesForListStream(listId.toInt()).first()
        assertEquals(1, titles.size)
        assertEquals("Inception", titles[0].titleName)

        customListRepository.deleteCustomList(listId.toInt())
        val listsAfterDelete = customListRepository.getCustomListsStream().first()
        assertTrue(listsAfterDelete.isEmpty())
    }

    @Test
    fun testBackupRepositoryJsonExportAndImport() = runBlocking {
        val entry = DbLogEntry(
            id = 10,
            titleId = "movie_10",
            titleType = "FILM",
            titleName = "Oppenheimer",
            titlePosterUrl = null,
            dateVue = System.currentTimeMillis(),
            note = 5.0f,
            critique = "Masterpiece",
            revisionnage = false,
            spoiler = false
        )
        logRepository.saveLogEntry(entry)

        val json = backupRepository.exportBackupJson()
        assertTrue(json.contains("Oppenheimer"))

        logRepository.deleteLogById(10)
        assertTrue(logRepository.getLogsStream().first().isEmpty())

        val summary = backupRepository.importBackup(json)
        assertEquals(1, summary.logsCount)
        assertEquals(1, logRepository.getLogsStream().first().size)
    }
}
