package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

// ==========================================
// JIKAN (ANIME) API MODELS & INTERFACE
// ==========================================

@JsonClass(generateAdapter = true)
data class JikanGenre(
    @Json(name = "name") val name: String? = null
)

@JsonClass(generateAdapter = true)
data class JikanStudio(
    @Json(name = "name") val name: String? = null
)

@JsonClass(generateAdapter = true)
data class JikanImageJpg(
    @Json(name = "image_url") val imageUrl: String? = null,
    @Json(name = "large_image_url") val largeImageUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class JikanImages(
    @Json(name = "jpg") val jpg: JikanImageJpg? = null
)

@JsonClass(generateAdapter = true)
data class JikanAnimeData(
    @Json(name = "mal_id") val malId: Int = 0,
    @Json(name = "title") val title: String? = null,
    @Json(name = "synopsis") val synopsis: String? = null,
    @Json(name = "images") val images: JikanImages? = null,
    @Json(name = "score") val score: Float? = null,
    @Json(name = "year") val year: Int? = null,
    @Json(name = "genres") val genres: List<JikanGenre?>? = null,
    @Json(name = "studios") val studios: List<JikanStudio?>? = null,
    @Json(name = "episodes") val episodes: Int? = null
)

@JsonClass(generateAdapter = true)
data class JikanAnimeSearchResponse(
    @Json(name = "data") val data: List<JikanAnimeData>? = null
)

@JsonClass(generateAdapter = true)
data class JikanAnimeDetailResponse(
    @Json(name = "data") val data: JikanAnimeData? = null
)

interface JikanApiService {
    @GET("anime")
    suspend fun searchAnime(
        @Query("q") query: String,
        @Query("limit") limit: Int = 20,
        @Query("page") page: Int = 1
    ): JikanAnimeSearchResponse

    @GET("anime/{id}/full")
    suspend fun getAnimeDetail(
        @Path("id") id: Int
    ): JikanAnimeDetailResponse

    @GET("top/anime")
    suspend fun getTopAnime(
        @Query("limit") limit: Int = 20,
        @Query("page") page: Int = 1
    ): JikanAnimeSearchResponse
}

// ==========================================
// TMDB (MOVIES & SERIES) API MODELS & INTERFACE
// ==========================================

@JsonClass(generateAdapter = true)
data class TmdbMovieResult(
    @Json(name = "id") val id: Int,
    @Json(name = "title") val title: String,
    @Json(name = "overview") val overview: String?,
    @Json(name = "release_date") val releaseDate: String?,
    @Json(name = "poster_path") val posterPath: String?,
    @Json(name = "vote_average") val voteAverage: Float?
)

@JsonClass(generateAdapter = true)
data class TmdbMovieSearchResponse(
    @Json(name = "results") val results: List<TmdbMovieResult>
)

@JsonClass(generateAdapter = true)
data class TmdbTvResult(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String,
    @Json(name = "overview") val overview: String?,
    @Json(name = "first_air_date") val firstAirDate: String?,
    @Json(name = "poster_path") val posterPath: String?,
    @Json(name = "vote_average") val voteAverage: Float?,
    @Json(name = "genre_ids") val genreIds: List<Int>? = null,
    @Json(name = "origin_country") val originCountry: List<String>? = null,
    @Json(name = "original_language") val originalLanguage: String? = null,
    @Json(name = "episode_run_time") val episodeRunTime: List<Int>? = null
)

@JsonClass(generateAdapter = true)
data class TmdbTvSearchResponse(
    @Json(name = "results") val results: List<TmdbTvResult>
)

@JsonClass(generateAdapter = true)
data class TmdbGenre(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String
)

@JsonClass(generateAdapter = true)
data class TmdbCast(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String,
    @Json(name = "character") val character: String?,
    @Json(name = "profile_path") val profilePath: String?
)

@JsonClass(generateAdapter = true)
data class TmdbCredits(
    @Json(name = "cast") val cast: List<TmdbCast>?
)

@JsonClass(generateAdapter = true)
data class TmdbSeason(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String,
    @Json(name = "season_number") val seasonNumber: Int,
    @Json(name = "episode_count") val episodeCount: Int,
    @Json(name = "poster_path") val posterPath: String?
)

@JsonClass(generateAdapter = true)
data class TmdbCollectionRef(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String,
    @Json(name = "poster_path") val posterPath: String?
)

@JsonClass(generateAdapter = true)
data class TmdbMovieDetail(
    @Json(name = "id") val id: Int,
    @Json(name = "title") val title: String,
    @Json(name = "overview") val overview: String?,
    @Json(name = "release_date") val releaseDate: String?,
    @Json(name = "poster_path") val posterPath: String?,
    @Json(name = "vote_average") val voteAverage: Float?,
    @Json(name = "genres") val genres: List<TmdbGenre>?,
    @Json(name = "runtime") val runtime: Int?,
    @Json(name = "credits") val credits: TmdbCredits?,
    @Json(name = "belongs_to_collection") val belongsToCollection: TmdbCollectionRef? = null
)

@JsonClass(generateAdapter = true)
data class TmdbCollectionDetail(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String,
    @Json(name = "overview") val overview: String?,
    @Json(name = "poster_path") val posterPath: String?,
    @Json(name = "parts") val parts: List<TmdbMovieResult>
)

@JsonClass(generateAdapter = true)
data class TmdbTvDetail(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String,
    @Json(name = "overview") val overview: String?,
    @Json(name = "first_air_date") val firstAirDate: String?,
    @Json(name = "poster_path") val posterPath: String?,
    @Json(name = "vote_average") val voteAverage: Float?,
    @Json(name = "genres") val genres: List<TmdbGenre>?,
    @Json(name = "seasons") val seasons: List<TmdbSeason>?,
    @Json(name = "credits") val credits: TmdbCredits?,
    @Json(name = "origin_country") val originCountry: List<String>? = null,
    @Json(name = "original_language") val originalLanguage: String? = null,
    @Json(name = "episode_run_time") val episodeRunTime: List<Int>? = null
)

// Standard Trending responses
@JsonClass(generateAdapter = true)
data class TmdbTrendingMovieResponse(
    @Json(name = "results") val results: List<TmdbMovieResult>
)

@JsonClass(generateAdapter = true)
data class TmdbTrendingTvResponse(
    @Json(name = "results") val results: List<TmdbTvResult>
)

interface TmdbApiService {
    @GET("search/movie")
    suspend fun searchMovie(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("language") language: String = "fr-FR",
        @Query("page") page: Int = 1
    ): TmdbMovieSearchResponse

    @GET("search/tv")
    suspend fun searchTv(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("language") language: String = "fr-FR",
        @Query("page") page: Int = 1
    ): TmdbTvSearchResponse

    @GET("movie/{id}")
    suspend fun getMovieDetail(
        @Path("id") id: Int,
        @Query("api_key") apiKey: String,
        @Query("append_to_response") appendToResponse: String = "credits",
        @Query("language") language: String = "fr-FR"
    ): TmdbMovieDetail

    @GET("tv/{id}")
    suspend fun getTvDetail(
        @Path("id") id: Int,
        @Query("api_key") apiKey: String,
        @Query("append_to_response") appendToResponse: String = "credits",
        @Query("language") language: String = "fr-FR"
    ): TmdbTvDetail

    @GET("collection/{id}")
    suspend fun getCollection(
        @Path("id") id: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "fr-FR"
    ): TmdbCollectionDetail

    @GET("trending/movie/week")
    suspend fun getTrendingMovies(
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "fr-FR",
        @Query("page") page: Int = 1
    ): TmdbTrendingMovieResponse

    @GET("trending/tv/week")
    suspend fun getTrendingTv(
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "fr-FR",
        @Query("page") page: Int = 1
    ): TmdbTrendingTvResponse
}
