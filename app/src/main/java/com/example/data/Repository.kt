package com.example.data

import android.content.Context
import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

import com.example.util.DateFormatter
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

enum class TitleType {
    FILM, SERIE, ANIME;

    val displayName: String
        get() = when (this) {
            FILM -> "Film"
            SERIE -> "Série"
            ANIME -> "Anime"
        }
}

data class CineSeason(
    val seasonNumber: Int,
    val name: String,
    val episodeCount: Int
)

enum class SeasonStatus {
    NOT_WATCHED, WATCHING, WATCHED;

    val displayName: String
        get() = when (this) {
            NOT_WATCHED -> "Non vue"
            WATCHING -> "En cours"
            WATCHED -> "Vue"
        }
}

data class ProfileStats(
    val totalLogs: Int = 0,
    val averageScore: Float = 0f,
    val rewatchCount: Int = 0,
    val currentStreak: Int = 0,
    val maxStreak: Int = 0,
    val mostProductiveYear: Pair<String, Int>? = null,
    val monthlyAverageScores: List<Pair<String, Float>> = emptyList(),
    val topGenres: List<Pair<String, Int>> = emptyList(),
    val topDirectorsOrStudios: List<Pair<String, Int>> = emptyList(),
    val communityScoreDelta: Float? = null,
    val totalRuntimeMinutes: Int = 0
)

// Informations enrichies recuperées depuis la fiche détaillée d'un titre
// (utilisées par les statistiques du profil, issue #29).
private data class TitleMeta(
    val genres: List<String>,
    val studioOrDirector: String?,
    val voteAverage: Float,
    val runtime: Int?
)

data class CineTitle(
    val id: String,          // e.g., "movie_123", "tv_456", "anime_789"
    val type: TitleType,     // FILM, SERIE, ANIME
    val title: String,
    val year: String,
    val posterUrl: String?,
    val synopsis: String,
    val genres: List<String>,
    val voteAverage: Float,
    val studioOrDirector: String? = null,
    val seasons: List<CineSeason> = emptyList(),
    val collectionId: Int? = null,   // TMDB "saga" this movie belongs to, if any
    val collectionName: String? = null,
    val collectionPosterUrl: String? = null, // official saga poster, distinct from this movie's own poster
    val runtime: Int? = null
)

class SearchPagingSource(
    private val repository: Repository,
    private val query: String,
    private val typeFilter: TitleType?
) : PagingSource<Int, CineTitle>() {
    override fun getRefreshKey(state: PagingState<Int, CineTitle>): Int? {
        return state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, CineTitle> {
        val page = params.key ?: 1
        return try {
            val items = repository.searchTitlesPaged(query, typeFilter, page)
            LoadResult.Page(
                data = items,
                prevKey = if (page == 1) null else page - 1,
                nextKey = if (items.isEmpty()) null else page + 1
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}

class DiscoverPagingSource(
    private val repository: Repository,
    private val typeFilter: TitleType
) : PagingSource<Int, CineTitle>() {
    override fun getRefreshKey(state: PagingState<Int, CineTitle>): Int? {
        return state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, CineTitle> {
        val page = params.key ?: 1
        return try {
            val items = repository.getTrendingOrPopularPaged(typeFilter, page)
            LoadResult.Page(
                data = items,
                prevKey = if (page == 1) null else page - 1,
                nextKey = if (items.isEmpty()) null else page + 1
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}

class Repository(
    private val logDao: LogDao,
    private val watchlistDao: WatchlistDao,
    private val customListDao: CustomListDao,
    private val seasonProgressDao: SeasonProgressDao,
    private val collectionCacheDao: CollectionCacheDao,
    private val sagaSizeDao: SagaSizeDao,
    private val titleMetaCacheDao: TitleMetaCacheDao? = null,
    private val preferenceManager: PreferenceManager,
    private val context: Context? = null
) {
    private val tag = "Repository"

    private val moshi: com.squareup.moshi.Moshi by lazy {
        com.squareup.moshi.Moshi.Builder()
            .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
            .build()
    }

    private val okHttpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)

        builder.addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 CineLog/1.0")
                .build()
            chain.proceed(request)
        }

        context?.cacheDir?.let { cacheDir ->
            builder.cache(Cache(File(cacheDir, "http_cache"), 10L * 1024 * 1024))
        }

        if (BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(logging)
        }

        builder.addNetworkInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            val path = request.url.encodedPath
            val maxAge = when {
                path.contains("trending") || path.contains("top/anime") -> 3600
                path.contains("search") || path.endsWith("/anime") -> 300
                path.contains("movie/") || path.contains("tv/") || path.contains("collection/") || path.contains("/full") -> 86400
                else -> 300
            }
            response.newBuilder()
                .header("Cache-Control", "public, max-age=$maxAge")
                .build()
        }

        builder.build()
    }

    private val jikanApi: JikanApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.jikan.moe/v4/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(JikanApiService::class.java)
    }

    private fun buildTmdbApi(baseUrl: String): TmdbApiService {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(TmdbApiService::class.java)
    }

    private val tmdbDirectApi: TmdbApiService by lazy { buildTmdbApi("https://api.themoviedb.org/3/") }

    private val tmdbProxyApi: TmdbApiService by lazy {
        buildTmdbApi(BuildConfig.TMDB_PROXY_BASE_URL.ifBlank { "https://api.themoviedb.org/3/" })
    }

    private val tmdbApi: TmdbApiService
        get() = if (getTmdbKey().isEmpty()) tmdbProxyApi else tmdbDirectApi

    // ==========================================
    // LOCAL ROOM DATABASE QUERY FLOWS
    // ==========================================

    val allLogs: Flow<List<DbLogEntry>> = logDao.getAllLogs()

    fun getLogsForTitle(titleId: String): Flow<List<DbLogEntry>> = logDao.getLogsForTitle(titleId)

    suspend fun insertLog(entry: DbLogEntry) = withContext(Dispatchers.IO) {
        logDao.insertLog(entry)
        if (!titleMetaCache.containsKey(entry.titleId)) {
            try {
                enrichLogMetadata(listOf(entry))
            } catch (e: Exception) {
                Log.w(tag, "Background enrichLogMetadata failed for ${entry.titleId}: ${e.localizedMessage}")
            }
        }
    }

    suspend fun deleteLogById(id: Int) = withContext(Dispatchers.IO) {
        logDao.deleteLogById(id)
    }

    val allWatchlist: Flow<List<DbWatchlist>> = watchlistDao.getAllWatchlist()

    fun isInWatchlist(titleId: String): Flow<Boolean> = watchlistDao.isInWatchlist(titleId)

    suspend fun addToWatchlist(item: DbWatchlist) = withContext(Dispatchers.IO) {
        watchlistDao.insertWatchlist(item)
    }

    suspend fun addToWatchlist(title: CineTitle) = withContext(Dispatchers.IO) {
        watchlistDao.insertWatchlist(
            DbWatchlist(
                titleId = title.id,
                titleType = title.type.name,
                titleName = title.title,
                titlePosterUrl = title.posterUrl,
                titleYear = title.year.ifBlank { null },
                titleGenres = title.genres.takeIf { it.isNotEmpty() }?.joinToString(","),
                titleVoteAverage = title.voteAverage.takeIf { it > 0f },
                collectionId = title.collectionId,
                collectionName = title.collectionName,
                collectionPosterUrl = title.collectionPosterUrl
            )
        )
    }

    suspend fun removeFromWatchlist(titleId: String) = withContext(Dispatchers.IO) {
        watchlistDao.deleteFromWatchlist(titleId)
    }

    suspend fun backfillWatchlistMetadata(titleId: String) = withContext(Dispatchers.IO) {
        try {
            val detail = getTitleDetail(titleId)
            val genres = detail.genres.takeIf { it.isNotEmpty() }?.joinToString(",")
            val year = detail.year.takeIf { it.isNotBlank() } ?: "N/A"
            watchlistDao.updateWatchlistMetadata(
                titleId = titleId,
                year = year,
                genres = genres,
                voteAverage = detail.voteAverage.takeIf { it > 0f }
            )
        } catch (e: Exception) {
            Log.e(tag, "Error backfilling metadata for $titleId: ${e.localizedMessage}")
        }
    }

    fun getSeasonProgressForTitle(titleId: String): Flow<List<DbSeasonProgress>> =
        seasonProgressDao.getForTitle(titleId)

    suspend fun setSeasonStatus(titleId: String, seasonNumber: Int, status: SeasonStatus) =
        withContext(Dispatchers.IO) {
            if (status == SeasonStatus.NOT_WATCHED) {
                seasonProgressDao.deleteForSeason(titleId, seasonNumber)
            } else {
                seasonProgressDao.upsert(
                    DbSeasonProgress(
                        titleId = titleId,
                        seasonNumber = seasonNumber,
                        status = status.name
                    )
                )
            }
        }

    val collectionCache: Flow<List<DbCollectionCache>> = collectionCacheDao.getAll()

    private suspend fun cacheCollectionInfo(
        titleId: String,
        collectionId: Int?,
        collectionName: String?,
        collectionPosterUrl: String?
    ) {
        if (collectionId == null || collectionName.isNullOrBlank()) return
        withContext(Dispatchers.IO) {
            collectionCacheDao.upsert(DbCollectionCache(titleId, collectionId, collectionName, collectionPosterUrl))
        }
    }

    val allCustomLists: Flow<List<DbCustomList>> = customListDao.getAllCustomLists()

    fun getCustomListById(listId: Int): Flow<DbCustomList?> = customListDao.getCustomListById(listId)

    fun getCustomListTitles(listId: Int): Flow<List<DbCustomListTitle>> = customListDao.getCustomListTitles(listId)

    suspend fun createCustomList(name: String, description: String): Long = withContext(Dispatchers.IO) {
        customListDao.insertCustomList(DbCustomList(name = name, description = description))
    }

    suspend fun deleteCustomList(listId: Int) = withContext(Dispatchers.IO) {
        customListDao.deleteCustomListById(listId)
        customListDao.deleteCustomListTitlesForList(listId)
    }

    suspend fun addTitleToCustomList(listId: Int, titleId: String, titleType: String, titleName: String, titlePosterUrl: String?, orderIndex: Int) = withContext(Dispatchers.IO) {
        val entry = DbCustomListTitle(
            listId = listId,
            titleId = titleId,
            titleType = titleType,
            titleName = titleName,
            titlePosterUrl = titlePosterUrl,
            orderIndex = orderIndex
        )
        customListDao.insertCustomListTitle(entry)
    }

    suspend fun removeTitleFromCustomList(id: Int) = withContext(Dispatchers.IO) {
        customListDao.deleteCustomListTitleById(id)
    }

    suspend fun updateCustomListTitleOrder(id: Int, newOrderIndex: Int) = withContext(Dispatchers.IO) {
        customListDao.updateCustomListTitleOrder(id, newOrderIndex)
    }

    private fun getTmdbKey(): String {
        return preferenceManager.getTmdbApiKey()
    }

    private fun List<CineTitle>.dedupeByTitle(): List<CineTitle> {
        val seen = HashSet<String>()
        return filter { seen.add(it.title.trim().lowercase()) }
    }

    private fun TmdbTvResult.isLikelyAnime(): Boolean {
        val isAnimation = genreIds?.contains(16) == true
        val isJapaneseOrigin = originalLanguage == "ja" || originCountry?.contains("JP") == true
        return isAnimation && isJapaneseOrigin
    }

    suspend fun searchTitles(query: String, typeFilter: TitleType? = null): List<CineTitle> =
        searchTitlesPaged(query, typeFilter, page = 1)

    suspend fun searchTitlesPaged(query: String, typeFilter: TitleType? = null, page: Int = 1): List<CineTitle> = coroutineScope {
        if (query.trim().isEmpty()) return@coroutineScope emptyList()

        val tmdbKey = getTmdbKey()

        val filmsDeferred = if (typeFilter == null || typeFilter == TitleType.FILM) {
            async(Dispatchers.IO) {
                try {
                    val response = tmdbApi.searchMovie(tmdbKey, query, page = page)
                    response.results.map { it.toCineTitle() }
                } catch (e: Exception) {
                    Log.e(tag, "Error searching TMDB movie: ${e.localizedMessage}")
                    emptyList()
                }
            }
        } else null

        val seriesResultDeferred = if (typeFilter == null || typeFilter == TitleType.SERIE || typeFilter == TitleType.ANIME) {
            async(Dispatchers.IO) {
                try {
                    tmdbApi.searchTv(tmdbKey, query, page = page).results
                } catch (e: Exception) {
                    Log.e(tag, "Error searching TMDB TV: ${e.localizedMessage}")
                    emptyList<TmdbTvResult>()
                }
            }
        } else null

        val animeDeferred = if (typeFilter == null || typeFilter == TitleType.ANIME) {
            async(Dispatchers.IO) {
                try {
                    val response = jikanApi.searchAnime(query, page = page)
                    response.data.map { it.toCineTitle() }
                } catch (e: Exception) {
                    Log.e(tag, "Error searching Jikan Anime: ${e.localizedMessage}")
                    emptyList()
                }
            }
        } else null

        val films = filmsDeferred?.await() ?: emptyList()
        val tvResults = seriesResultDeferred?.await() ?: emptyList()
        val jikanAnime = animeDeferred?.await() ?: emptyList()

        val (animeFromTmdb, pureSeries) = tvResults.partition { it.isLikelyAnime() }
        val series = if (typeFilter == null || typeFilter == TitleType.SERIE) {
            pureSeries.map { it.toCineTitle() }
        } else emptyList()

        val anime = if (typeFilter == null || typeFilter == TitleType.ANIME) {
            (jikanAnime + animeFromTmdb.map { it.toAnimeCineTitle() }).dedupeByTitle()
        } else emptyList()

        return@coroutineScope (films + series + anime).sortedByDescending { it.voteAverage }
    }

    suspend fun getTitleDetail(id: String): CineTitle = withContext(Dispatchers.IO) {
        val parts = id.split("_", limit = 2)
        if (parts.size < 2) throw IllegalArgumentException("Format ID invalide: $id")

        val prefix = parts[0]
        val rawIdString = parts[1]
        val rawId = rawIdString.toIntOrNull() ?: throw IllegalArgumentException("ID numérique invalide: $rawIdString")

        when (prefix) {
            "movie" -> {
                val tmdbKey = getTmdbKey()
                val movie = tmdbApi.getMovieDetail(rawId, tmdbKey)
                val cineTitle = movie.toCineTitle()
                cacheCollectionInfo(cineTitle.id, cineTitle.collectionId, cineTitle.collectionName, cineTitle.collectionPosterUrl)
                cineTitle
            }
            "tv" -> {
                val tmdbKey = getTmdbKey()
                val tv = tmdbApi.getTvDetail(rawId, tmdbKey)
                tv.toCineTitle()
            }
            "anime" -> {
                val animeResponse = jikanApi.getAnimeDetail(rawId)
                animeResponse.data.toCineTitle()
            }
            else -> throw IllegalArgumentException("Type inconnu pour l'ID: $id")
        }
    }

    private suspend fun fetchCollectionDetail(collectionId: Int): TmdbCollectionDetail? {
        val tmdbKey = getTmdbKey()
        return try {
            tmdbApi.getCollection(collectionId, tmdbKey)
        } catch (e: Exception) {
            Log.e(tag, "Error fetching collection $collectionId: ${e.localizedMessage}")
            null
        }
    }

    suspend fun getCollectionTitles(collectionId: Int, excludeTitleId: String? = null): List<CineTitle> =
        withContext(Dispatchers.IO) {
            val collection = fetchCollectionDetail(collectionId) ?: return@withContext emptyList()
            val posterUrl = collection.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
            collection.parts
                .map {
                    it.toCineTitle().copy(
                        collectionId = collection.id,
                        collectionName = collection.name,
                        collectionPosterUrl = posterUrl
                    )
                }
                .filter { it.id != excludeTitleId }
                .sortedBy { it.year }
                .also { titles ->
                    titles.forEach { cacheCollectionInfo(it.id, collection.id, collection.name, posterUrl) }
                    sagaSizeDao.upsert(DbSagaSize(collection.id, titles.size))
                }
        }

    val sagaSizeCache: Flow<List<DbSagaSize>> = sagaSizeDao.getAll()

    suspend fun ensureSagaSizeCached(collectionId: Int) {
        withContext(Dispatchers.IO) {
            if (sagaSizeDao.exists(collectionId)) return@withContext
            val collection = fetchCollectionDetail(collectionId) ?: return@withContext
            sagaSizeDao.upsert(DbSagaSize(collectionId, collection.parts.size))
        }
    }

    data class SagaInfo(
        val id: Int,
        val name: String,
        val overview: String?,
        val posterUrl: String?
    )

    suspend fun getSagaDetail(collectionId: Int): Pair<SagaInfo, List<CineTitle>>? =
        withContext(Dispatchers.IO) {
            val collection = fetchCollectionDetail(collectionId) ?: return@withContext null
            val posterUrl = collection.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
            val info = SagaInfo(
                id = collection.id,
                name = collection.name,
                overview = collection.overview,
                posterUrl = posterUrl
            )
            val titles = collection.parts
                .map {
                    it.toCineTitle().copy(
                        collectionId = collection.id,
                        collectionName = collection.name,
                        collectionPosterUrl = posterUrl
                    )
                }
                .sortedBy { it.year }
            titles.forEach { cacheCollectionInfo(it.id, collection.id, collection.name, posterUrl) }
            sagaSizeDao.upsert(DbSagaSize(collection.id, titles.size))
            info to titles
        }

    suspend fun getTrendingOrPopular(type: TitleType): List<CineTitle> =
        getTrendingOrPopularPaged(type, page = 1)

    suspend fun getTrendingOrPopularPaged(type: TitleType, page: Int = 1): List<CineTitle> = withContext(Dispatchers.IO) {
        val tmdbKey = getTmdbKey()
        when (type) {
            TitleType.FILM -> {
                try {
                    tmdbApi.getTrendingMovies(tmdbKey, page = page).results.map { it.toCineTitle() }
                } catch (e: Exception) {
                    Log.e(tag, "Error fetching trending movies: ${e.localizedMessage}")
                    if (page == 1) getFallbackFilms() else emptyList()
                }
            }
            TitleType.SERIE -> {
                try {
                    tmdbApi.getTrendingTv(tmdbKey, page = page).results.filterNot { it.isLikelyAnime() }.map { it.toCineTitle() }
                } catch (e: Exception) {
                    Log.e(tag, "Error fetching trending TV: ${e.localizedMessage}")
                    if (page == 1) getFallbackSeries() else emptyList()
                }
            }
            TitleType.ANIME -> {
                try {
                    val jikanAnime = jikanApi.getTopAnime(page = page).data.map { it.toCineTitle() }
                    if (jikanAnime.isNotEmpty()) {
                        jikanAnime
                    } else {
                        getAnimeFromTmdbFallback(tmdbKey, page)
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error fetching top anime from Jikan: ${e.localizedMessage}")
                    getAnimeFromTmdbFallback(tmdbKey, page)
                }
            }
        }
    }

    private suspend fun getAnimeFromTmdbFallback(tmdbKey: String, page: Int): List<CineTitle> {
        return try {
            val tmdbAnime = tmdbApi.getTrendingTv(tmdbKey, page = page).results
                .filter { it.isLikelyAnime() }
                .map { it.toAnimeCineTitle() }
            if (tmdbAnime.isNotEmpty()) tmdbAnime else if (page == 1) getFallbackAnime() else emptyList()
        } catch (e: Exception) {
            Log.e(tag, "Error fetching TMDB fallback anime: ${e.localizedMessage}")
            if (page == 1) getFallbackAnime() else emptyList()
        }
    }

    private fun TmdbMovieResult.toCineTitle(): CineTitle {
        val y = releaseDate?.take(4) ?: "N/A"
        val poster = if (posterPath != null) "https://image.tmdb.org/t/p/w500$posterPath" else null
        return CineTitle(
            id = "movie_$id",
            type = TitleType.FILM,
            title = title,
            year = y,
            posterUrl = poster,
            synopsis = overview ?: "",
            genres = emptyList(),
            voteAverage = (voteAverage ?: 0f) / 2f
        )
    }

    private fun TmdbTvResult.toCineTitle(): CineTitle {
        val y = firstAirDate?.take(4) ?: "N/A"
        val poster = if (posterPath != null) "https://image.tmdb.org/t/p/w500$posterPath" else null
        return CineTitle(
            id = "tv_$id",
            type = TitleType.SERIE,
            title = name,
            year = y,
            posterUrl = poster,
            synopsis = overview ?: "",
            genres = emptyList(),
            voteAverage = (voteAverage ?: 0f) / 2f
        )
    }

    private fun TmdbTvResult.toAnimeCineTitle(): CineTitle {
        val y = firstAirDate?.take(4) ?: "N/A"
        val poster = if (posterPath != null) "https://image.tmdb.org/t/p/w500$posterPath" else null
        return CineTitle(
            id = "tv_$id",
            type = TitleType.ANIME,
            title = name,
            year = y,
            posterUrl = poster,
            synopsis = overview ?: "",
            genres = emptyList(),
            voteAverage = (voteAverage ?: 0f) / 2f
        )
    }

    private fun TmdbMovieDetail.toCineTitle(): CineTitle {
        val y = releaseDate?.take(4) ?: "N/A"
        val poster = if (posterPath != null) "https://image.tmdb.org/t/p/w500$posterPath" else null
        val director = credits?.cast?.take(3)?.joinToString { it.name } ?: "N/A"
        return CineTitle(
            id = "movie_$id",
            type = TitleType.FILM,
            title = title,
            year = y,
            posterUrl = poster,
            synopsis = overview ?: "",
            genres = genres?.map { it.name } ?: emptyList(),
            voteAverage = (voteAverage ?: 0f) / 2f,
            studioOrDirector = director,
            collectionId = belongsToCollection?.id,
            collectionName = belongsToCollection?.name,
            collectionPosterUrl = belongsToCollection?.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
            runtime = runtime
        )
    }

    private fun TmdbTvDetail.isLikelyAnime(): Boolean {
        val isAnimation = genres?.any { it.id == 16 } == true
        val isJapaneseOrigin = originalLanguage == "ja" || originCountry?.contains("JP") == true
        return isAnimation && isJapaneseOrigin
    }

    private fun TmdbTvDetail.toCineTitle(): CineTitle {
        val y = firstAirDate?.take(4) ?: "N/A"
        val poster = if (posterPath != null) "https://image.tmdb.org/t/p/w500$posterPath" else null
        val director = credits?.cast?.take(3)?.joinToString { it.name } ?: "N/A"
        return CineTitle(
            id = "tv_$id",
            type = if (isLikelyAnime()) TitleType.ANIME else TitleType.SERIE,
            title = name,
            year = y,
            posterUrl = poster,
            synopsis = overview ?: "",
            genres = genres?.map { it.name } ?: emptyList(),
            voteAverage = (voteAverage ?: 0f) / 2f,
            studioOrDirector = director,
            seasons = seasons?.map { CineSeason(it.seasonNumber, it.name, it.episodeCount) } ?: emptyList()
            ,
            runtime = episodeRunTime?.firstOrNull()
        )
    }

    private fun JikanAnimeData.toCineTitle(): CineTitle {
        val y = year?.toString() ?: "N/A"
        val poster = images?.jpg?.largeImageUrl ?: images?.jpg?.imageUrl
        val studio = studios?.firstOrNull()?.name
        val mappedSeasons = if (episodes != null) {
            listOf(CineSeason(1, "Saison Unique", episodes))
        } else emptyList()

        return CineTitle(
            id = "anime_$malId",
            type = TitleType.ANIME,
            title = title,
            year = y,
            posterUrl = poster,
            synopsis = synopsis ?: "",
            genres = genres?.map { it.name } ?: emptyList(),
            voteAverage = (score ?: 0f) / 2f,
            studioOrDirector = studio,
            seasons = mappedSeasons
        )
    }

    private fun getFallbackFilms(): List<CineTitle> = listOf(
        CineTitle("movie_27205", TitleType.FILM, "Inception", "2010", "https://image.tmdb.org/t/p/w500/aeG07bS9Z6g0D8U5I14kY2q0bM5.jpg", "Un voleur de secrets industriels utilise le subconscient.", listOf("Action", "Science-Fiction"), 4.4f, "Christopher Nolan"),
        CineTitle("movie_157336", TitleType.FILM, "Interstellar", "2014", "https://image.tmdb.org/t/p/w500/gEU2vYvKext9hqg6vXXndccOWmO.jpg", "Un voyage interstellaire pour sauver l'humanité.", listOf("Aventure", "Science-Fiction"), 4.3f, "Christopher Nolan"),
        CineTitle("movie_680", TitleType.FILM, "Pulp Fiction", "1994", "https://image.tmdb.org/t/p/w500/fIE3lYTE9An6Y8Zg8f2clg6cuyp.jpg", "L'odyssée sanglante et ironique de truands de bas étage.", listOf("Thriller", "Crime"), 4.5f, "Quentin Tarantino"),
        CineTitle("movie_129", TitleType.FILM, "Le Voyage de Chihiro", "2001", "https://image.tmdb.org/t/p/w500/39wmItIWsg6s9XRY7gZg92zAsas.jpg", "Une jeune fille se retrouve bloquée dans le monde des esprits.", listOf("Animation", "Fantastique"), 4.6f, "Hayao Miyazaki")
    )

    private fun getFallbackSeries(): List<CineTitle> = listOf(
        CineTitle("tv_1396", TitleType.SERIE, "Breaking Bad", "2008", "https://image.tmdb.org/t/p/w500/ztk6scNlh6g69gXv7qPG9836g9n.jpg", "Un prof de chimie malade devient baron de la drogue.", listOf("Drame", "Crime"), 4.5f, "Vince Gilligan"),
        CineTitle("tv_1399", TitleType.SERIE, "Game of Thrones", "2011", "https://image.tmdb.org/t/p/w500/1XS19CfS3Z79YvHG6go4gH6gX4C.jpg", "Lutte de pouvoir pour le trône de fer de Westeros.", listOf("Drame", "Fantastique"), 4.2f, "David Benioff"),
        CineTitle("tv_456", TitleType.SERIE, "The Simpsons", "1989", "https://image.tmdb.org/t/p/w500/77u7S2bAt795X8p66A59fXnJ8jX.jpg", "Le quotidien déjanté d'une famille de Springfield.", listOf("Animation", "Comédie"), 4.0f, "Matt Groening")
    )

    private fun getFallbackAnime(): List<CineTitle> = listOf(
        CineTitle("anime_5114", TitleType.ANIME, "Fullmetal Alchemist: Brotherhood", "2009", "https://cdn.myanimelist.net/images/anime/1208/94745l.jpg", "Deux frères alchimistes cherchent à récupérer leurs corps.", listOf("Action", "Drame", "Fantastique"), 4.6f, "Bones"),
        CineTitle("anime_38524", TitleType.ANIME, "Shingeki no Kyojin Season 3 Part 2", "2019", "https://cdn.myanimelist.net/images/anime/1517/100633l.jpg", "La reconquêté du Mur Maria commence, face aux Titans.", listOf("Action", "Drame", "Mystère"), 4.5f, "Wit Studio"),
        CineTitle("anime_21", TitleType.ANIME, "One Piece", "1999", "https://cdn.myanimelist.net/images/anime/1244/138851l.jpg", "Monkey D. Luffy explore Grand Line à la recherche du trésor ultime.", listOf("Action", "Aventure", "Comédie"), 4.4f, "Toei Animation"),
        CineTitle("anime_1535", TitleType.ANIME, "Death Note", "2006", "https://cdn.myanimelist.net/images/anime/9/9444l.jpg", "Un lycéen découvre un cahier capable de tuer quiconque y voit son nom écrit.", listOf("Mystère", "Psychologique", "Thriller"), 4.3f, "Madhouse"),
        CineTitle("anime_38000", TitleType.ANIME, "Demon Slayer: Kimetsu no Yaiba", "2019", "https://cdn.myanimelist.net/images/anime/1286/99889l.jpg", "Tanjiro cherche un remède pour sa sœur transformée en démon.", listOf("Action", "Fantastique"), 4.3f, "ufotable"),
        CineTitle("anime_40748", TitleType.ANIME, "Jujutsu Kaisen", "2020", "https://cdn.myanimelist.net/images/anime/1171/109222l.jpg", "Un lycéen rejoint une organisation secrète d'exorcistes.", listOf("Action", "Fantastique"), 4.3f, "MAPPA"),
        CineTitle("anime_11061", TitleType.ANIME, "Hunter x Hunter (2011)", "2011", "https://cdn.myanimelist.net/images/anime/1337/99013l.jpg", "Gon veut devenir Hunter pour retrouver son père disparu.", listOf("Action", "Aventure", "Fantastique"), 4.5f, "Madhouse"),
        CineTitle("anime_1735", TitleType.ANIME, "Naruto Shippuden", "2007", "https://cdn.myanimelist.net/images/anime/1565/111305l.jpg", "Naruto s'entraîne sans relâche pour ramener Sasuke et protéger son village.", listOf("Action", "Aventure"), 4.2f, "Studio Pierrot"),
        CineTitle("anime_9253", TitleType.ANIME, "Steins;Gate", "2011", "https://cdn.myanimelist.net/images/anime/1935/127974l.jpg", "Des jeunes chercheurs découvrent le moyen d'envoyer des messages dans le passé.", listOf("Science-Fiction", "Thriller"), 4.5f, "White Fox"),
        CineTitle("anime_31964", TitleType.ANIME, "My Hero Academia", "2016", "https://cdn.myanimelist.net/images/anime/10/79238l.jpg", "Dans un monde de super-héros, un garçon sans pouvoir rêve de devenir le numéro un.", listOf("Action", "Aventure"), 4.1f, "Bones"),
        CineTitle("anime_22319", TitleType.ANIME, "Tokyo Ghoul", "2014", "https://cdn.myanimelist.net/images/anime/1498/134443l.jpg", "Un étudiant devient demi-ghoul après une attaque mystérieuse.", listOf("Action", "Horreur", "Mystère"), 4.0f, "Studio Pierrot"),
        CineTitle("anime_1575", TitleType.ANIME, "Code Geass: Lelouch of the Rebellion", "2006", "https://cdn.myanimelist.net/images/anime/1032/135088l.jpg", "Un prince exilé obtient un pouvoir absolu pour renverser un empire.", listOf("Action", "Drame", "Mecha"), 4.4f, "Sunrise"),
        CineTitle("anime_44511", TitleType.ANIME, "Chainsaw Man", "2022", "https://cdn.myanimelist.net/images/anime/1806/126216l.jpg", "Un jeune homme fusionne avec son chien démon tronçonneuse.", listOf("Action", "Horreur", "Fantastique"), 4.2f, "MAPPA"),
        CineTitle("anime_269", TitleType.ANIME, "Bleach", "2004", "https://cdn.myanimelist.net/images/anime/3/40451l.jpg", "Ichigo Kurosaki devient Shinigami pour défendre les humains contre les Hollows.", listOf("Action", "Aventure", "Fantastique"), 4.0f, "Studio Pierrot"),
        CineTitle("anime_11757", TitleType.ANIME, "Sword Art Online", "2012", "https://cdn.myanimelist.net/images/anime/11/39717l.jpg", "Des joueurs sont piégés dans un jeu de réalité virtuelle mortel.", listOf("Action", "Aventure", "Romance"), 3.7f, "A-1 Pictures")
    )

    // ==========================================
    // DATA EXPORT & IMPORT (BACKUP / RESTORE)
    // ==========================================

    suspend fun exportBackupJson(): String = withContext(Dispatchers.IO) {
        val backup = CineLogBackup(
            version = 1,
            exportedAt = System.currentTimeMillis(),
            logs = logDao.getAllLogsList(),
            watchlist = watchlistDao.getAllWatchlistList(),
            customLists = customListDao.getAllCustomListsList(),
            customListTitles = customListDao.getAllCustomListTitlesList(),
            seasonProgress = seasonProgressDao.getAllSeasonProgressList()
        )
        val adapter = moshi.adapter(CineLogBackup::class.java)
        adapter.toJson(backup)
    }

    // ==========================================
    // STATISTIQUES DU PROFIL (issue #29)
    // ==========================================

    // Cache en mémoire des métadonnées détaillées par titre afin d'éviter
    // des appels réseau redondants lors du calcul des stats du profil.
    
private fun DbTitleMetaCache.toTitleMeta(): TitleMeta {
    return TitleMeta(
        genres = if (genres.isBlank()) emptyList() else genres.split(",").map { it.trim() }.filter { it.isNotEmpty() },
        studioOrDirector = studioOrDirector,
        voteAverage = voteAverage,
        runtime = runtime
    )
}

private fun TitleMeta.toDbTitleMetaCache(titleId: String): DbTitleMetaCache {
    return DbTitleMetaCache(
        titleId = titleId,
        genres = genres.joinToString(","),
        studioOrDirector = studioOrDirector,
        voteAverage = voteAverage,
        runtime = runtime
    )
}

    private val titleMetaCache = ConcurrentHashMap<String, TitleMeta>()

    val titleMetaCacheFlow: Flow<List<DbTitleMetaCache>> = titleMetaCacheDao?.getAllFlow()
        ?.onEach { list ->
            list.forEach { item ->
                titleMetaCache[item.titleId] = item.toTitleMeta()
            }
        } ?: flowOf(emptyList())

    suspend fun loadTitleMetaCacheFromDb() = withContext(Dispatchers.IO) {
        if (titleMetaCacheDao == null) return@withContext
        val cached = titleMetaCacheDao.getAllList()
        cached.forEach { item ->
            titleMetaCache[item.titleId] = item.toTitleMeta()
        }
    }

    // Récupère les métadonnées détaillées pour tous les titres journalisés.
    // Limité à 3 appels réseau simultanés pour ne pas saturer la connexion.
    suspend fun enrichLogMetadata(logs: List<DbLogEntry>) = coroutineScope {
        val uniqueTitles = logs.map { it.titleId }.distinct()

        if (titleMetaCacheDao != null) {
            val dbCached = titleMetaCacheDao.getByTitleIds(uniqueTitles)
            dbCached.forEach { item ->
                titleMetaCache[item.titleId] = item.toTitleMeta()
            }
        }

        val missing = uniqueTitles.filter { !titleMetaCache.containsKey(it) }
        if (missing.isEmpty()) return@coroutineScope

        val newEntries = ConcurrentHashMap<String, DbTitleMetaCache>()
        val semaphore = Semaphore(3)
        val jobs = missing.map { titleId ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    try {
                        val detail = getTitleDetail(titleId)
                        val meta = TitleMeta(
                            genres = detail.genres,
                            studioOrDirector = detail.studioOrDirector,
                            voteAverage = detail.voteAverage,
                            runtime = detail.runtime
                        )
                        titleMetaCache[titleId] = meta
                        newEntries[titleId] = meta.toDbTitleMetaCache(titleId)
                    } catch (e: Exception) {
                        Log.w(tag, "enrichLogMetadata: impossible de charger $titleId: ${e.localizedMessage}")
                    }
                }
            }
        }
        jobs.forEach { it.await() }

        if (newEntries.isNotEmpty() && titleMetaCacheDao != null) {
            withContext(Dispatchers.IO) {
                titleMetaCacheDao.upsertAll(newEntries.values.toList())
            }
        }
    }

    // Calcule l'ensemble des statistiques du profil à partir des logs et
    // des métadonnées enrichies disponibles dans le cache local.
    fun getProfileStats(logs: List<DbLogEntry>, watchlist: List<DbWatchlist>): ProfileStats {
        if (logs.isEmpty()) return ProfileStats()

        val totalLogs = logs.size
        val averageScore = logs.map { it.note }.average().toFloat()
        val rewatchCount = logs.count { it.revisionnage }

        // Récupération des métadonnées disponibles (titres déjà chargés)
        val enriched = logs.mapNotNull { log -> titleMetaCache[log.titleId]?.let { log to it } }

        // Top genres (sur les titres enrichis)
        val topGenres = enriched
            .flatMap { (_, meta) -> meta.genres }
            .groupingBy { it }.eachCount()
            .toList().sortedByDescending { it.second }.take(5)

        // Top réalisateurs / studios
        val topDirectorsOrStudios = enriched
            .groupingBy { (_, meta) -> meta.studioOrDirector ?: return@groupingBy "Inconnu" }
            .eachCount()
            .filterKeys { it != "Inconnu" && it.isNotBlank() }
            .toList().sortedByDescending { it.second }.take(5)

        // Streak de visionnage (jours consécutifs avec au moins un log)
        val days = logs.map { log ->
            val cal = Calendar.getInstance()
            cal.timeInMillis = log.dateVue
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }.distinct().sorted()

        val (currentStreak, maxStreak) = computeStreaks(days)

        // Année la plus productive (nombre de visionnages par année civile)
        val mostProductiveYear = logs
            .groupingBy { log ->
                val cal = Calendar.getInstance()
                cal.timeInMillis = log.dateVue
                cal.get(Calendar.YEAR).toString()
            }
            .eachCount()
            .maxByOrNull { it.value }
            ?.let { it.key to it.value }

        // Évolution de la note moyenne sur 12 mois glissants
        val monthlyAverageScores = buildMonthlyScoreSeries(logs)

        // Comparaison note perso vs. note communauté
        val deltas = enriched.map { (log, meta) -> log.note - meta.voteAverage }
        val communityScoreDelta = if (deltas.isNotEmpty()) deltas.average().toFloat() else null

        // Temps total estimé (somme des runtimes des titres uniques)
        val totalRuntimeMinutes = logs
            .mapNotNull { log -> titleMetaCache[log.titleId]?.runtime }
            .sum()

        return ProfileStats(
            totalLogs = totalLogs,
            averageScore = averageScore,
            rewatchCount = rewatchCount,
            currentStreak = currentStreak,
            maxStreak = maxStreak,
            mostProductiveYear = mostProductiveYear,
            monthlyAverageScores = monthlyAverageScores,
            topGenres = topGenres,
            topDirectorsOrStudios = topDirectorsOrStudios,
            communityScoreDelta = communityScoreDelta,
            totalRuntimeMinutes = totalRuntimeMinutes
        )
    }

    // Calcule le streak courant et le streak max à partir d'une liste
    // ordonnée de timestamps (minuit, normalisés).
    private fun computeStreaks(days: List<Long>): Pair<Int, Int> {
        if (days.isEmpty()) return 0 to 0
        var current = 1
        var max = 1
        for (i in 1 until days.size) {
            val diff = (days[i] - days[i - 1]) / (24 * 60 * 60 * 1000)
            current = if (diff == 1L) current + 1 else 1
            if (current > max) max = current
        }
        // Un streak "courant" n'est valide que si le dernier jour loggé est aujourd'hui ou hier
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val lastDay = days.last()
        val activeCurrent = if (today - lastDay <= 24L * 60 * 60 * 1000) current else 0
        return activeCurrent to max
    }

    // Construit la série des notes moyennes mensuelles sur les 12 derniers mois
    // (ordre chronologique, mois sans log rapportés avec une note de 0).
    private fun buildMonthlyScoreSeries(logs: List<DbLogEntry>): List<Pair<String, Float>> {
        val cal = Calendar.getInstance()
        val now = System.currentTimeMillis()
        val result = mutableListOf<Pair<String, Float>>()
        for (i in 11 downTo 0) {
            cal.timeInMillis = now
            cal.add(Calendar.MONTH, -i)
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH)
            val label = "${String.format("%02d", month + 1)}/${year % 100}"
            val monthLogs = logs.filter { log ->
                val logCal = Calendar.getInstance()
                logCal.timeInMillis = log.dateVue
                logCal.get(Calendar.YEAR) == year && logCal.get(Calendar.MONTH) == month
            }
            val avg = if (monthLogs.isNotEmpty()) monthLogs.map { it.note }.average().toFloat() else 0f
            result.add(label to avg)
        }
        return result
    }

    suspend fun exportBackupCsv(): String = withContext(Dispatchers.IO) {
        val logs = logDao.getAllLogsList()
        val watchlist = watchlistDao.getAllWatchlistList()
        val customLists = customListDao.getAllCustomListsList()
        val customListTitles = customListDao.getAllCustomListTitlesList()

        val sb = StringBuilder()
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())

        sb.append("=== LOGS DE VISIONNAGE ===\n")
        sb.append("ID,ID_Titre,Type,Titre,Date_Vue,Note,Critique,Revisionnage,Spoiler,Collection\n")
        for (e in logs) {
            val dateStr = dateFormat.format(java.util.Date(e.dateVue))
            sb.append("${e.id},\"${e.titleId}\",\"${e.titleType}\",\"${escapeCsv(e.titleName)}\",\"$dateStr\",${e.note},\"${escapeCsv(e.critique)}\",${e.revisionnage},${e.spoiler},\"${escapeCsv(e.collectionName ?: "")}\"\n")
        }

        sb.append("\n=== WATCHLIST ===\n")
        sb.append("ID_Titre,Type,Titre,Date_Ajout,Annee,Genres,Note_Moyenne,Collection\n")
        for (w in watchlist) {
            val dateStr = dateFormat.format(java.util.Date(w.dateAdded))
            sb.append("\"${w.titleId}\",\"${w.titleType}\",\"${escapeCsv(w.titleName)}\",\"$dateStr\",\"${w.titleYear ?: ""}\",\"${escapeCsv(w.titleGenres ?: "")}\",${w.titleVoteAverage ?: ""},\"${escapeCsv(w.collectionName ?: "")}\"\n")
        }

        sb.append("\n=== LISTES PERSONNALISEES ===\n")
        sb.append("ID_Liste,Nom_Liste,Description\n")
        for (l in customLists) {
            sb.append("${l.id},\"${escapeCsv(l.name)}\",\"${escapeCsv(l.description)}\"\n")
        }

        sb.append("\n=== TITRES EN LISTES ===\n")
        sb.append("ID_Entree,ID_Liste,ID_Titre,Type,Titre,Ordre\n")
        for (t in customListTitles) {
            sb.append("${t.id},${t.listId},\"${t.titleId}\",\"${t.titleType}\",\"${escapeCsv(t.titleName)}\",${t.orderIndex}\n")
        }

        sb.toString()
    }

    private fun escapeCsv(text: String): String {
        return text.replace("\"", "\"\"")
    }

    suspend fun importBackup(content: String): ImportSummary = withContext(Dispatchers.IO) {
        val trimmed = content.trim()
        if (trimmed.startsWith("{")) {
            val adapter = moshi.adapter(CineLogBackup::class.java)
            val backup = adapter.fromJson(trimmed) ?: throw IllegalArgumentException("Format JSON invalide")
            if (backup.logs.isNotEmpty()) logDao.insertLogs(backup.logs)
            if (backup.watchlist.isNotEmpty()) watchlistDao.insertWatchlists(backup.watchlist)
            if (backup.customLists.isNotEmpty()) customListDao.insertCustomLists(backup.customLists)
            if (backup.customListTitles.isNotEmpty()) customListDao.insertCustomListTitles(backup.customListTitles)
            if (backup.seasonProgress.isNotEmpty()) seasonProgressDao.upsertAll(backup.seasonProgress)

            ImportSummary(
                logsCount = backup.logs.size,
                watchlistCount = backup.watchlist.size,
                customListsCount = backup.customLists.size,
                seasonProgressCount = backup.seasonProgress.size
            )
        } else {
            val (logs, watchlist) = parseCsvImport(trimmed)
            if (logs.isEmpty() && watchlist.isEmpty()) {
                throw IllegalArgumentException("Fichier CSV non reconnu ou vide")
            }
            if (logs.isNotEmpty()) logDao.insertLogs(logs)
            if (watchlist.isNotEmpty()) watchlistDao.insertWatchlists(watchlist)

            ImportSummary(
                logsCount = logs.size,
                watchlistCount = watchlist.size,
                customListsCount = 0,
                seasonProgressCount = 0
            )
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    current.append('"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString())
                current.clear()
            } else {
                current.append(c)
            }
            i++
        }
        result.add(current.toString())
        return result
    }

    private fun parseCsvImport(content: String): Pair<List<DbLogEntry>, List<DbWatchlist>> {
        val logs = mutableListOf<DbLogEntry>()
        val watchlist = mutableListOf<DbWatchlist>()

        var currentSection = ""
        val lines = content.lines()
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isBlank()) continue
            if (line.startsWith("===")) {
                when {
                    line.contains("LOGS") -> currentSection = "LOGS"
                    line.contains("WATCHLIST") -> currentSection = "WATCHLIST"
                    else -> currentSection = ""
                }
                continue
            }

            val cols = parseCsvLine(line)
            if (cols.isEmpty()) continue

            if (cols[0].equals("ID", ignoreCase = true) || cols[0].equals("ID_Titre", ignoreCase = true) || cols[0].startsWith("ID_")) {
                continue
            }

            if (currentSection == "LOGS" || (currentSection == "" && cols.size >= 8)) {
                try {
                    val id = cols.getOrNull(0)?.toIntOrNull() ?: 0
                    val titleId = cols.getOrNull(1) ?: continue
                    val type = cols.getOrNull(2) ?: "FILM"
                    val name = cols.getOrNull(3) ?: ""
                    val dateStr = cols.getOrNull(4) ?: ""
                    val dateVue = parseDateOrTimestamp(dateStr)
                    val note = cols.getOrNull(5)?.toFloatOrNull() ?: 0f
                    val critique = cols.getOrNull(6) ?: ""
                    val revisionnage = cols.getOrNull(7)?.toBoolean() ?: false
                    val spoiler = cols.getOrNull(8)?.toBoolean() ?: false
                    val collectionName = cols.getOrNull(9).takeIf { !it.isNullOrBlank() }

                    logs.add(
                        DbLogEntry(
                            id = id,
                            titleId = titleId,
                            titleType = type,
                            titleName = name,
                            titlePosterUrl = null,
                            dateVue = dateVue,
                            note = note,
                            critique = critique,
                            revisionnage = revisionnage,
                            spoiler = spoiler,
                            collectionName = collectionName
                        )
                    )
                } catch (e: Exception) {
                    // Ignore malformed lines
                }
            } else if (currentSection == "WATCHLIST") {
                try {
                    val titleId = cols.getOrNull(0) ?: continue
                    val type = cols.getOrNull(1) ?: "FILM"
                    val name = cols.getOrNull(2) ?: ""
                    val dateStr = cols.getOrNull(3) ?: ""
                    val dateAdded = parseDateOrTimestamp(dateStr)
                    val year = cols.getOrNull(4)
                    val genres = cols.getOrNull(5)
                    val voteAverage = cols.getOrNull(6)?.toFloatOrNull()
                    val collectionName = cols.getOrNull(7).takeIf { !it.isNullOrBlank() }

                    watchlist.add(
                        DbWatchlist(
                            titleId = titleId,
                            titleType = type,
                            titleName = name,
                            titlePosterUrl = null,
                            dateAdded = dateAdded,
                            titleYear = year,
                            titleGenres = genres,
                            titleVoteAverage = voteAverage,
                            collectionName = collectionName
                        )
                    )
                } catch (e: Exception) {
                    // Ignore malformed lines
                }
            }
        }

        return Pair(logs, watchlist)
    }

    private fun parseDateOrTimestamp(str: String): Long {
        val longVal = str.toLongOrNull()
        if (longVal != null) return longVal
        return try {
            val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            format.parse(str)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}
