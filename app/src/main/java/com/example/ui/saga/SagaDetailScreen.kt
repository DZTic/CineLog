package com.example.ui.saga

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.CineTitle
import com.example.ui.CineViewModel
import com.example.ui.theme.CinemaSecondary
import com.example.ui.theme.CinemaSurfaceVariant
import com.example.ui.theme.GrayText
import com.example.ui.theme.StarGold

/**
 * Fiche détail dédiée à une saga (collection TMDB), distincte de la fiche
 * détail d'un film : pas de note/critique/journal ici (une saga ne se
 * "visionne" pas elle-même), mais un aperçu de la franchise et la liste de
 * ses films, chacun cliquable vers sa propre fiche détail.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SagaDetailScreen(
    collectionId: Int,
    viewModel: CineViewModel,
    onBackClick: () -> Unit,
    onTitleClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val sagaInfo by viewModel.sagaInfo.collectAsState()
    val sagaTitles by viewModel.sagaTitles.collectAsState()
    val loading by viewModel.sagaLoading.collectAsState()
    val error by viewModel.sagaError.collectAsState()
    val allLogs by viewModel.allLogs.collectAsState()
    val allWatchlist by viewModel.allWatchlist.collectAsState()

    LaunchedEffect(collectionId) {
        viewModel.loadSagaDetail(collectionId)
    }

    val watchedTitleIds = remember(allLogs) { allLogs.map { it.titleId }.toSet() }
    val watchlistTitleIds = remember(allWatchlist) { allWatchlist.map { it.titleId }.toSet() }
    val watchedCount = remember(sagaTitles, watchedTitleIds) {
        sagaTitles.count { it.id in watchedTitleIds }
    }
    val isSagaComplete = remember(sagaTitles, watchedCount) {
        sagaTitles.isNotEmpty() && watchedCount == sagaTitles.size
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(sagaInfo?.name ?: "Saga") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        if (loading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (error != null || sagaInfo == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = error ?: "Impossible de charger cette saga.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.loadSagaDetail(collectionId) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Réessayer", color = Color.Black)
                    }
                }
            }
        } else {
            val info = sagaInfo!!
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // Header : affiche officielle de la saga + nom + synopsis
                item {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            if (info.posterUrl != null) {
                                AsyncImage(
                                    model = info.posterUrl,
                                    contentDescription = info.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Collections,
                                        contentDescription = null,
                                        tint = GrayText,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }

                            // Petit badge indiquant que tous les films de la
                            // saga ont été vus, superposé en haut à droite de
                            // l'affiche (même esprit que le badge "SAGA" des
                            // cartes de la liste).
                            if (isSagaComplete) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(MaterialTheme.colorScheme.primary)
                                        .padding(4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Saga vue en entier",
                                        tint = Color.Black,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(CinemaSecondary.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "SAGA",
                                    color = CinemaSecondary,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    letterSpacing = 0.5.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = info.name,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "$watchedCount / ${sagaTitles.size} films vus",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = GrayText
                                )
                                if (isSagaComplete) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Saga vue en entier",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (!info.overview.isNullOrBlank()) {
                    item {
                        Text(
                            text = info.overview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Films de la saga",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        val remaining = sagaTitles.filter {
                            it.id !in watchlistTitleIds && it.id !in watchedTitleIds
                        }
                        if (remaining.isNotEmpty()) {
                            TextButton(onClick = { viewModel.addAllToWatchlist(remaining) }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.PlaylistAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Tout ajouter")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                items(sagaTitles, key = { it.id }) { movie ->
                    SagaMovieRow(
                        movie = movie,
                        isWatched = movie.id in watchedTitleIds,
                        isInWatchlist = movie.id in watchlistTitleIds,
                        onClick = { onTitleClick(movie.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SagaMovieRow(
    movie: CineTitle,
    isWatched: Boolean,
    isInWatchlist: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CinemaSurfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 50.dp, height = 75.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                if (movie.posterUrl != null) {
                    AsyncImage(
                        model = movie.posterUrl,
                        contentDescription = movie.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = movie.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = movie.year,
                        style = MaterialTheme.typography.bodySmall,
                        color = GrayText
                    )
                    if (movie.voteAverage > 0f) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = StarGold,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = String.format("%.1f", movie.voteAverage),
                            style = MaterialTheme.typography.bodySmall,
                            color = GrayText
                        )
                    }
                }
            }

            if (isWatched) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Déjà vu",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            } else if (isInWatchlist) {
                Icon(
                    Icons.Default.Bookmark,
                    contentDescription = "Dans la watchlist",
                    tint = GrayText,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
