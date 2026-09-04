package com.example.util

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Movie
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import coil.imageLoader
import coil.request.ImageRequest

enum class PosterSize(val sizePath: String) {
    THUMBNAIL("w200"),  // Mini posters / thumbnails ~50dp
    CARD("w300"),       // Cards ~110dp
    DETAIL("w500")      // Detail screen poster
}

private val TMDB_POSTER_REGEX = Regex("/t/p/w[0-9]+/")
private const val TMDB_ORIGINAL_PATH = "/t/p/original/"

fun formatPosterUrl(urlOrPath: String?, size: PosterSize = PosterSize.CARD): String? {
    if (urlOrPath.isNullOrBlank()) return null
    return if (urlOrPath.startsWith("http://") || urlOrPath.startsWith("https://")) {
        if (urlOrPath.contains("image.tmdb.org/t/p/")) {
            urlOrPath.replace(TMDB_POSTER_REGEX, "/t/p/${size.sizePath}/")
                .replace(TMDB_ORIGINAL_PATH, "/t/p/${size.sizePath}/")
        } else {
            urlOrPath
        }
    } else {
        val cleanPath = if (urlOrPath.startsWith("/")) urlOrPath else "/$urlOrPath"
        "https://image.tmdb.org/t/p/${size.sizePath}$cleanPath"
    }
}

object ImagePlaceholders {
    @Composable
    fun movie(): Painter = rememberVectorPainter(Icons.Default.Movie)

    @Composable
    fun collections(): Painter = rememberVectorPainter(Icons.Default.Collections)
}

fun preloadImages(context: Context, urls: List<String?>, size: PosterSize = PosterSize.CARD) {
    val loader = context.imageLoader
    urls.filterNotNull().take(10).forEach { rawUrl ->
        val formatted = formatPosterUrl(rawUrl, size)
        if (!formatted.isNullOrBlank()) {
            val request = ImageRequest.Builder(context)
                .data(formatted)
                .build()
            loader.enqueue(request)
        }
    }
}
