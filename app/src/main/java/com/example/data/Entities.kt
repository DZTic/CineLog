package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "log_entries")
data class DbLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val titleId: String,       // e.g. "movie_123", "tv_456", "anime_789"
    val titleType: String,     // "FILM", "SERIE", "ANIME"
    val titleName: String,
    val titlePosterUrl: String?,
    val dateVue: Long,         // timestamp in ms
    val note: Float,           // 0.5 to 5.0
    val critique: String,
    val revisionnage: Boolean,
    val spoiler: Boolean,
    val collectionId: Int? = null,     // TMDB "saga" this movie belongs to, if any
    val collectionName: String? = null,
    val collectionPosterUrl: String? = null
)

@Entity(tableName = "watchlist")
data class DbWatchlist(
    @PrimaryKey val titleId: String, // e.g. "movie_123"
    val titleType: String,
    val titleName: String,
    val titlePosterUrl: String?,
    val dateAdded: Long = System.currentTimeMillis(),
    val collectionId: Int? = null,     // TMDB "saga" this movie belongs to, if any
    val collectionName: String? = null,
    val collectionPosterUrl: String? = null
)

// Lightweight local cache of titleId -> TMDB "saga" (collection), populated
// every time a movie's detail page is loaded. TMDB's search endpoints don't
// return belongs_to_collection (only the detail endpoint does), so this
// cache lets the Search screen group already-seen movies into their saga
// without an extra network round-trip per result.
@Entity(tableName = "collection_cache")
data class DbCollectionCache(
    @PrimaryKey val titleId: String,
    val collectionId: Int,
    val collectionName: String,
    val collectionPosterUrl: String? = null
)

@Entity(tableName = "custom_lists")
data class DbCustomList(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val description: String,
    val dateCreated: Long = System.currentTimeMillis()
)

@Entity(tableName = "custom_list_titles")
data class DbCustomListTitle(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val listId: Int,
    val titleId: String,
    val titleType: String,
    val titleName: String,
    val titlePosterUrl: String?,
    val orderIndex: Int
)

// Tracks per-season watch progress for series/anime (movies have no seasons).
// status is one of SeasonStatus's enum names: NOT_WATCHED, WATCHING, WATCHED.
@Entity(tableName = "season_progress", primaryKeys = ["titleId", "seasonNumber"])
data class DbSeasonProgress(
    val titleId: String,
    val seasonNumber: Int,
    val status: String,
    val dateUpdated: Long = System.currentTimeMillis()
)
