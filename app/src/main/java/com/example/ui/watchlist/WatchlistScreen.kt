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
import com.example.ui.components.TitleCard
import com.example.ui.theme.GrayText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    viewModel: CineViewModel,
    onTitleClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val watchlist by viewModel.allWatchlist.collectAsState()

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
            // same approach as the "Activité Récente" grouping on Home.
            val groupedWatchlist = remember(watchlist) {
                watchlist.groupBy {
                    try {
                        TitleType.valueOf(it.titleType)
                    } catch (e: Exception) {
                        TitleType.FILM
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
                    if (!itemsForType.isNullOrEmpty()) {
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
                        items(itemsForType, key = { it.titleId }) { item ->
                            val title = item.toCineTitle()
                            TitleCard(
                                title = title,
                                onClick = { onTitleClick(title.id) }
                            )
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
