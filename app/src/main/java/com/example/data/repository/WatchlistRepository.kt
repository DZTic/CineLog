package com.example.data.repository

import com.example.data.DbWatchlist
import com.example.data.WatchlistDao
import kotlinx.coroutines.flow.Flow

class WatchlistRepository(
    private val watchlistDao: WatchlistDao
) {
    fun getWatchlistStream(): Flow<List<DbWatchlist>> = watchlistDao.getAllWatchlist()

    suspend fun getAllWatchlistList(): List<DbWatchlist> = watchlistDao.getAllWatchlistList()

    fun isInWatchlistStream(titleId: String): Flow<Boolean> = watchlistDao.isInWatchlist(titleId)

    suspend fun addToWatchlist(item: DbWatchlist) = watchlistDao.insertWatchlist(item)

    suspend fun addToWatchlistBatch(items: List<DbWatchlist>) = watchlistDao.insertWatchlists(items)

    suspend fun removeFromWatchlist(titleId: String) = watchlistDao.deleteFromWatchlist(titleId)

    suspend fun updateMetadata(titleId: String, year: String?, genres: String?, voteAverage: Float?) =
        watchlistDao.updateWatchlistMetadata(titleId, year, genres, voteAverage)
}
