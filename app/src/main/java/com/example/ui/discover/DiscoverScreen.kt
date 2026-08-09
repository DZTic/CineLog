package com.example.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CineTitle
import com.example.data.TitleType
import com.example.ui.discover.DiscoverViewModel
import com.example.ui.components.EmptyState
import com.example.ui.components.GroupedDisplay
import com.example.ui.components.SagaCard
import com.example.ui.components.SkeletonDiscoverContent
import com.example.ui.components.SkeletonDiscoverGrid
import com.example.ui.components.TitleCard
import com.example.ui.components.groupBySaga
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    viewModel: DiscoverViewModel,
    onTitleClick: (String) -> Unit,
    onSagaClick: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf<TitleType?>(null) }
    val focusManager = LocalFocusManager.current

    val trendingFilms by viewModel.trendingFilms.collectAsState()
    val trendingSeries by viewModel.trendingSeries.collectAsState()
    val topAnime by viewModel.topAnime.collectAsState()
    val discoverLoading by viewModel.discoverLoading.collectAsState()
    val discoverError by viewModel.discoverError.collectAsState()
    val watchlist by viewModel.allWatchlist.collectAsState()
    val watchlistTitleIds by remember(watchlist) { derivedStateOf { watchlist.map { it.titleId }.toSet() } }

    val allLogs by viewModel.allLogs.collectAsState()
    val watchedTitleIds by remember(allLogs) { derivedStateOf { allLogs.map { it.titleId }.toSet() } }

    val filteredFilms by remember(trendingFilms, watchedTitleIds) {
        derivedStateOf { trendingFilms.filter { it.id !in watchedTitleIds } }
    }
    val filteredSeries by remember(trendingSeries, watchedTitleIds) {
        derivedStateOf { trendingSeries.filter { it.id !in watchedTitleIds } }
    }
    val filteredAnime by remember(topAnime, watchedTitleIds) {
        derivedStateOf { topAnime.filter { it.id !in watchedTitleIds } }
    }

    val searchResults by viewModel.searchResults.collectAsState()
    val searchLoading by viewModel.searchLoading.collectAsState()
    val searchError by viewModel.searchError.collectAsState()

    val filteredSearchResults by remember(searchResults, watchedTitleIds) {
        derivedStateOf { searchResults.filter { it.id !in watchedTitleIds } }
    }

    val displaySearchResults by remember(filteredSearchResults) {
        derivedStateOf {
            filteredSearchResults.groupBySaga(
                collectionId = { it.collectionId },
                collectionName = { it.collectionName },
                posterUrl = { it.collectionPosterUrl }
            ).sortedByDescending { display ->
                when (display) {
                    is GroupedDisplay.Single -> display.item.voteAverage
                    is GroupedDisplay.Grouped -> display.group.items.maxOf { it.voteAverage }
                }
            }
        }
    }

    var isDebouncing by remember { mutableStateOf(false) }
    LaunchedEffect(query, selectedFilter) {
        if (query.trim().length >= 2) {
            isDebouncing = true
            delay(350)
            isDebouncing = false
            viewModel.performSearch(query, selectedFilter)
        } else {
            isDebouncing = false
        }
    }

    val pullToRefreshState = rememberPullToRefreshState()

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Découvrir",
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
            // Persistent Search Bar
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("search_input_field"),
                placeholder = {
                    Text(
                        "Rechercher un film, une série, un anime...",
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
                        IconButton(onClick = { query = "" }) {
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
                    if (query.trim().length >= 2) {
                        viewModel.performSearch(query, selectedFilter)
                    }
                }),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                )
            )

            // Filter Selector Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == null,
                    onClick = { selectedFilter = null },
                    label = { Text("Tout") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
                FilterChip(
                    selected = selectedFilter == TitleType.FILM,
                    onClick = { selectedFilter = TitleType.FILM },
                    label = { Text("Films") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
                FilterChip(
                    selected = selectedFilter == TitleType.SERIE,
                    onClick = { selectedFilter = TitleType.SERIE },
                    label = { Text("Séries") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
                FilterChip(
                    selected = selectedFilter == TitleType.ANIME,
                    onClick = { selectedFilter = TitleType.ANIME },
                    label = { Text("Animes") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }

            if (query.trim().length >= 2) {
                // Search Results Grid
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (searchLoading || isDebouncing) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else if (searchError != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                                .align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = searchError ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else if (searchResults.isEmpty()) {
                        EmptyState(
                            message = "Aucun titre trouvé pour \"$query\".",
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
                                displaySearchResults,
                                key = { display ->
                                    when (display) {
                                        is GroupedDisplay.Single -> display.item.id
                                        is GroupedDisplay.Grouped -> "saga_${display.group.collectionId}"
                                    }
                                }
                            ) { display ->
                                when (display) {
                                    is GroupedDisplay.Single -> {
                                        TitleCard(
                                            title = display.item,
                                            onClick = { onTitleClick(display.item.id) },
                                            isInWatchlist = display.item.id in watchlistTitleIds
                                        )
                                    }
                                    is GroupedDisplay.Grouped -> {
                                        val group = display.group
                                        SagaCard(
                                            name = group.collectionName,
                                            posterUrl = group.posterUrl,
                                            filmCount = group.items.size,
                                            onClick = { onSagaClick(group.collectionId) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Standard Discover Mode with PullToRefresh
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
                                    Text("Réessayer", color = Color.Black)
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
                                        message = "Vous avez déjà vu tous les titres tendance !"
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
                                        onViewAll = { selectedFilter = TitleType.FILM },
                                        watchlistTitleIds = watchlistTitleIds
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))
                                }

                                if (filteredSeries.isNotEmpty()) {
                                    CarouselSection(
                                        title = "Séries populaires",
                                        items = filteredSeries,
                                        onTitleClick = onTitleClick,
                                        onViewAll = { selectedFilter = TitleType.SERIE },
                                        watchlistTitleIds = watchlistTitleIds
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))
                                }

                                if (filteredAnime.isNotEmpty()) {
                                    CarouselSection(
                                        title = "Animes les mieux notés",
                                        items = filteredAnime,
                                        onTitleClick = onTitleClick,
                                        onViewAll = { selectedFilter = TitleType.ANIME },
                                        watchlistTitleIds = watchlistTitleIds
                                    )
                                }
                            }
                            }
                       } else {
                            val gridItems by remember(selectedFilter, filteredFilms, filteredSeries, filteredAnime) {
                                derivedStateOf {
                                    when (selectedFilter) {
                                        TitleType.FILM -> filteredFilms
                                        TitleType.SERIE -> filteredSeries
                                        TitleType.ANIME -> filteredAnime
                                        else -> emptyList()
                                    }
                                }
                            }

                            if (gridItems.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    EmptyState(
                                        message = "Vous avez déjà vu tous les titres de cette catégorie !"
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
                                    items(gridItems, key = { "grid_" }) { title ->
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
            items(items, key = { "carousel_" }) { title ->
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
