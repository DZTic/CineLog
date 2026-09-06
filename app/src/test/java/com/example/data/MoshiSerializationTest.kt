package com.example.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MoshiSerializationTest {

    private lateinit var moshi: Moshi

    @Before
    fun setup() {
        moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    @Test
    fun testTmdbMovieResultSerialization() {
        val json = """
            {
                "id": 550,
                "title": "Fight Club",
                "overview": "An insomniac office worker...",
                "release_date": "1999-10-15",
                "poster_path": "/pB8BM7pdSp6B6Ih7QZ4DrQ3PmJK.jpg",
                "vote_average": 8.4
            }
        """.trimIndent()

        val adapter = moshi.adapter(TmdbMovieResult::class.java)
        val movie = adapter.fromJson(json)

        assertNotNull(movie)
        assertEquals(550, movie!!.id)
        assertEquals("Fight Club", movie.title)
        assertEquals("/pB8BM7pdSp6B6Ih7QZ4DrQ3PmJK.jpg", movie.posterPath)
        assertEquals(8.4f, movie.voteAverage ?: 0f, 0.01f)

        // Test serialization round-trip
        val serialized = adapter.toJson(movie)
        assertTrue(serialized.contains("\"id\":550"))
        assertTrue(serialized.contains("\"title\":\"Fight Club\""))
    }

    @Test
    fun testTmdbTvResultSerialization() {
        val json = """
            {
                "id": 1399,
                "name": "Game of Thrones",
                "overview": "Seven noble families fight...",
                "first_air_date": "2011-04-17",
                "poster_path": "/u3bZgnGQ9T01sWNhyveQz0wH0Hl.jpg",
                "vote_average": 8.4,
                "genre_ids": [18, 10765],
                "origin_country": ["US"],
                "original_language": "en"
            }
        """.trimIndent()

        val adapter = moshi.adapter(TmdbTvResult::class.java)
        val tv = adapter.fromJson(json)

        assertNotNull(tv)
        assertEquals(1399, tv!!.id)
        assertEquals("Game of Thrones", tv.name)
        assertEquals(listOf(18, 10765), tv.genreIds)
        assertEquals(listOf("US"), tv.originCountry)
        assertEquals("en", tv.originalLanguage)
    }

    @Test
    fun testTmdbMovieDetailWithBelongsToCollection() {
        val json = """
            {
                "id": 671,
                "title": "Harry Potter and the Philosopher's Stone",
                "overview": "An orphaned boy enrolls in a school of wizardry...",
                "release_date": "2001-11-16",
                "poster_path": "/wuMc08IPKEatf9rnMNXvIDxqP4W.jpg",
                "vote_average": 7.9,
                "runtime": 152,
                "genres": [
                    {"id": 12, "name": "Adventure"},
                    {"id": 14, "name": "Fantasy"}
                ],
                "credits": {
                    "cast": [
                        {"id": 10980, "name": "Daniel Radcliffe", "character": "Harry Potter", "profile_path": "/k0c1q.jpg"}
                    ]
                },
                "belongs_to_collection": {
                    "id": 1241,
                    "name": "Harry Potter Collection",
                    "poster_path": "/collection.jpg"
                }
            }
        """.trimIndent()

        val adapter = moshi.adapter(TmdbMovieDetail::class.java)
        val detail = adapter.fromJson(json)

        assertNotNull(detail)
        assertEquals(671, detail!!.id)
        assertEquals(152, detail.runtime)
        assertEquals(2, detail.genres?.size)
        assertEquals("Adventure", detail.genres?.first()?.name)
        assertNotNull(detail.credits)
        assertEquals(1, detail.credits?.cast?.size)
        assertEquals("Daniel Radcliffe", detail.credits?.cast?.first()?.name)
        assertNotNull(detail.belongsToCollection)
        assertEquals(1241, detail.belongsToCollection?.id)
        assertEquals("Harry Potter Collection", detail.belongsToCollection?.name)
    }

    @Test
    fun testJikanAnimeDataSerialization() {
        val json = """
            {
                "mal_id": 5114,
                "title": "Fullmetal Alchemist: Brotherhood",
                "synopsis": "After a horrific alchemy experiment goes wrong...",
                "score": 9.1,
                "year": 2009,
                "episodes": 64,
                "images": {
                    "jpg": {
                        "image_url": "https://cdn.myanimelist.net/images/anime/1223/96541.jpg",
                        "large_image_url": "https://cdn.myanimelist.net/images/anime/1223/96541l.jpg"
                    }
                },
                "genres": [
                    {"name": "Action"},
                    {"name": "Adventure"}
                ],
                "studios": [
                    {"name": "Bones"}
                ]
            }
        """.trimIndent()

        val adapter = moshi.adapter(JikanAnimeData::class.java)
        val anime = adapter.fromJson(json)

        assertNotNull(anime)
        assertEquals(5114, anime!!.malId)
        assertEquals("Fullmetal Alchemist: Brotherhood", anime.title)
        assertEquals(9.1f, anime.score ?: 0f, 0.01f)
        assertEquals(64, anime.episodes)
        assertEquals("https://cdn.myanimelist.net/images/anime/1223/96541l.jpg", anime.images?.jpg?.largeImageUrl)
        assertEquals("Action", anime.genres?.first()?.name)
        assertEquals("Bones", anime.studios?.first()?.name)
    }

    @Test
    fun testCineLogBackupSerializationWithEntities() {
        val backup = CineLogBackup(
            version = 1,
            exportedAt = 1700000000000L,
            logs = listOf(
                DbLogEntry(
                    id = 1,
                    titleId = "movie_550",
                    titleType = "FILM",
                    titleName = "Fight Club",
                    titlePosterUrl = "/poster.jpg",
                    dateVue = 1700000000000L,
                    note = 5.0f,
                    critique = "Chef d'œuvre",
                    revisionnage = false,
                    spoiler = false,
                    collectionId = null,
                    collectionName = null,
                    collectionPosterUrl = null
                )
            ),
            watchlist = listOf(
                DbWatchlist(
                    titleId = "tv_1399",
                    titleType = "SERIE",
                    titleName = "Game of Thrones",
                    titlePosterUrl = "/tv_poster.jpg",
                    dateAdded = 1700000000000L,
                    titleYear = "2011",
                    titleGenres = "Drame,Fantastique",
                    titleVoteAverage = 4.2f
                )
            ),
            customLists = listOf(
                DbCustomList(id = 1, name = "Favoris", description = "Mes préférés")
            ),
            customListTitles = listOf(
                DbCustomListTitle(id = 1, listId = 1, titleId = "movie_550", titleType = "FILM", titleName = "Fight Club", titlePosterUrl = "/poster.jpg", orderIndex = 0)
            ),
            seasonProgress = listOf(
                DbSeasonProgress(titleId = "tv_1399", seasonNumber = 1, status = "WATCHED")
            )
        )

        val adapter = moshi.adapter(CineLogBackup::class.java)
        val json = adapter.toJson(backup)

        assertTrue(json.contains("\"version\":1"))
        assertTrue(json.contains("Fight Club"))
        assertTrue(json.contains("Game of Thrones"))
        assertTrue(json.contains("Favoris"))

        val deserialized = adapter.fromJson(json)
        assertNotNull(deserialized)
        assertEquals(1, deserialized!!.logs.size)
        assertEquals("Fight Club", deserialized.logs[0].titleName)
        assertEquals(1, deserialized.watchlist.size)
        assertEquals("Game of Thrones", deserialized.watchlist[0].titleName)
        assertEquals(1, deserialized.customLists.size)
        assertEquals(1, deserialized.seasonProgress.size)
    }
}
