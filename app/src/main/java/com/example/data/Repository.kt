package com.example.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

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
    val seasons: List<CineSeason> = emptyList()
)

class Repository(
    private val logDao: LogDao,
    private val watchlistDao: WatchlistDao,
    private val customListDao: CustomListDao,
    private val preferenceManager: PreferenceManager
) {
    private val tag = "Repository"
    
    private val moshi: com.squareup.moshi.Moshi by lazy {
        com.squareup.moshi.Moshi.Builder()
            .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
            .build()
    }

    // Set up Retrofit for Jikan (Anime)
    private val jikanApi: JikanApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.jikan.moe/v4/")
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(JikanApiService::class.java)
    }

    // Set up Retrofit for TMDB (Movies / TV)
    private val tmdbApi: TmdbApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/3/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(TmdbApiService::class.java)
    }

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

    suspend fun removeFromWatchlist(titleId: String) = withContext(Dispatchers.IO) {
        watchlistDao.deleteFromWatchlist(titleId)
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

    // ==========================================
    // REMOTE API OPERATIONS & CONVERSIONS
    // ==========================================

    private fun getTmdbKey(): String {
        return preferenceManager.getTmdbApiKey()
    }

    /**
     * Search movies, TV series, or anime dynamically.
     */
    suspend fun searchTitles(query: String, typeFilter: TitleType? = null): List<CineTitle> = coroutineScope {
        if (query.trim().isEmpty()) return@coroutineScope emptyList()

        val tmdbKey = getTmdbKey()
        val hasTmdbKey = tmdbKey.isNotEmpty()

        val filmsDeferred = if (hasTmdbKey && (typeFilter == null || typeFilter == TitleType.FILM)) {
            async(Dispatchers.IO) {
                try {
                    val response = tmdbApi.searchMovie(tmdbKey, query)
                    response.results.map { it.toCineTitle() }
                } catch (e: Exception) {
                    Log.e(tag, "Error searching TMDB movie: ${e.localizedMessage}")
                    emptyList()
                }
            }
        } else {
            null
        }

        val seriesDeferred = if (hasTmdbKey && (typeFilter == null || typeFilter == TitleType.SERIE)) {
            async(Dispatchers.IO) {
                try {
                    val response = tmdbApi.searchTv(tmdbKey, query)
                    response.results.map { it.toCineTitle() }
                } catch (e: Exception) {
                    Log.e(tag, "Error searching TMDB TV: ${e.localizedMessage}")
                    emptyList()
                }
            }
        } else {
            null
        }

        val animeDeferred = if (typeFilter == null || typeFilter == TitleType.ANIME) {
            async(Dispatchers.IO) {
                try {
                    val response = jikanApi.searchAnime(query)
                    response.data.map { it.toCineTitle() }
                } catch (e: Exception) {
                    Log.e(tag, "Error searching Jikan Anime: ${e.localizedMessage}")
                    emptyList()
                }
            }
        } else {
            null
        }

        val films = filmsDeferred?.await() ?: emptyList()
        val series = seriesDeferred?.await() ?: emptyList()
        val anime = animeDeferred?.await() ?: emptyList()

        return@coroutineScope (films + series + anime).sortedByDescending { it.voteAverage }
    }

    /**
     * Fetch a specific item from its uniform identifier.
     */
    suspend fun getTitleDetail(id: String): CineTitle = withContext(Dispatchers.IO) {
        val parts = id.split("_", limit = 2)
        if (parts.size < 2) throw IllegalArgumentException("Format ID invalide: $id")

        val prefix = parts[0]
        val rawIdString = parts[1]
        val rawId = rawIdString.toIntOrNull() ?: throw IllegalArgumentException("ID numérique invalide: $rawIdString")

        when (prefix) {
            "movie" -> {
                val tmdbKey = getTmdbKey()
                if (tmdbKey.isEmpty()) throw IllegalStateException("Clé API TMDB manquante dans les Paramètres.")
                val movie = tmdbApi.getMovieDetail(rawId, tmdbKey)
                movie.toCineTitle()
            }
            "tv" -> {
                val tmdbKey = getTmdbKey()
                if (tmdbKey.isEmpty()) throw IllegalStateException("Clé API TMDB manquante dans les Paramètres.")
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

    /**
     * Get popular/trending content (Accueil / Découverte).
     */
    suspend fun getTrendingOrPopular(type: TitleType): List<CineTitle> = withContext(Dispatchers.IO) {
        val tmdbKey = getTmdbKey()
        when (type) {
            TitleType.FILM -> {
                if (tmdbKey.isEmpty()) {
                    return@withContext getFallbackFilms()
                }
                try {
                    tmdbApi.getTrendingMovies(tmdbKey).results.map { it.toCineTitle() }
                } catch (e: Exception) {
                    Log.e(tag, "Error fetching trending movies: ${e.localizedMessage}")
                    getFallbackFilms()
                }
            }
            TitleType.SERIE -> {
                if (tmdbKey.isEmpty()) {
                    return@withContext getFallbackSeries()
                }
                try {
                    tmdbApi.getTrendingTv(tmdbKey).results.map { it.toCineTitle() }
                } catch (e: Exception) {
                    Log.e(tag, "Error fetching trending TV: ${e.localizedMessage}")
                    getFallbackSeries()
                }
            }
            TitleType.ANIME -> {
                try {
                    jikanApi.getTopAnime().data.map { it.toCineTitle() }
                } catch (e: Exception) {
                    Log.e(tag, "Error fetching top anime: ${e.localizedMessage}")
                    getFallbackAnime()
                }
            }
        }
    }

    // ==========================================
    // DOMAIN MODEL MAPPING EXTENSIONS
    // ==========================================

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
            voteAverage = (voteAverage ?: 0f) / 2f // Scale from TMDB's 0-10 to 0-5
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
            voteAverage = (voteAverage ?: 0f) / 2f // Scale from TMDB's 0-10 to 0-5
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
            voteAverage = (voteAverage ?: 0f) / 2f, // Scale from TMDB's 0-10 to 0-5
            studioOrDirector = director
        )
    }

    private fun TmdbTvDetail.toCineTitle(): CineTitle {
        val y = firstAirDate?.take(4) ?: "N/A"
        val poster = if (posterPath != null) "https://image.tmdb.org/t/p/w500$posterPath" else null
        val director = credits?.cast?.take(3)?.joinToString { it.name } ?: "N/A"
        return CineTitle(
            id = "tv_$id",
            type = TitleType.SERIE,
            title = name,
            year = y,
            posterUrl = poster,
            synopsis = overview ?: "",
            genres = genres?.map { it.name } ?: emptyList(),
            voteAverage = (voteAverage ?: 0f) / 2f, // Scale from TMDB's 0-10 to 0-5
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
        } else {
            emptyList()
        }
        return CineTitle(
            id = "anime_$malId",
            type = TitleType.ANIME,
            title = title,
            year = y,
            posterUrl = poster,
            synopsis = synopsis ?: "",
            genres = genres?.map { it.name } ?: emptyList(),
            voteAverage = (score ?: 0f) / 2f, // Scale from 0-10 to 0-5
            studioOrDirector = studio,
            seasons = mappedSeasons
        )
    }

    // ==========================================
    // PRE-PACKAGED COLD-START FALLBACK LISTS
    // ==========================================

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
        CineTitle("anime_38524", TitleType.ANIME, "Shingeki no Kyojin Season 3 Part 2", "2019", "https://cdn.myanimelist.net/images/anime/1517/100633l.jpg", "La reconquête du Mur Maria commence, face aux Titans.", listOf("Action", "Drame", "Mystère"), 4.5f, "Wit Studio"),
        CineTitle("anime_21", TitleType.ANIME, "One Piece", "1999", "https://cdn.myanimelist.net/images/anime/1244/138851l.jpg", "Monkey D. Luffy explore Grand Line à la recherche du trésor ultime.", listOf("Action", "Aventure", "Comédie"), 4.4f, "Toei Animation")
    )
}
