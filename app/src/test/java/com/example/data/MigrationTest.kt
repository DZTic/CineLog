package com.example.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MigrationTest {

    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        listOf(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate8To9() {
        var db = helper.createDatabase(TEST_DB, 8)
        db.execSQL("INSERT INTO watchlist (titleId, titleType, titleName, titlePosterUrl, dateAdded, collectionId) VALUES ('movie_1', 'FILM', 'Inception', null, 1000, 10)")
        db.execSQL("INSERT INTO title_meta_cache (titleId, genres, studioOrDirector, voteAverage, runtime) VALUES ('movie_1', 'Action,Sci-Fi', 'Nolan', 4.5, 148)")
        db.execSQL("INSERT INTO collection_cache (titleId, collectionId, collectionName, collectionPosterUrl) VALUES ('movie_1', 10, 'Inception Saga', null)")
        db.execSQL("INSERT INTO custom_list_titles (id, listId, titleId, titleType, titleName, titlePosterUrl, orderIndex) VALUES (1, 1, 'movie_1', 'FILM', 'Inception', null, 0)")
        db.close()

        db = helper.runMigrationsAndValidate(TEST_DB, 9, true, MIGRATION_8_9)

        val cursorMeta = db.query("SELECT titleId, genres, cachedAt FROM title_meta_cache WHERE titleId = 'movie_1'")
        assertTrue(cursorMeta.moveToFirst())
        assertEquals("movie_1", cursorMeta.getString(0))
        assertEquals("Action,Sci-Fi", cursorMeta.getString(1))
        assertEquals(0L, cursorMeta.getLong(2))
        cursorMeta.close()

        val cursorWatchlist = db.query("SELECT titleId, collectionId FROM watchlist WHERE titleId = 'movie_1'")
        assertTrue(cursorWatchlist.moveToFirst())
        assertEquals("movie_1", cursorWatchlist.getString(0))
        assertEquals(10, cursorWatchlist.getInt(1))
        cursorWatchlist.close()

        val cursorCollection = db.query("SELECT titleId, cachedAt FROM collection_cache WHERE titleId = 'movie_1'")
        assertTrue(cursorCollection.moveToFirst())
        assertEquals("movie_1", cursorCollection.getString(0))
        assertEquals(0L, cursorCollection.getLong(1))
        cursorCollection.close()
    }

    @Test
    fun migrate9To10() {
        var db = helper.createDatabase(TEST_DB, 9)
        db.execSQL(
            """
            INSERT INTO log_entries (id, titleId, titleType, titleName, titlePosterUrl, dateVue, note, critique, revisionnage, spoiler, collectionId, collectionName, collectionPosterUrl)
            VALUES (1, 'movie_1', 'FILM', 'Inception', null, 1700000000000, 4.5, 'Super film', 0, 0, 10, 'Inception Saga', null)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO watchlist (titleId, titleType, titleName, titlePosterUrl, dateAdded, titleYear, titleGenres, titleVoteAverage, collectionId, collectionName, collectionPosterUrl)
            VALUES ('movie_2', 'FILM', 'Interstellar', null, 1690000000000, '2014', 'Sci-Fi', 4.8, null, null, null)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO collection_cache (titleId, collectionId, collectionName, collectionPosterUrl, cachedAt)
            VALUES ('movie_1', 10, 'Inception Saga', null, 1680000000000)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO title_meta_cache (titleId, genres, studioOrDirector, voteAverage, runtime, cachedAt)
            VALUES ('movie_1', 'Action,Sci-Fi', 'Nolan', 4.5, 148, 1680000000000)
            """.trimIndent()
        )
        db.close()

        db = helper.runMigrationsAndValidate(TEST_DB, 10, true, MIGRATION_9_10)

        // Verifier que les donnees sont preservees
        val cursorLog = db.query("SELECT id, titleId, dateVue, note FROM log_entries WHERE id = 1 ORDER BY dateVue DESC")
        assertTrue(cursorLog.moveToFirst())
        assertEquals(1, cursorLog.getInt(0))
        assertEquals("movie_1", cursorLog.getString(1))
        assertEquals(1700000000000L, cursorLog.getLong(2))
        assertEquals(4.5f, cursorLog.getFloat(3), 0.01f)
        cursorLog.close()

        val cursorWatchlist = db.query("SELECT titleId, dateAdded FROM watchlist WHERE titleId = 'movie_2' ORDER BY dateAdded DESC")
        assertTrue(cursorWatchlist.moveToFirst())
        assertEquals("movie_2", cursorWatchlist.getString(0))
        assertEquals(1690000000000L, cursorWatchlist.getLong(1))
        cursorWatchlist.close()

        val cursorCollection = db.query("SELECT titleId, cachedAt FROM collection_cache WHERE cachedAt < 1700000000000")
        assertTrue(cursorCollection.moveToFirst())
        assertEquals("movie_1", cursorCollection.getString(0))
        assertEquals(1680000000000L, cursorCollection.getLong(1))
        cursorCollection.close()

        val cursorMeta = db.query("SELECT titleId, cachedAt FROM title_meta_cache WHERE cachedAt < 1700000000000")
        assertTrue(cursorMeta.moveToFirst())
        assertEquals("movie_1", cursorMeta.getString(0))
        assertEquals(1680000000000L, cursorMeta.getLong(1))
        cursorMeta.close()

        // Verifier la presence explicite des nouveaux index SQLite
        val cursorIndexes = db.query(
            """
            SELECT name FROM sqlite_master
            WHERE type = 'index' AND name IN (
                'index_log_entries_dateVue',
                'index_watchlist_dateAdded',
                'index_collection_cache_cachedAt',
                'index_title_meta_cache_cachedAt'
            )
            """.trimIndent()
        )
        assertEquals(4, cursorIndexes.count)
        cursorIndexes.close()
    }
}
