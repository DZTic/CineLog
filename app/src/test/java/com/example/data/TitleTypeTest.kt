package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// #148: a titleType outside the enum must never throw.
class TitleTypeTest {

    @Test
    fun fromDb_acceptsKnownValuesCaseInsensitively() {
        assertEquals(TitleType.FILM, TitleType.fromDb("FILM"))
        assertEquals(TitleType.SERIE, TitleType.fromDb("serie"))
        assertEquals(TitleType.ANIME, TitleType.fromDb("  Anime "))
    }

    @Test
    fun fromDb_fallsBackToFilmOnUnknownValues() {
        assertEquals(TitleType.FILM, TitleType.fromDb("2019"))
        assertEquals(TitleType.FILM, TitleType.fromDb("Year"))
        assertEquals(TitleType.FILM, TitleType.fromDb(""))
        assertEquals(TitleType.FILM, TitleType.fromDb(null))
    }

    @Test
    fun parseOrNull_rejectsUnknownValues() {
        assertEquals(TitleType.SERIE, TitleType.parseOrNull("Serie"))
        assertNull(TitleType.parseOrNull("2019"))
        assertNull(TitleType.parseOrNull(null))
    }
}
