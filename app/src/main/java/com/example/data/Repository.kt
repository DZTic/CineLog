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
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

enum class TitleType {
    FILM, SERIE, ANIME;

    val displayName: String
        get() = when (this) {
            FILM -> "Film"
            SERIE -> "S?rie"
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
    val collectionPosterUrl: String? = null // official saga poster, distinct from this movie's own poster
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
        val rawId = rawIdString.toIntOrNull() ?: throw IllegalArgumentException("ID num?rique invalide: $rawIdString")

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
                    jikanApi.getTopAnime(page = page).data.map { it.toCineTitle() }
                } catch (e: Exception) {
                    Log.e(tag, "Error fetching top anime: ${e.localizedMessage}")
                    if (page == 1) getFallbackAnime() else emptyList()
                }
            }
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
            collectionPosterUrl = belongsToCollection?.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
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
        CineTitle("movie_157336", TitleType.FILM, "Interstellar", "2014", "https://image.tmdb.org/t/p/w500/gEU2vYvKext9hqg6vXXndccOWmO.jpg", "Un voyage interstellaire pour sauver l'humanit?.", listOf("Aventure", "Science-Fiction"), 4.3f, "Christopher Nolan"),
        CineTitle("movie_680", TitleType.FILM, "Pulp Fiction", "1994", "https://image.tmdb.org/t/p/w500/fIE3lYTE9An6Y8Zg8f2clg6cuyp.jpg", "L'odyss?e sanglante et ironique de truands de bas ?tage.", listOf("Thriller", "Crime"), 4.5f, "Quentin Tarantino"),
        CineTitle("movie_129", TitleType.FILM, "Le Voyage de Chihiro", "2001", "https://image.tmdb.org/t/p/w500/39wmItIWsg6s9XRY7gZg92zAsas.jpg", "Une jeune fille se retrouve bloqu?e dans le monde des esprits.", listOf("Animation", "Fantastique"), 4.6f, "Hayao Miyazaki")
    )

    private fun getFallbackSeries(): List<CineTitle> = listOf(
        CineTitle("tv_1396", TitleType.SERIE, "Breaking Bad", "2008", "https://image.tmdb.org/t/p/w500/ztk6scNlh6g69gXv7qPG9836g9n.jpg", "Un prof de chimie malade devient baron de la drogue.", listOf("Drame", "Crime"), 4.5f, "Vince Gilligan"),
        CineTitle("tv_1399", TitleType.SERIE, "Game of Thrones", "2011", "https://image.tmdb.org/t/p/w500/1XS19CfS3Z79YvHG6go4gH6gX4C.jpg", "Lutte de pouvoir pour le tr?ne de fer de Westeros.", listOf("Drame", "Fantastique"), 4.2f, "David Benioff"),
        CineTitle("tv_456", TitleType.SERIE, "The Simpsons", "1989", "https://image.tmdb.org/t/p/w500/77u7S2bAt795X8p66A59fXnJ8jX.jpg", "Le quotidien d?jant? d'une famille de Springfield.", listOf("Animation", "Com?die"), 4.0f, "Matt Groening")
    )

    private fun getFallbackAnime(): List<CineTitle> = listOf(
        CineTitle("anime_5114", TitleType.ANIME, "Fullmetal Alchemist: Brotherhood", "2009", "https://cdn.myanimelist.net/images/anime/1208/94745l.jpg", "Deux fr?res alchimistes cherchent ? r?cup?rer leurs corps.", listOf("Action", "Drame", "Fantastique"), 4.6f, "Bones"),
        CineTitle("anime_38524", TitleType.ANIME, "Shingeki no Kyojin Season 3 Part 2", "2019", "https://cdn.myanimelist.net/images/anime/1517/100633l.jpg", "La reconqu?te du Mur Maria commence, face aux Titans.", listOf("Action", "Drame", "Myst?re"), 4.5f, "Wit Studio"),
        CineTitle("anime_21", TitleType.ANIME, "One Piece", "1999", "https://cdn.myanimelist.net/images/anime/1244/138851l.jpg", "Monkey D. Luffy explore Grand Line ? la recherche du tr?sor ultime.", listOf("Action", "Aventure", "Com?die"), 4.4f, "Toei Animation")
    )
}
