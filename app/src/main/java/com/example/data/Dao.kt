package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {
    @Query("SELECT * FROM log_entries ORDER BY dateVue DESC")
    fun getAllLogs(): Flow<List<DbLogEntry>>

    @Query("SELECT * FROM log_entries ORDER BY dateVue DESC")
    suspend fun getAllLogsList(): List<DbLogEntry>

    @Query("SELECT DISTINCT titleId FROM log_entries")
    suspend fun getWatchedTitleIds(): List<String>

    @Query("SELECT * FROM log_entries WHERE titleId = :titleId ORDER BY dateVue DESC")
    fun getLogsForTitle(titleId: String): Flow<List<DbLogEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(entry: DbLogEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(entries: List<DbLogEntry>)

    @Query("DELETE FROM log_entries WHERE id = :id")
    suspend fun deleteLogById(id: Int)
}

@Dao
interface WatchlistDao {
    @Query("SELECT * FROM watchlist ORDER BY dateAdded DESC")
    fun getAllWatchlist(): Flow<List<DbWatchlist>>

    @Query("SELECT * FROM watchlist ORDER BY dateAdded DESC")
    suspend fun getAllWatchlistList(): List<DbWatchlist>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE titleId = :titleId)")
    fun isInWatchlist(titleId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchlist(item: DbWatchlist)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchlists(items: List<DbWatchlist>)

    @Query("DELETE FROM watchlist WHERE titleId = :titleId")
    suspend fun deleteFromWatchlist(titleId: String)

    @Query("SELECT titleId FROM watchlist WHERE titleId IN (:titleIds)")
    suspend fun getExistingTitleIds(titleIds: List<String>): List<String>

    @Transaction
    suspend fun insertMissingWatchlists(items: List<DbWatchlist>) {
        if (items.isEmpty()) return
        val existingIds = getExistingTitleIds(items.map { it.titleId }).toSet()
        val toInsert = items.filterNot { it.titleId in existingIds }
        if (toInsert.isNotEmpty()) {
            insertWatchlists(toInsert)
        }
    }

    // Re-remplit les metadonnees de tri/filtre pour une entree existante
    // (ajoutee avant l'arrivee de ces colonnes, voir issue #33).
    @Query("UPDATE watchlist SET titleYear = :year, titleGenres = :genres, titleVoteAverage = :voteAverage WHERE titleId = :titleId")
    suspend fun updateWatchlistMetadata(titleId: String, year: String?, genres: String?, voteAverage: Float?)
}

@Dao
interface CustomListDao {
    @Query("SELECT * FROM custom_lists ORDER BY dateCreated DESC")
    fun getAllCustomLists(): Flow<List<DbCustomList>>

    @Query("SELECT * FROM custom_lists ORDER BY dateCreated DESC")
    suspend fun getAllCustomListsList(): List<DbCustomList>

    @Query("SELECT * FROM custom_lists WHERE id = :listId")
    fun getCustomListById(listId: Int): Flow<DbCustomList?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomList(list: DbCustomList): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomLists(lists: List<DbCustomList>)

    @Query("DELETE FROM custom_lists WHERE id = :listId")
    suspend fun deleteCustomListById(listId: Int)

    @Query("SELECT * FROM custom_list_titles WHERE listId = :listId ORDER BY orderIndex ASC")
    fun getCustomListTitles(listId: Int): Flow<List<DbCustomListTitle>>

    @Query("SELECT * FROM custom_list_titles ORDER BY id ASC")
    suspend fun getAllCustomListTitlesList(): List<DbCustomListTitle>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomListTitle(title: DbCustomListTitle)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomListTitles(titles: List<DbCustomListTitle>)

    @Query("DELETE FROM custom_list_titles WHERE id = :id")
    suspend fun deleteCustomListTitleById(id: Int)

    @Query("DELETE FROM custom_list_titles WHERE listId = :listId")
    suspend fun deleteCustomListTitlesForList(listId: Int)

    @Query("UPDATE custom_list_titles SET orderIndex = :newOrderIndex WHERE id = :id")
    suspend fun updateCustomListTitleOrder(id: Int, newOrderIndex: Int)

    @Transaction
    suspend fun updateCustomListTitlesOrder(items: List<DbCustomListTitle>) {
        items.forEachIndexed { index, item ->
            updateCustomListTitleOrder(item.id, index)
        }
    }
}

@Dao
interface CollectionCacheDao {
    @Query("SELECT * FROM collection_cache")
    fun getAll(): Flow<List<DbCollectionCache>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: DbCollectionCache)

    @Query("DELETE FROM collection_cache WHERE cachedAt < :threshold")
    suspend fun deleteExpired(threshold: Long)
}

@Dao
interface SagaSizeDao {
    @Query("SELECT * FROM saga_size_cache")
    fun getAll(): Flow<List<DbSagaSize>>

    @Query("SELECT EXISTS(SELECT 1 FROM saga_size_cache WHERE collectionId = :collectionId)")
    suspend fun exists(collectionId: Int): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: DbSagaSize)
}

@Dao
interface SeasonProgressDao {
    @Query("SELECT * FROM season_progress WHERE titleId = :titleId")
    fun getForTitle(titleId: String): Flow<List<DbSeasonProgress>>

    @Query("SELECT * FROM season_progress")
    suspend fun getAllSeasonProgressList(): List<DbSeasonProgress>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: DbSeasonProgress)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(progresses: List<DbSeasonProgress>)

    @Query("DELETE FROM season_progress WHERE titleId = :titleId AND seasonNumber = :seasonNumber")
    suspend fun deleteForSeason(titleId: String, seasonNumber: Int)
}


@Dao
interface TitleMetaCacheDao {
    @Query("SELECT * FROM title_meta_cache")
    fun getAllFlow(): Flow<List<DbTitleMetaCache>>

    @Query("SELECT * FROM title_meta_cache")
    suspend fun getAllList(): List<DbTitleMetaCache>

    @Query("SELECT * FROM title_meta_cache WHERE titleId = :titleId")
    suspend fun getByTitleId(titleId: String): DbTitleMetaCache?

    @Query("SELECT * FROM title_meta_cache WHERE titleId IN (:titleIds)")
    suspend fun getByTitleIds(titleIds: List<String>): List<DbTitleMetaCache>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: DbTitleMetaCache)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<DbTitleMetaCache>)

    @Query("DELETE FROM title_meta_cache WHERE cachedAt < :threshold")
    suspend fun deleteExpired(threshold: Long)
}

