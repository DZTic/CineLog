package com.example.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@Entity(
    tableName = "log_entries",
    indices = [Index(value = ["titleId"])]
)
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

@Entity(
    tableName = "watchlist",
    indices = [Index(value = ["collectionId"])]
)
data class DbWatchlist(
    @PrimaryKey val titleId: String, // e.g. "movie_123"
    val titleType: String,
    val titleName: String,
    val titlePosterUrl: String?,
    val dateAdded: Long = System.currentTimeMillis(),
    // Metadonnees de tri/filtre (issue #33). Nullables car les entrees
    // existantes avant cette version n'ont pas ces infos : elles sont
    // progressivement re-remplies depuis l'API quand elles manquent.
    val titleYear: String? = null,
    val titleGenres: String? = null,      // stocke CSV ("Action,Drame")
    val titleVoteAverage: Float? = null,  // echelle 0-5 comme partout dans l'app
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
    val collectionPosterUrl: String? = null,
    @ColumnInfo(defaultValue = "0") val cachedAt: Long = System.currentTimeMillis()
)

// Caches the total number of films belonging to a TMDB saga (collection),
// so screens showing a saga as a single grouped card (Accueil, Watchlist,
// Recherche) can tell whether the user has watched it in its entirety
// without re-fetching the collection from TMDB every time it's displayed.
@Entity(tableName = "saga_size_cache")
data class DbSagaSize(
    @PrimaryKey val collectionId: Int,
    val totalFilms: Int
)


@Entity(tableName = "title_meta_cache")
data class DbTitleMetaCache(
    @PrimaryKey val titleId: String,
    val genres: String = "",
    val studioOrDirector: String? = null,
    val voteAverage: Float = 0f,
    val runtime: Int? = null,
    @ColumnInfo(defaultValue = "0") val cachedAt: Long = System.currentTimeMillis()
)


@Entity(tableName = "custom_lists")
data class DbCustomList(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val description: String,
    val dateCreated: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "custom_list_titles",
    indices = [
        Index(value = ["listId", "orderIndex"]),
        Index(value = ["titleId"])
    ]
)
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

@JsonClass(generateAdapter = true)
data class CineLogBackup(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val logs: List<DbLogEntry> = emptyList(),
    val watchlist: List<DbWatchlist> = emptyList(),
    val customLists: List<DbCustomList> = emptyList(),
    val customListTitles: List<DbCustomListTitle> = emptyList(),
    val seasonProgress: List<DbSeasonProgress> = emptyList()
)

data class ImportSummary(
    val logsCount: Int,
    val watchlistCount: Int,
    val customListsCount: Int,
    val seasonProgressCount: Int
)
