package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageUtilsTest {

    @Test
    fun testFormatPosterUrl_withNullOrBlank_returnsNull() {
        assertNull(formatPosterUrl(null, PosterSize.CARD))
        assertNull(formatPosterUrl("", PosterSize.CARD))
        assertNull(formatPosterUrl("   ", PosterSize.CARD))
    }

    @Test
    fun testFormatPosterUrl_withTmdbFullUrl_replacesSize() {
        val input = "https://image.tmdb.org/t/p/w500/aeG07bS9Z6g0D8U5I14kY2q0bM5.jpg"
        
        val thumbnail = formatPosterUrl(input, PosterSize.THUMBNAIL)
        assertEquals("https://image.tmdb.org/t/p/w200/aeG07bS9Z6g0D8U5I14kY2q0bM5.jpg", thumbnail)

        val card = formatPosterUrl(input, PosterSize.CARD)
        assertEquals("https://image.tmdb.org/t/p/w300/aeG07bS9Z6g0D8U5I14kY2q0bM5.jpg", card)

        val detail = formatPosterUrl(input, PosterSize.DETAIL)
        assertEquals("https://image.tmdb.org/t/p/w500/aeG07bS9Z6g0D8U5I14kY2q0bM5.jpg", detail)
    }

    @Test
    fun testFormatPosterUrl_withRelativePath_prependsTmdbHostAndSize() {
        val input = "/aeG07bS9Z6g0D8U5I14kY2q0bM5.jpg"
        
        val result = formatPosterUrl(input, PosterSize.THUMBNAIL)
        assertEquals("https://image.tmdb.org/t/p/w200/aeG07bS9Z6g0D8U5I14kY2q0bM5.jpg", result)
    }

    @Test
    fun testFormatPosterUrl_withTmdbOriginalUrl_replacesWithConfiguredSize() {
        val input = "https://image.tmdb.org/t/p/original/aeG07bS9Z6g0D8U5I14kY2q0bM5.jpg"
        val card = formatPosterUrl(input, PosterSize.CARD)
        assertEquals("https://image.tmdb.org/t/p/w300/aeG07bS9Z6g0D8U5I14kY2q0bM5.jpg", card)
    }

    @Test
    fun testFormatPosterUrl_withRelativePathWithoutLeadingSlash_prependsTmdbHostAndSlash() {
        val input = "aeG07bS9Z6g0D8U5I14kY2q0bM5.jpg"
        val result = formatPosterUrl(input, PosterSize.CARD)
        assertEquals("https://image.tmdb.org/t/p/w300/aeG07bS9Z6g0D8U5I14kY2q0bM5.jpg", result)
    }

    @Test
    fun testFormatPosterUrl_withNonTmdbUrl_returnsUnchanged() {
        val input = "https://cdn.myanimelist.net/images/anime/10/47339.jpg"
        val result = formatPosterUrl(input, PosterSize.CARD)
        assertEquals(input, result)
    }
}
