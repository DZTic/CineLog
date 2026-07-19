package com.example.ui.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.data.TitleType
import com.example.ui.CineViewModel
import com.example.ui.components.EmptyState
import com.example.ui.components.GroupedDisplay
import com.example.ui.components.SagaCard
import com.example.ui.components.TitleCard
import com.example.ui.components.groupBySaga

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: CineViewModel,
    onTitleClick: (String) -> Unit,
    onSagaClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf<TitleType?>(null) }
    val focusManager = LocalFocusManager.current

    val searchResults by viewModel.searchResults.collectAsState()
    val loading by viewModel.searchLoading.collectAsState()
    val error by viewModel.searchError.collectAsState()
    val apiKey by viewModel.tmdbApiKey.collectAsState()

    // Movies from the same saga (TMDB collection) are collapsed into a
    // single result, so a franchise shows up once instead of cluttering the
    // grid with every film in it. Order is kept close to the original
    // relevance ranking (best vote average first).
    val displayResults = remember(searchResults) {
        searchResults.groupBySaga(
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

    // Perform search whenever query or filter changes
    LaunchedEffect(query, selectedFilter) {
        if (query.trim().length >= 2) {
            viewModel.performSearch(query, selectedFilter)
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
                    viewModel.performSearch(query, selectedFilter)
                }),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            // Search Filters
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
                        selectedLabelColor = Color.Black
                    )
                )
                FilterChip(
                    selected = selectedFilter == TitleType.FILM,
                    onClick = { selectedFilter = TitleType.FILM },
                    label = { Text("Films") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.Black
                    )
                )
                FilterChip(
                    selected = selectedFilter == TitleType.SERIE,
                    onClick = { selectedFilter = TitleType.SERIE },
                    label = { Text("Séries") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.Black
                    )
                )
                FilterChip(
                    selected = selectedFilter == TitleType.ANIME,
                    onClick = { selectedFilter = TitleType.ANIME },
                    label = { Text("Animes") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.Black
                    )
                )
            }

            // Results UI Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (loading) {
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
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else if (query.trim().length < 2) {
                    EmptyState(
                        message = "Entrez au moins 2 caractères pour lancer la recherche globale.",
                        modifier = Modifier.align(Alignment.Center)
                    )
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
    }
}
