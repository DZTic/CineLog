import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupExportTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private class FakeLogDao : LogDao {
        val logs = mutableListOf<DbLogEntry>()
        override fun getAllLogs(): Flow<List<DbLogEntry>> = flowOf(logs)
        override suspend fun getAllLogsList(): List<DbLogEntry> = logs
        override fun getLogsForTitle(titleId: String): Flow<List<DbLogEntry>> = flowOf(emptyList())
        override suspend fun insertLog(entry: DbLogEntry) { logs.add(entry) }
        override suspend fun insertLogs(entries: List<DbLogEntry>) { logs.addAll(entries) }
        override suspend fun deleteLogById(id: Int) { logs.removeAll { it.id == id } }
    }

    private class FakeWatchlistDao : WatchlistDao {
        val watchlist = mutableListOf<DbWatchlist>()
        override fun getAllWatchlist(): Flow<List<DbWatchlist>> = flowOf(watchlist)
        override suspend fun getAllWatchlistList(): List<DbWatchlist> = watchlist
        override fun isInWatchlist(titleId: String): Flow<Boolean> = flowOf(false)
        override suspend fun insertWatchlist(item: DbWatchlist) { watchlist.add(item) }
        override suspend fun insertWatchlists(items: List<DbWatchlist>) { watchlist.addAll(items) }
        override suspend fun deleteFromWatchlist(titleId: String) { watchlist.removeAll { it.titleId == titleId } }
        override suspend fun updateWatchlistMetadata(titleId: String, year: String?, genres: String?, voteAverage: Float?) {}
    }

    private class FakeCustomListDao : CustomListDao {
        val lists = mutableListOf<DbCustomList>()
        val titles = mutableListOf<DbCustomListTitle>()
        override fun getAllCustomLists(): Flow<List<DbCustomList>> = flowOf(lists)
        override suspend fun getAllCustomListsList(): List<DbCustomList> = lists
        override fun getCustomListById(listId: Int): Flow<DbCustomList?> = flowOf(null)
        override suspend fun insertCustomList(list: DbCustomList): Long { lists.add(list); return list.id.toLong() }
        override suspend fun insertCustomLists(lists: List<DbCustomList>) { this.lists.addAll(lists) }
        override suspend fun deleteCustomListById(listId: Int) { lists.removeAll { it.id == listId } }
        override fun getCustomListTitles(listId: Int): Flow<List<DbCustomListTitle>> = flowOf(emptyList())
        override suspend fun getAllCustomListTitlesList(): List<DbCustomListTitle> = titles
        override suspend fun insertCustomListTitle(title: DbCustomListTitle) { titles.add(title) }
        override suspend fun insertCustomListTitles(titles: List<DbCustomListTitle>) { this.titles.addAll(titles) }
        override suspend fun deleteCustomListTitleById(id: Int) { titles.removeAll { it.id == id } }
        override suspend fun deleteCustomListTitlesForList(listId: Int) { titles.removeAll { it.listId == listId } }
        override suspend fun updateCustomListTitleOrder(id: Int, newOrderIndex: Int) {}
    }

    private class FakeSeasonProgressDao : SeasonProgressDao {
        val progressList = mutableListOf<DbSeasonProgress>()
        override fun getForTitle(titleId: String): Flow<List<DbSeasonProgress>> = flowOf(emptyList())
        override suspend fun getAllSeasonProgressList(): List<DbSeasonProgress> = progressList
        override suspend fun upsert(progress: DbSeasonProgress) { progressList.add(progress) }
        override suspend fun upsertAll(progresses: List<DbSeasonProgress>) { progressList.addAll(progresses) }
        override suspend fun deleteForSeason(titleId: String, seasonNumber: Int) {
            progressList.removeAll { it.titleId == titleId && it.seasonNumber == seasonNumber }
        }
    }

    private class FakeCollectionCacheDao : CollectionCacheDao {
        override fun getAll(): Flow<List<DbCollectionCache>> = flowOf(emptyList())
        override suspend fun upsert(entry: DbCollectionCache) {}
    }

    
    private class FakeTitleMetaCacheDao : TitleMetaCacheDao {
        val metaMap = mutableMapOf<String, DbTitleMetaCache>()
        override fun getAllFlow(): Flow<List<DbTitleMetaCache>> = flowOf(metaMap.values.toList())
        override suspend fun getAllList(): List<DbTitleMetaCache> = metaMap.values.toList()
        override suspend fun getByTitleId(titleId: String): DbTitleMetaCache? = metaMap[titleId]
        override suspend fun getByTitleIds(titleIds: List<String>): List<DbTitleMetaCache> =
            titleIds.mapNotNull { metaMap[it] }
        override suspend fun upsert(entry: DbTitleMetaCache) { metaMap[entry.titleId] = entry }
        override suspend fun upsertAll(entries: List<DbTitleMetaCache>) {
            entries.forEach { metaMap[it.titleId] = it }
        }
    }

    private class FakeSagaSizeDao : SagaSizeDao {
        override fun getAll(): Flow<List<DbSagaSize>> = flowOf(emptyList())
        override suspend fun exists(collectionId: Int) = false
        override suspend fun upsert(entry: DbSagaSize) {}
    }

    @Test
    fun testJsonExportAndImport() = runBlocking {
        val logDao = FakeLogDao()
        val watchlistDao = FakeWatchlistDao()
        val customListDao = FakeCustomListDao()
        val seasonProgressDao = FakeSeasonProgressDao()

        logDao.logs.add(
            DbLogEntry(
                id = 1,
                titleId = "movie_100",
                titleType = "FILM",
                titleName = "Inception",
                titlePosterUrl = null,
                dateVue = 1700000000000L,
                note = 4.5f,
                critique = "Masterpiece",
                revisionnage = false,
                spoiler = false
            )
        )

        watchlistDao.watchlist.add(
            DbWatchlist(
                titleId = "tv_200",
                titleType = "SERIE",
                titleName = "Breaking Bad",
                titlePosterUrl = null,
                dateAdded = 1700000000000L
            )
        )

        val repo = Repository(
            logDao = logDao,
            watchlistDao = watchlistDao,
            customListDao = customListDao,
            seasonProgressDao = seasonProgressDao,
            collectionCacheDao = FakeCollectionCacheDao(),
            sagaSizeDao = FakeSagaSizeDao(),
            preferenceManager = PreferenceManager(context)
        )

        val json = repo.exportBackupJson()
        assertTrue(json.contains("Inception"))
        assertTrue(json.contains("Breaking Bad"))

        val targetLogDao = FakeLogDao()
        val targetWatchlistDao = FakeWatchlistDao()
        val targetRepo = Repository(
            logDao = targetLogDao,
            watchlistDao = targetWatchlistDao,
            customListDao = FakeCustomListDao(),
            seasonProgressDao = FakeSeasonProgressDao(),
            collectionCacheDao = FakeCollectionCacheDao(),
            sagaSizeDao = FakeSagaSizeDao(),
            preferenceManager = PreferenceManager(context)
        )

        val summary = targetRepo.importBackup(json)
        assertEquals(1, summary.logsCount)
        assertEquals(1, summary.watchlistCount)

        assertEquals("Inception", targetLogDao.logs[0].titleName)
        assertEquals("Breaking Bad", targetWatchlistDao.watchlist[0].titleName)
    }

    @Test
    fun testCsvExportAndImport() = runBlocking {
        val logDao = FakeLogDao()
        val watchlistDao = FakeWatchlistDao()

        logDao.logs.add(
            DbLogEntry(
                id = 1,
                titleId = "movie_100",
                titleType = "FILM",
                titleName = "Interstellar",
                titlePosterUrl = null,
                dateVue = 1700000000000L,
                note = 5.0f,
                critique = "Incroyable",
                revisionnage = true,
                spoiler = false
            )
        )

        val repo = Repository(
            logDao = logDao,
            watchlistDao = watchlistDao,
            customListDao = FakeCustomListDao(),
            seasonProgressDao = FakeSeasonProgressDao(),
            collectionCacheDao = FakeCollectionCacheDao(),
            sagaSizeDao = FakeSagaSizeDao(),
            preferenceManager = PreferenceManager(context)
        )

        val csv = repo.exportBackupCsv()
        println("GENERATED CSV:\n$csv")
        assertTrue("CSV should contain Interstellar. Output was: $csv", csv.contains("Interstellar"))
        assertTrue("CSV should contain LOGS section. Output was: $csv", csv.contains("LOGS"))

        val targetLogDao = FakeLogDao()
        val targetRepo = Repository(
            logDao = targetLogDao,
            watchlistDao = FakeWatchlistDao(),
            customListDao = FakeCustomListDao(),
            seasonProgressDao = FakeSeasonProgressDao(),
            collectionCacheDao = FakeCollectionCacheDao(),
            sagaSizeDao = FakeSagaSizeDao(),
            preferenceManager = PreferenceManager(context)
        )

        val summary = targetRepo.importBackup(csv)
        assertEquals(1, summary.logsCount)
        assertEquals("Interstellar", targetLogDao.logs[0].titleName)
    }

    @Test
    fun testTitleMetaCachePersistence() = runBlocking {
        val metaDao = FakeTitleMetaCacheDao()
        metaDao.upsert(DbTitleMetaCache(titleId = "movie_100", genres = "Action,Sci-Fi", studioOrDirector = "Christopher Nolan", voteAverage = 8.8f, runtime = 148))

        val repo = Repository(
            logDao = FakeLogDao(),
            watchlistDao = FakeWatchlistDao(),
            customListDao = FakeCustomListDao(),
            seasonProgressDao = FakeSeasonProgressDao(),
            collectionCacheDao = FakeCollectionCacheDao(),
            sagaSizeDao = FakeSagaSizeDao(),
            titleMetaCacheDao = metaDao,
            preferenceManager = PreferenceManager(context)
        )

        repo.loadTitleMetaCacheFromDb()

        val logs = listOf(
            DbLogEntry(id = 1, titleId = "movie_100", titleType = "FILM", titleName = "Inception", titlePosterUrl = null, dateVue = 1700000000000L, note = 5.0f, critique = "", revisionnage = false, spoiler = false)
        )

        val stats = repo.getProfileStats(logs, emptyList())
        assertEquals(1, stats.totalLogs)
        assertEquals(2, stats.topGenres.size)
        assertEquals("Action", stats.topGenres[0].first)
        assertEquals("Christopher Nolan", stats.topDirectorsOrStudios[0].first)
        assertEquals(148, stats.totalRuntimeMinutes)
    }

}
