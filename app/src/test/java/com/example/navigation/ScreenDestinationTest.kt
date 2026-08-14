package com.example.navigation

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenDestinationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testScreenDestinationSerialization() {
        val detail = ScreenDestination.Detail(titleId = "movie_12345")
        val serializedDetail = json.encodeToString(detail)
        val deserializedDetail = json.decodeFromString<ScreenDestination.Detail>(serializedDetail)
        assertEquals("movie_12345", deserializedDetail.titleId)

        val saga = ScreenDestination.SagaDetail(collectionId = 999)
        val serializedSaga = json.encodeToString(saga)
        val deserializedSaga = json.decodeFromString<ScreenDestination.SagaDetail>(serializedSaga)
        assertEquals(999, deserializedSaga.collectionId)
    }

    @Test
    fun testSingletonDestinations() {
        val home = ScreenDestination.Home
        val serializedHome = json.encodeToString(home)
        val deserializedHome = json.decodeFromString<ScreenDestination.Home>(serializedHome)
        assertEquals(ScreenDestination.Home, deserializedHome)
    }
}
