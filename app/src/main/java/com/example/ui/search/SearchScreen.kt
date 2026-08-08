package com.example.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CineTitle
import com.example.data.TitleType
import com.example.ui.search.SearchViewModel
import com.example.ui.log.LogViewModel
import com.example.ui.components.EmptyState
import com.example.ui.components.GroupedDisplay
import com.example.ui.components.SagaCard
import com.example.ui.components.TitleCard
import com.example.ui.components.groupBySaga
import com.example.ui.log.LogBottomSheet
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    logViewModel: LogViewModel? = null,
    onTitleClick: (String) -> Unit,
    onSagaClick: (Int) -> Unit,
    onNavigateToSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf<TitleType?>(null) }
    var selectedGenre by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedYear by rememberSaveable { mutableStateOf<String?>(null) }
    var manualLogTitle by remember { mutableStateOf<CineTitle?>(null) }
    val focusManager = LocalFocusManager.current

    val searchResults by viewModel.searchResults.collectAsState()
    val loading by viewModel.searchLoading.collectAsState()
    val error by viewModel.searchError.collectAsState()
    val apiKey by viewModel.tmdbApiKey.collectAsState()

    val searchHistory by viewModel.searchHistory.collectAsState()
    val pinnedSearches by viewModel.pinnedSearches.collectAsState()

    val trendingFilms by viewModel.trendingFilms.collectAsState()
    val trendingSeries by viewModel.trendingSeries.collectAsState()
    val topAnime by viewModel.topAnime.collectAsState()

    val popularSuggestions = remember(trendingFilms, trendingSeries, topAnime) {
        (trendingFilms.take(3) + trendingSeries.take(3) + topAnime.take(3)).distinctBy { it.id }
    }

    // Dynamic Genre and Year options derived from search results
    val availableGenres = remember(searchResults) {
        searchResults.flatMap { it.genres }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    val availableYears = remember(searchResults) {
        searchResults.mapNotNull { title ->
            title.year.takeIf { it.isNotBlank() && it != "N/A" }
        }
        .distinct()
        .sortedDescending()
    }

    // Filter search results by selected genre and year
    val filteredResults = remember(searchResults, selectedGenre, selectedYear) {
        searchResults.filter { title ->
            val matchesGenre = selectedGenre == null || title.genres.any { it.equals(selectedGenre, ignoreCase = true) }
            val matchesYear = selectedYear == null || title.year.equals(selectedYear, ignoreCase = true)
            matchesGenre && matchesYear
        }
    }

    val displayResults = remember(filteredResults) {
        filteredResults.groupBySaga(
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

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Recherche",
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
            // Search Input Field
            TextField(
                value = query,
                onValueChange = { 
                    query = it 
                    selectedGenre = null
                    selectedYear = null
                },
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val isPinned = query.trim().isNotBlank() && pinnedSearches.any { it.equals(query.trim(), ignoreCase = true) }
                        if (query.trim().length >= 2) {
                            IconButton(onClick = { viewModel.togglePinSearch(query.trim()) }) {
                                Icon(
                                    imageVector = if (isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                                    contentDescription = if (isPinned) "Dépingler cette recherche" else "Épingler cette recherche",
                                    tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { 
                                query = ""
                                selectedGenre = null
                                selectedYear = null
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Effacer"
                                )
                            }
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

            // Search Filters (Type)
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

            // Dynamic Sub-filters (Genre & Year) when search results exist
            if (searchResults.isNotEmpty() && (availableGenres.isNotEmpty() || availableYears.isNotEmpty())) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedGenre != null || selectedYear != null) {
                        item {
                            SuggestionChip(
                                onClick = {
                                    selectedGenre = null
                                    selectedYear = null
                                },
                                label = { Text("Réinitialiser") },
                                icon = { Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                colors = SuggestionChipDefaults.suggestionChipColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                            )
                        }
                    }

                    // Available Genre Filter Chips
                    items(availableGenres) { genre ->
                        FilterChip(
                            selected = selectedGenre.equals(genre, ignoreCase = true),
                            onClick = {
                                selectedGenre = if (selectedGenre.equals(genre, ignoreCase = true)) null else genre
                            },
                            label = { Text(genre) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        )
                    }

                    // Available Year Filter Chips
                    items(availableYears) { year ->
                        FilterChip(
                            selected = selectedYear == year,
                            onClick = {
                                selectedYear = if (selectedYear == year) null else year
                            },
                            label = { Text(year) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        )
                    }
                }
            }

            if (apiKey.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("tmdb_api_key_banner"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Clé API TMDB non renseignée",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Pour un accès complet et direct à la recherche TMDB, vous pouvez configurer votre propre clé dans les Paramètres.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (onNavigateToSettings != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(onClick = onNavigateToSettings) {
                                Text("Paramètres", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Results / Suggestions Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (loading || isDebouncing) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else if (error != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                            .align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = error ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        if (onNavigateToSettings != null && apiKey.isEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onNavigateToSettings,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Paramètres API", color = Color.Black)
                            }
                        }
                    }
                } else if (query.trim().length < 2) {
                    // Search Suggestions (Pinned & Recent History)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        // Pinned Searches Section
                        if (pinnedSearches.isNotEmpty()) {
                            Text(
                                text = "Recherches enregistrées",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            pinnedSearches.forEach { pinnedItem ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            query = pinnedItem
                                            viewModel.performSearch(pinnedItem, selectedFilter)
                                        },
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PushPin,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = pinnedItem,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(onClick = { viewModel.togglePinSearch(pinnedItem) }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Dépingler",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Search History Section
                        if (searchHistory.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Historique de recherche",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                TextButton(onClick = { viewModel.clearSearchHistory() }) {
                                    Text("Effacer tout", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            searchHistory.forEach { historyItem ->
                                val isPinned = pinnedSearches.any { it.equals(historyItem, ignoreCase = true) }
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            query = historyItem
                                            viewModel.performSearch(historyItem, selectedFilter)
                                        },
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = historyItem,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(onClick = { viewModel.togglePinSearch(historyItem) }) {
                                            Icon(
                                                imageVector = if (isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                                                contentDescription = if (isPinned) "Dépingler" else "Épingler",
                                                tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        IconButton(onClick = { viewModel.removeSearchHistory(historyItem) }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Supprimer de l'historique",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (pinnedSearches.isEmpty() && searchHistory.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                                EmptyState(
                                    message = "Entrez au moins 2 caractères pour lancer la recherche globale."
                                )
                            }
                        }
                    }
                } else if (searchResults.isEmpty() || filteredResults.isEmpty()) {
                    // Enriched Empty State with Manual Add Button & Popular Suggestions
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (searchResults.isEmpty()) "Aucun titre trouvé pour '$query'." else "Aucun résultat correspondant aux filtres sélectionnés.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // Button to add title manually
                        Button(
                            onClick = {
                                manualLogTitle = CineTitle(
                                    id = "custom_${System.currentTimeMillis()}",
                                    type = selectedFilter ?: TitleType.FILM,
                                    title = query.trim(),
                                    year = "",
                                    posterUrl = null,
                                    synopsis = "",
                                    genres = emptyList(),
                                    voteAverage = 0f
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.padding(bottom = 24.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ajouter manuellement", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                        }

                        // Recommendation Section
                        if (popularSuggestions.isNotEmpty()) {
                            Text(
                                text = "Suggestions de titres populaires",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.align(Alignment.Start).padding(bottom = 12.dp)
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(popularSuggestions) { suggestion ->
                                    TitleCard(
                                        title = suggestion,
                                        onClick = { onTitleClick(suggestion.id) },
                                        modifier = Modifier.width(110.dp)
                                    )
                                }
                            }
                        }
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
                            displayResults,
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
                                        onClick = { onTitleClick(display.item.id) }
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
        }

        // Dialog to manually add/log a title when empty state button is clicked
        val customTitle = manualLogTitle
        if (customTitle != null && logViewModel != null) {
            LogBottomSheet(
                title = customTitle,
                viewModel = logViewModel,
                onDismiss = { manualLogTitle = null }
            )
        }
    }
}
