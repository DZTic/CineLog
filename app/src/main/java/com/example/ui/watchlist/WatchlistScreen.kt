package com.example.ui.watchlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.CineTitle
import com.example.data.DbWatchlist
import com.example.data.TitleType
import com.example.ui.CineViewModel
import com.example.ui.CollectionViewMode
import com.example.ui.components.CollapsibleCategoryHeader
import com.example.ui.components.EmptyState
import com.example.ui.components.GroupedDisplay
import com.example.ui.components.SagaCard
import com.example.ui.components.TitleCard
import com.example.ui.components.TypeBadge
import com.example.ui.components.ViewModeToggle
import com.example.ui.components.groupBySaga
import com.example.ui.theme.CinemaSurfaceVariant
import com.example.ui.theme.GrayText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    viewModel: CineViewModel,
    onTitleClick: (String) -> Unit,
    onSagaClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val watchlistRaw by viewModel.allWatchlist.collectAsState()
    val collectionCache by viewModel.collectionCache.collectAsState()
    val viewMode by viewModel.watchlistViewMode.collectAsState()
    val collapsedCategories by viewModel.watchlistCollapsedCategories.collectAsState()

    // Entries added before the saga cache existed (or via the "Tout
    // ajouter" bug) may have no collectionId stored yet. Backfill it from
    // the local cache at read time so they regroup as soon as their saga is
    // known, without needing to be re-added.
    val watchlist = remember(watchlistRaw, collectionCache) {
        watchlistRaw.map { entry ->
            if (entry.collectionId == null) {
                collectionCache[entry.titleId]?.let { cached ->
                    entry.copy(
                        collectionId = cached.collectionId,
                        collectionName = cached.collectionName,
                        collectionPosterUrl = cached.posterUrl
                    )
                } ?: entry
            } else {
                entry
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Watchlist",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        if (watchlist.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    message = "Votre Watchlist est vide.\nAjoutez des titres depuis leur fiche détail pour les retrouver ici !"
                )
            }
        } else {
            // Group by category (Films / Séries / Animes) for readability,
            // same approach as the "Activité Récente" grouping on Home. Within
            // each category, movies that belong to the same TMDB saga are
            // further collapsed into a single entry.
            val groupedWatchlist = remember(watchlist) {
                watchlist.groupBy {
                    try {
                        TitleType.valueOf(it.titleType)
                    } catch (e: Exception) {
                        TitleType.FILM
                    }
                }
            }
            val displayItemsByType = remember(groupedWatchlist) {
                groupedWatchlist.mapValues { (_, items) ->
                    items.groupBySaga(
                        collectionId = { it.collectionId },
                        collectionName = { it.collectionName },
                        posterUrl = { it.collectionPosterUrl }
                    ).sortedByDescending { display ->
                        when (display) {
                            is GroupedDisplay.Single -> display.item.dateAdded
                            is GroupedDisplay.Grouped -> display.group.items.maxOf { it.dateAdded }
                        }
                    }
                }
            }
            val categoryOrder = listOf(TitleType.FILM, TitleType.SERIE, TitleType.ANIME)

            // Même logique que sur l'Accueil : le nombre de colonnes pilote
            // à la fois la mise en page et le style de carte (ligne pleine
            // largeur en Liste, affiche compacte en Grille).
            val columnCount = if (viewMode == CollectionViewMode.GRID) 3 else 1

            LazyVerticalGrid(
                columns = GridCells.Fixed(columnCount),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(if (viewMode == CollectionViewMode.GRID) 16.dp else 0.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Sélecteur Liste / Grille, en haut de la page
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${watchlist.size} titre${if (watchlist.size > 1) "s" else ""}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GrayText
                        )
                        ViewModeToggle(
                            viewMode = viewMode,
                            onViewModeChange = { viewModel.setWatchlistViewMode(it) }
                        )
                    }
                }

                categoryOrder.forEach { type ->
                    val itemsForType = groupedWatchlist[type]
                    val displayItems = displayItemsByType[type]
                    if (!itemsForType.isNullOrEmpty() && !displayItems.isNullOrEmpty()) {
                        val isCollapsed = collapsedCategories.contains(type.name)
                        item(
                            key = "header_${type.name}",
                            span = { GridItemSpan(maxLineSpan) }
                        ) {
                            CollapsibleCategoryHeader(
                                label = "${type.displayName}s (${itemsForType.size})",
                                collapsed = isCollapsed,
                                onToggle = { viewModel.toggleWatchlistCategoryCollapsed(type.name) }
                            )
                        }
                        // Catégorie réduite : aucun item émis, ce qui laisse
                        // immédiatement de la place aux catégories suivantes.
                        if (!isCollapsed) {
                            items(
                                displayItems,
                                key = { display ->
                                    when (display) {
                                        is GroupedDisplay.Single -> display.item.titleId
                                        is GroupedDisplay.Grouped -> "saga_${display.group.collectionId}"
                                    }
                                }
                            ) { display ->
                                when (display) {
                                    is GroupedDisplay.Single -> {
                                        if (viewMode == CollectionViewMode.GRID) {
                                            val title = display.item.toCineTitle()
                                            TitleCard(
                                                title = title,
                                                onClick = { onTitleClick(title.id) }
                                            )
                                        } else {
                                            WatchlistRow(
                                                entry = display.item,
                                                onClick = { onTitleClick(display.item.titleId) }
                                            )
                                        }
                                    }
                                    is GroupedDisplay.Grouped -> {
                                        val group = display.group
                                        if (viewMode == CollectionViewMode.GRID) {
                                            SagaCard(
                                                name = group.collectionName,
                                                posterUrl = group.posterUrl,
                                                filmCount = group.items.size,
                                                onClick = { onSagaClick(group.collectionId) }
                                            )
                                        } else {
                                            SagaWatchlistRow(
                                                collectionName = group.collectionName,
                                                posterUrl = group.posterUrl,
                                                count = group.items.size,
                                                onClick = { onSagaClick(group.collectionId) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Ligne pleine largeur pour un titre de la Watchlist, utilisée en mode
 * Liste. Contrairement à "Activité Récente" (Accueil), il n'y a ni note ni
 * critique ici : le titre n'a pas encore été vu, seulement ajouté.
 */
@Composable
private fun WatchlistRow(
    entry: DbWatchlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.FRENCH) }
    val formattedDate = remember(entry.dateAdded) { formatter.format(Date(entry.dateAdded)) }
    val titleType = remember(entry.titleType) {
        try {
            TitleType.valueOf(entry.titleType)
        } catch (e: Exception) {
            TitleType.FILM
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            // Pas de padding horizontal ici : la marge vient du
            // contentPadding du conteneur (LazyVerticalGrid).
            .padding(vertical = 6.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CinemaSurfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 50.dp, height = 75.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                if (entry.titlePosterUrl != null) {
                    AsyncImage(
                        model = entry.titlePosterUrl,
                        contentDescription = entry.titleName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = GrayText.copy(alpha = 0.5f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TypeBadge(type = titleType, compact = true)
                    Text(
                        text = "Ajouté le $formattedDate",
                        style = MaterialTheme.typography.bodySmall,
                        color = GrayText
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = entry.titleName,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Équivalent de WatchlistRow pour une saga entière (plusieurs films de la
 * même franchise ajoutés à la Watchlist), en mode Liste.
 */
@Composable
private fun SagaWatchlistRow(
    collectionName: String,
    posterUrl: String?,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CinemaSurfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 50.dp, height = 75.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                if (posterUrl != null) {
                    AsyncImage(
                        model = posterUrl,
                        contentDescription = collectionName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Collections,
                            contentDescription = null,
                            tint = GrayText.copy(alpha = 0.5f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "SAGA",
                        color = MaterialTheme.colorScheme.tertiary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = collectionName,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "$count films de la saga à voir",
                    style = MaterialTheme.typography.bodySmall,
                    color = GrayText
                )
            }
        }
    }
}

private fun DbWatchlist.toCineTitle(): CineTitle {
    val tType = try {
        TitleType.valueOf(titleType)
    } catch (e: Exception) {
        TitleType.FILM
    }
    return CineTitle(
        id = titleId,
        type = tType,
        title = titleName,
        year = "",
        posterUrl = titlePosterUrl,
        synopsis = "",
        genres = emptyList(),
        voteAverage = 0f
    )
}
