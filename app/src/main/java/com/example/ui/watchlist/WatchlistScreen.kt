package com.example.ui.watchlist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.CineTitle
import com.example.data.DbWatchlist
import com.example.data.TitleType
import com.example.ui.CineViewModel
import com.example.ui.components.EmptyState
import com.example.ui.components.GroupedDisplay
import com.example.ui.components.TitleCard
import com.example.ui.components.groupBySaga
import com.example.ui.theme.GrayText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    viewModel: CineViewModel,
    onTitleClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val watchlistRaw by viewModel.allWatchlist.collectAsState()
    val collectionCache by viewModel.collectionCache.collectAsState()

    // Entries added before the saga cache existed (or via the "Tout
    // ajouter" bug) may have no collectionId stored yet. Backfill it from
    // the local cache at read time so they regroup as soon as their saga is
    // known, without needing to be re-added.
    val watchlist = remember(watchlistRaw, collectionCache) {
        watchlistRaw.map { entry ->
            if (entry.collectionId == null) {
                collectionCache[entry.titleId]?.let { (id, name) ->
                    entry.copy(collectionId = id, collectionName = name)
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
                        posterUrl = { it.titlePosterUrl }
                    ).sortedByDescending { display ->
                        when (display) {
                            is GroupedDisplay.Single -> display.item.dateAdded
                            is GroupedDisplay.Grouped -> display.group.items.maxOf { it.dateAdded }
                        }
                    }
                }
            }
            val categoryOrder = listOf(TitleType.FILM, TitleType.SERIE, TitleType.ANIME)

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                categoryOrder.forEach { type ->
                    val itemsForType = groupedWatchlist[type]
                    val displayItems = displayItemsByType[type]
                    if (!itemsForType.isNullOrEmpty() && !displayItems.isNullOrEmpty()) {
                        item(
                            key = "header_${type.name}",
                            span = { GridItemSpan(maxLineSpan) }
                        ) {
                            Text(
                                text = "${type.displayName}s (${itemsForType.size})",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = GrayText,
                                modifier = Modifier.padding(
                                    top = if (type == categoryOrder.first()) 0.dp else 8.dp,
                                    bottom = 4.dp
                                )
                            )
                        }
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
                                    val title = display.item.toCineTitle()
                                    TitleCard(
                                        title = title,
                                        onClick = { onTitleClick(title.id) }
                                    )
                                }
                                is GroupedDisplay.Grouped -> {
                                    val group = display.group
                                    // Navigate to the most recently added movie of
                                    // the saga; its detail page already surfaces the
                                    // full saga list to browse the rest.
                                    val target = group.items.maxByOrNull { it.dateAdded }!!
                                    val sagaTitle = CineTitle(
                                        id = target.titleId,
                                        type = TitleType.FILM,
                                        title = group.collectionName,
                                        year = "",
                                        posterUrl = group.posterUrl,
                                        synopsis = "",
                                        genres = emptyList(),
                                        voteAverage = 0f
                                    )
                                    TitleCard(
                                        title = sagaTitle,
                                        onClick = { onTitleClick(target.titleId) },
                                        sagaCount = group.items.size
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
