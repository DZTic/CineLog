package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {
    @Query("SELECT * FROM log_entries ORDER BY dateVue DESC")
    fun getAllLogs(): Flow<List<DbLogEntry>>

    @Query("SELECT * FROM log_entries WHERE titleId = :titleId ORDER BY dateVue DESC")
    fun getLogsForTitle(titleId: String): Flow<List<DbLogEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(entry: DbLogEntry)

    @Query("DELETE FROM log_entries WHERE id = :id")
    suspend fun deleteLogById(id: Int)
}

@Dao
interface WatchlistDao {
    @Query("SELECT * FROM watchlist ORDER BY dateAdded DESC")
    fun getAllWatchlist(): Flow<List<DbWatchlist>>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE titleId = :titleId)")
    fun isInWatchlist(titleId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchlist(item: DbWatchlist)

    @Query("DELETE FROM watchlist WHERE titleId = :titleId")
    suspend fun deleteFromWatchlist(titleId: String)
}

@Dao
interface CustomListDao {
    @Query("SELECT * FROM custom_lists ORDER BY dateCreated DESC")
    fun getAllCustomLists(): Flow<List<DbCustomList>>

    @Query("SELECT * FROM custom_lists WHERE id = :listId")
    fun getCustomListById(listId: Int): Flow<DbCustomList?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomList(list: DbCustomList): Long

    @Query("DELETE FROM custom_lists WHERE id = :listId")
    suspend fun deleteCustomListById(listId: Int)

    @Query("SELECT * FROM custom_list_titles WHERE listId = :listId ORDER BY orderIndex ASC")
    fun getCustomListTitles(listId: Int): Flow<List<DbCustomListTitle>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomListTitle(title: DbCustomListTitle)

    @Query("DELETE FROM custom_list_titles WHERE id = :id")
    suspend fun deleteCustomListTitleById(id: Int)

    @Query("DELETE FROM custom_list_titles WHERE listId = :listId")
    suspend fun deleteCustomListTitlesForList(listId: Int)

    @Query("UPDATE custom_list_titles SET orderIndex = :newOrderIndex WHERE id = :id")
    suspend fun updateCustomListTitleOrder(id: Int, newOrderIndex: Int)
}

@Dao
interface SeasonProgressDao {
    @Query("SELECT * FROM season_progress WHERE titleId = :titleId")
    fun getForTitle(titleId: String): Flow<List<DbSeasonProgress>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: DbSeasonProgress)

    @Query("DELETE FROM season_progress WHERE titleId = :titleId AND seasonNumber = :seasonNumber")
    suspend fun deleteForSeason(titleId: String, seasonNumber: Int)
}
