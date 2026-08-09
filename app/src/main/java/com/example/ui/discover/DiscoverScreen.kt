
package com.example.ui.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.data.CineTitle
import com.example.data.TitleType
import com.example.ui.components.EmptyState
import com.example.ui.components.SkeletonDiscoverContent
import com.example.ui.components.SkeletonDiscoverGrid
import com.example.ui.components.TitleCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    viewModel: DiscoverViewModel,
    onTitleClick: (String) -> Unit,
    onSagaClick: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val query by viewModel.searchQuery.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val focusManager = LocalFocusManager.current

    val trendingFilms by viewModel.trendingFilms.collectAsState()
    val trendingSeries by viewModel.trendingSeries.collectAsState()
    val topAnime by viewModel.topAnime.collectAsState()
    val discoverLoading by viewModel.discoverLoading.collectAsState()
    val discoverError by viewModel.discoverError.collectAsState()
    val watchlist by viewModel.allWatchlist.collectAsState()
    val watchlistTitleIds = remember(watchlist) { watchlist.map { it.titleId }.toSet() }

    val allLogs by viewModel.allLogs.collectAsState()
    val watchedTitleIds = remember(allLogs) { allLogs.map { it.titleId }.toSet() }

    val filteredFilms = remember(trendingFilms, watchedTitleIds) {
        trendingFilms.filter { it.id !in watchedTitleIds }
    }
    val filteredSeries = remember(trendingSeries, watchedTitleIds) {
        trendingSeries.filter { it.id !in watchedTitleIds }
    }
    val filteredAnime = remember(topAnime, watchedTitleIds) {
        topAnime.filter { it.id !in watchedTitleIds }
    }

    val searchPagingItems = viewModel.searchPagingFlow.collectAsLazyPagingItems()
    val discoverPagingItems = viewModel.discoverPagingFlow.collectAsLazyPagingItems()

    val pullToRefreshState = rememberPullToRefreshState()

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "D?couvrir",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TextField(
                value = query,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("search_input_field"),
                placeholder = {
                    Text(
                        "Rechercher un film, une s?rie, un anime...",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Recherche"
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Effacer"
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focusManager.clearFocus()
                }),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == null,
                    onClick = { viewModel.setFilter(null) },
                    label = { Text("Tout") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
                FilterChip(
                    selected = selectedFilter == TitleType.FILM,
                    onClick = { viewModel.setFilter(TitleType.FILM) },
                    label = { Text("Films") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
                FilterChip(
                    selected = selectedFilter == TitleType.SERIE,
                    onClick = { viewModel.setFilter(TitleType.SERIE) },
                    label = { Text("S?ries") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
                FilterChip(
                    selected = selectedFilter == TitleType.ANIME,
                    onClick = { viewModel.setFilter(TitleType.ANIME) },
                    label = { Text("Animes") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }

            if (query.trim().length >= 2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    val refreshState = searchPagingItems.loadState.refresh
                    if (refreshState is LoadState.Loading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else if (refreshState is LoadState.Error) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                                .align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = (refreshState as LoadState.Error).error.localizedMessage ?: "Erreur de connexion.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else if (searchPagingItems.itemCount == 0) {
                        EmptyState(
                            message = "Aucun titre trouv? pour '$query'.",
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                count = searchPagingItems.itemCount,
                                key = { index -> searchPagingItems[index]?.id ?: index }
                            ) { index ->
                                val title = searchPagingItems[index]
                                if (title != null && title.id !in watchedTitleIds) {
                                    TitleCard(
                                        title = title,
                                        onClick = { onTitleClick(title.id) },
                                        isInWatchlist = title.id in watchlistTitleIds
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = discoverLoading,
                    onRefresh = { viewModel.loadDiscoverContent() },
                    state = pullToRefreshState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (discoverLoading) {
                        if (selectedFilter == null) {
                            SkeletonDiscoverContent()
                        } else {
                            SkeletonDiscoverGrid()
                        }
                    } else if (discoverError != null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Text(
                                    text = discoverError ?: "Erreur inconnue",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { viewModel.loadDiscoverContent() },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("R?essayer", color = Color.Black)
                                }
                            }
                        }
                    } else {
                       if (selectedFilter == null) {
                            if (filteredFilms.isEmpty() && filteredSeries.isEmpty() && filteredAnime.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    EmptyState(
                                        message = "Vous avez d?j? vu tous les titres tendance !"
                                    )
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(bottom = 16.dp)
                                ) {
                                    if (filteredFilms.isNotEmpty()) {
                                        CarouselSection(
                                            title = "Films populaires",
                                            items = filteredFilms,
                                            onTitleClick = onTitleClick,
                                            onViewAll = { viewModel.setFilter(TitleType.FILM) },
                                            watchlistTitleIds = watchlistTitleIds
                                        )

                                        Spacer(modifier = Modifier.height(16.dp))
                                    }

                                    if (filteredSeries.isNotEmpty()) {
                                        CarouselSection(
                                            title = "S?ries populaires",
                                            items = filteredSeries,
                                            onTitleClick = onTitleClick,
                                            onViewAll = { viewModel.setFilter(TitleType.SERIE) },
                                            watchlistTitleIds = watchlistTitleIds
                                        )

                                        Spacer(modifier = Modifier.height(16.dp))
                                    }

                                    if (filteredAnime.isNotEmpty()) {
                                        CarouselSection(
                                            title = "Animes les mieux not?s",
                                            items = filteredAnime,
                                            onTitleClick = onTitleClick,
                                            onViewAll = { viewModel.setFilter(TitleType.ANIME) },
                                            watchlistTitleIds = watchlistTitleIds
                                        )
                                    }
                                }
                            }
                       } else {
                            if (discoverPagingItems.itemCount == 0) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    EmptyState(
                                        message = "Vous avez d?j? vu tous les titres de cette cat?gorie !"
                                    )
                                }
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(3),
                                    contentPadding = PaddingValues(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(
                                        count = discoverPagingItems.itemCount,
                                        key = { index -> discoverPagingItems[index]?.id ?: index }
                                    ) { index ->
                                        val title = discoverPagingItems[index]
                                        if (title != null && title.id !in watchedTitleIds) {
                                            TitleCard(
                                                title = title,
                                                onClick = { onTitleClick(title.id) },
                                                isInWatchlist = title.id in watchlistTitleIds
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

@Composable
fun CarouselSection(
    title: String,
    items: List<CineTitle>,
    onTitleClick: (String) -> Unit,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier,
    watchlistTitleIds: Set<String> = emptySet()
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Row(
                modifier = Modifier.clickable { onViewAll() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tout voir",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(items, key = { "carousel_${it.id}" }) { title ->
                TitleCard(
                    title = title,
                    onClick = { onTitleClick(title.id) },
                    modifier = Modifier.width(110.dp),
                    isInWatchlist = title.id in watchlistTitleIds
                )
            }
        }
    }
}
