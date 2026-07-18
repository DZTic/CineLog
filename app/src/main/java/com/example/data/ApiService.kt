package com.example.data

import com.squareup.moshi.Json
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

// ==========================================
// JIKAN (ANIME) API MODELS & INTERFACE
// ==========================================

data class JikanGenre(
    @Json(name = "name") val name: String
)

data class JikanStudio(
    @Json(name = "name") val name: String
)

data class JikanImageJpg(
    @Json(name = "image_url") val imageUrl: String?,
    @Json(name = "large_image_url") val largeImageUrl: String?
)

data class JikanImages(
    @Json(name = "jpg") val jpg: JikanImageJpg?
)

data class JikanAnimeData(
    @Json(name = "mal_id") val malId: Int,
    @Json(name = "title") val title: String,
    @Json(name = "synopsis") val synopsis: String?,
    @Json(name = "images") val images: JikanImages?,
    @Json(name = "score") val score: Float?,
    @Json(name = "year") val year: Int?,
    @Json(name = "genres") val genres: List<JikanGenre>?,
    @Json(name = "studios") val studios: List<JikanStudio>?,
    @Json(name = "episodes") val episodes: Int?
)

data class JikanAnimeSearchResponse(
    @Json(name = "data") val data: List<JikanAnimeData>
)

data class JikanAnimeDetailResponse(
    @Json(name = "data") val data: JikanAnimeData
)

interface JikanApiService {
    @GET("anime")
    suspend fun searchAnime(
        @Query("q") query: String,
        @Query("limit") limit: Int = 20
    ): JikanAnimeSearchResponse

    @GET("anime/{id}/full")
    suspend fun getAnimeDetail(
        @Path("id") id: Int
    ): JikanAnimeDetailResponse

    @GET("top/anime")
    suspend fun getTopAnime(
        @Query("limit") limit: Int = 20
    ): JikanAnimeSearchResponse
}

// ==========================================
// TMDB (MOVIES & SERIES) API MODELS & INTERFACE
// ==========================================

data class TmdbMovieResult(
    @Json(name = "id") val id: Int,
    @Json(name = "title") val title: String,
    @Json(name = "overview") val overview: String?,
    @Json(name = "release_date") val releaseDate: String?,
    @Json(name = "poster_path") val posterPath: String?,
    @Json(name = "vote_average") val voteAverage: Float?
)

data class TmdbMovieSearchResponse(
    @Json(name = "results") val results: List<TmdbMovieResult>
)

data class TmdbTvResult(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String,
    @Json(name = "overview") val overview: String?,
    @Json(name = "first_air_date") val firstAirDate: String?,
    @Json(name = "poster_path") val posterPath: String?,
    @Json(name = "vote_average") val voteAverage: Float?,
    @Json(name = "genre_ids") val genreIds: List<Int>? = null,
    @Json(name = "origin_country") val originCountry: List<String>? = null,
    @Json(name = "original_language") val originalLanguage: String? = null
)

data class TmdbTvSearchResponse(
    @Json(name = "results") val results: List<TmdbTvResult>
)

data class TmdbGenre(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String
)

data class TmdbCast(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String,
    @Json(name = "character") val character: String?,
    @Json(name = "profile_path") val profilePath: String?
)

data class TmdbCredits(
    @Json(name = "cast") val cast: List<TmdbCast>?
)

data class TmdbSeason(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String,
    @Json(name = "season_number") val seasonNumber: Int,
    @Json(name = "episode_count") val episodeCount: Int,
    @Json(name = "poster_path") val posterPath: String?
)

data class TmdbCollectionRef(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String,
    @Json(name = "poster_path") val posterPath: String?
)

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

data class TmdbCollectionDetail(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String,
    @Json(name = "overview") val overview: String?,
    @Json(name = "poster_path") val posterPath: String?,
    @Json(name = "parts") val parts: List<TmdbMovieResult>
)

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
    @Json(name = "original_language") val originalLanguage: String? = null
)

// Standard Trending responses
data class TmdbTrendingMovieResponse(
    @Json(name = "results") val results: List<TmdbMovieResult>
)

data class TmdbTrendingTvResponse(
    @Json(name = "results") val results: List<TmdbTvResult>
)

interface TmdbApiService {
    @GET("search/movie")
    suspend fun searchMovie(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("language") language: String = "fr-FR"
    ): TmdbMovieSearchResponse

    @GET("search/tv")
    suspend fun searchTv(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("language") language: String = "fr-FR"
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
        @Query("language") language: String = "fr-FR"
    ): TmdbTrendingMovieResponse

    @GET("trending/tv/week")
    suspend fun getTrendingTv(
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "fr-FR"
    ): TmdbTrendingTvResponse
}
