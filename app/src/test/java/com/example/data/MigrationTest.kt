package com.example.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        listOf(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun migrate5To6() {
        var db = helper.createDatabase(TEST_DB, 5)
        db.execSQL("INSERT INTO watchlist (titleId, titleType, titleName, titlePosterUrl, dateAdded, collectionId, collectionName, collectionPosterUrl) VALUES ('movie_1', 'FILM', 'Inception', null, 1000, 10, 'Inception Saga', null)")
        db.close()

        db = helper.runMigrationsAndValidate(TEST_DB, 6, true, MIGRATION_5_6)

        db.query("SELECT titleName, collectionId, titleYear, titleGenres, titleVoteAverage FROM watchlist WHERE titleId = 'movie_1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Inception", c.getString(0))
            assertEquals(10, c.getInt(1))
            assertTrue(c.isNull(2))
            assertTrue(c.isNull(3))
            assertTrue(c.isNull(4))
        }
    }

    @Test
    fun migrate6To7() {
        var db = helper.createDatabase(TEST_DB, 6)
        insertLog(db, id = 1, titleId = "movie_1")
        db.close()

        db = helper.runMigrationsAndValidate(TEST_DB, 7, true, MIGRATION_6_7)

        assertEquals(1, count(db, "log_entries"))
        // Nouvelle table utilisable avec ses valeurs par défaut.
        db.execSQL("INSERT INTO title_meta_cache (titleId) VALUES ('movie_1')")
        db.query("SELECT genres, studioOrDirector, voteAverage, runtime FROM title_meta_cache WHERE titleId = 'movie_1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("", c.getString(0))
            assertTrue(c.isNull(1))
            assertEquals(0.0, c.getDouble(2), 0.0)
            assertTrue(c.isNull(3))
        }
    }

    @Test
    fun migrate7To8() {
        var db = helper.createDatabase(TEST_DB, 7)
        insertLog(db, id = 1, titleId = "movie_1")
        db.close()

        db = helper.runMigrationsAndValidate(TEST_DB, 8, true, MIGRATION_7_8)

        assertEquals(1, count(db, "log_entries"))
        db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_log_entries_titleId'").use { c ->
            assertEquals(1, c.count)
        }
    }

    // Chaîne complète depuis la plus ancienne version dont le schéma est
    // exporté, avec une ligne dans chaque table.
    @Test
    fun migrate5ToLatestKeepsUserData() {
        var db = helper.createDatabase(TEST_DB, 5)
        insertUserDataV5(db)
        db.close()

        db = helper.runMigrationsAndValidate(TEST_DB, currentVersion(), true, *ALL_MIGRATIONS)

        assertUserDataV5(db)
    }

    // Garde-fou #147 : sans repli destructif pour les versions >= 4, chaque
    // montée de version doit avoir sa migration dans ALL_MIGRATIONS (sinon
    // plantage au lancement) et son schéma exporté (sinon pas testable).
    @Test
    fun migrationChainCoversEveryVersionSince4() {
        val current = currentVersion()
        for (from in 4 until current) {
            val steps = ALL_MIGRATIONS.filter { it.startVersion == from }
            assertEquals("MIGRATION_${from}_${from + 1} manquante ou en double dans ALL_MIGRATIONS", 1, steps.size)
            assertEquals("MIGRATION_${from}_* doit aller vers ${from + 1}", from + 1, steps.single().endVersion)
        }
        assertTrue("Migration au-delà de la version $current", ALL_MIGRATIONS.all { it.endVersion <= current })
        for (version in 5..current) {
            val schema = runCatching {
                InstrumentationRegistry.getInstrumentation().context.assets
                    .open("${AppDatabase::class.java.name}/$version.json").close()
            }
            assertTrue("schemas/${AppDatabase::class.java.name}/$version.json absent", schema.isSuccess)
        }
    }

    @Test
    fun realBuilderMigratesExistingDataInsteadOfWiping() {
        val db = helper.createDatabase(TEST_DB, 5)
        insertUserDataV5(db)
        db.close()

        val room = AppDatabase.build(context, TEST_DB)
        try {
            val sqlite = room.openHelper.writableDatabase
            assertEquals(currentVersion(), sqlite.version)
            assertUserDataV5(sqlite)
        } finally {
            room.close()
        }
    }

    // Une build plus ancienne installée par-dessus (rétrogradation) ne doit
    // plus effacer la base : l'ouverture échoue, les données restent.
    @Test
    fun realBuilderDoesNotWipeOnDowngrade() {
        val current = currentVersion()
        val newer = current + 1
        createRawDatabase(version = newer) { db ->
            db.execSQL("CREATE TABLE log_entries (id INTEGER PRIMARY KEY, titleName TEXT)")
            db.execSQL("INSERT INTO log_entries (id, titleName) VALUES (1, 'Inception')")
        }

        val room = AppDatabase.build(context, TEST_DB)
        try {
            val error = assertThrows(IllegalStateException::class.java) { room.openHelper.writableDatabase }
            val message = error.message.orEmpty()
            assertTrue(message, message.contains("migration from $newer to $current"))
        } finally {
            room.close()
        }

        SQLiteDatabase.openDatabase(context.getDatabasePath(TEST_DB).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            assertEquals(newer, db.version)
            db.rawQuery("SELECT titleName FROM log_entries WHERE id = 1", null).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Inception", c.getString(0))
            }
        }
    }

    // Versions 1 à 3 : publiées avant toute migration, seule exception
    // autorisée au repli destructif.
    @Test
    fun realBuilderRecreatesHistoricalVersion3() {
        createRawDatabase(version = 3) { db ->
            db.execSQL("CREATE TABLE log_entries (id INTEGER PRIMARY KEY, titleName TEXT)")
            db.execSQL("INSERT INTO log_entries (id, titleName) VALUES (1, 'Inception')")
        }

        val room = AppDatabase.build(context, TEST_DB)
        try {
            val sqlite = room.openHelper.writableDatabase
            assertEquals(currentVersion(), sqlite.version)
            assertEquals(0, count(sqlite, "log_entries"))
        } finally {
            room.close()
        }
    }

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

    private fun currentVersion(): Int {
        val room = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        return try {
            room.openHelper.writableDatabase.version
        } finally {
            room.close()
        }
    }

    private fun createRawDatabase(version: Int, block: (SQLiteDatabase) -> Unit) {
        val file = context.getDatabasePath(TEST_DB)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            block(db)
            db.version = version
        }
    }

    private fun count(db: SupportSQLiteDatabase, table: String): Int =
        db.query("SELECT COUNT(*) FROM $table").use { c ->
            c.moveToFirst()
            c.getInt(0)
        }

    private fun insertLog(db: SupportSQLiteDatabase, id: Int, titleId: String) {
        db.execSQL(
            """
            INSERT INTO log_entries (id, titleId, titleType, titleName, titlePosterUrl, dateVue, note, critique, revisionnage, spoiler, collectionId, collectionName, collectionPosterUrl)
            VALUES ($id, '$titleId', 'FILM', 'Inception', null, 1700000000000, 4.5, 'Super film', 0, 1, 10, 'Inception Saga', null)
            """.trimIndent()
        )
    }

    private fun insertUserDataV5(db: SupportSQLiteDatabase) {
        insertLog(db, id = 1, titleId = "movie_1")
        db.execSQL("INSERT INTO watchlist (titleId, titleType, titleName, titlePosterUrl, dateAdded, collectionId, collectionName, collectionPosterUrl) VALUES ('movie_2', 'FILM', 'Interstellar', null, 1690000000000, null, null, null)")
        db.execSQL("INSERT INTO custom_lists (id, name, description, dateCreated) VALUES (1, 'Favoris', 'Mes films', 1680000000000)")
        db.execSQL("INSERT INTO custom_list_titles (id, listId, titleId, titleType, titleName, titlePosterUrl, orderIndex) VALUES (1, 1, 'movie_1', 'FILM', 'Inception', null, 0)")
        db.execSQL("INSERT INTO season_progress (titleId, seasonNumber, status, dateUpdated) VALUES ('tv_1', 1, 'VUE', 1670000000000)")
        db.execSQL("INSERT INTO collection_cache (titleId, collectionId, collectionName, collectionPosterUrl) VALUES ('movie_1', 10, 'Inception Saga', null)")
        db.execSQL("INSERT INTO saga_size_cache (collectionId, totalFilms) VALUES (10, 1)")
    }

    private fun assertUserDataV5(db: SupportSQLiteDatabase) {
        db.query("SELECT titleName, dateVue, note, critique, spoiler FROM log_entries WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Inception", c.getString(0))
            assertEquals(1700000000000L, c.getLong(1))
            assertEquals(4.5f, c.getFloat(2), 0.01f)
            assertEquals("Super film", c.getString(3))
            assertEquals(1, c.getInt(4))
        }
        db.query("SELECT titleName, dateAdded, titleYear FROM watchlist WHERE titleId = 'movie_2'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Interstellar", c.getString(0))
            assertEquals(1690000000000L, c.getLong(1))
            assertNull(c.getString(2))
        }
        db.query("SELECT name, description FROM custom_lists WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Favoris", c.getString(0))
            assertEquals("Mes films", c.getString(1))
        }
        db.query("SELECT titleId, orderIndex FROM custom_list_titles WHERE listId = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("movie_1", c.getString(0))
            assertEquals(0, c.getInt(1))
        }
        db.query("SELECT status FROM season_progress WHERE titleId = 'tv_1' AND seasonNumber = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("VUE", c.getString(0))
        }
        db.query("SELECT collectionId, cachedAt FROM collection_cache WHERE titleId = 'movie_1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(10, c.getInt(0))
            assertEquals(0L, c.getLong(1))
        }
        db.query("SELECT totalFilms FROM saga_size_cache WHERE collectionId = 10").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
    }
}
