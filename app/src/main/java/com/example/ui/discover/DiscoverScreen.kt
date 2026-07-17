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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CineTitle
import com.example.data.TitleType
import com.example.ui.CineViewModel
import com.example.ui.components.TitleCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    viewModel: CineViewModel,
    onTitleClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val trendingFilms by viewModel.trendingFilms.collectAsState()
    val trendingSeries by viewModel.trendingSeries.collectAsState()
    val topAnime by viewModel.topAnime.collectAsState()
    val loading by viewModel.discoverLoading.collectAsState()
    val error by viewModel.discoverError.collectAsState()
    val apiKey by viewModel.tmdbApiKey.collectAsState()

    var selectedFilter by remember { mutableStateOf<TitleType?>(null) }

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
            // Filter Selector Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
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

            if (loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (error != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = error ?: "Erreur inconnue",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.loadDiscoverContent() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Réessayer", color = Color.Black)
                        }
                    }
                }
            } else {
                // If TMDB Key is empty, show a small helpful banner
                if (apiKey.isEmpty() && (selectedFilter == null || selectedFilter == TitleType.FILM || selectedFilter == TitleType.SERIE)) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "🎬 Clé API TMDB non configurée",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Allez dans l'onglet Paramètres pour coller votre clé API TMDB et débloquer le catalogue complet de films et séries. L'application affiche actuellement une liste de démonstration hors ligne.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.LightGray
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (selectedFilter == null) {
                    // "ALL" Layout with Carousel rows
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 16.dp)
                    ) {
                        CarouselSection(
                            title = "Films populaires",
                            items = trendingFilms,
                            onTitleClick = onTitleClick,
                            onViewAll = { selectedFilter = TitleType.FILM }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        CarouselSection(
                            title = "Séries populaires",
                            items = trendingSeries,
                            onTitleClick = onTitleClick,
                            onViewAll = { selectedFilter = TitleType.SERIE }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        CarouselSection(
                            title = "Animes les mieux notés",
                            items = topAnime,
                            onTitleClick = onTitleClick,
                            onViewAll = { selectedFilter = TitleType.ANIME }
                        )
                    }
                } else {
                    // Filtered Grid layout
                    val gridItems = when (selectedFilter) {
                        TitleType.FILM -> trendingFilms
                        TitleType.SERIE -> trendingSeries
                        TitleType.ANIME -> topAnime
                        else -> emptyList()
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(gridItems) { title ->
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

@Composable
fun CarouselSection(
    title: String,
    items: List<CineTitle>,
    onTitleClick: (String) -> Unit,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier
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
            items(items) { title ->
                TitleCard(
                    title = title,
                    onClick = { onTitleClick(title.id) },
                    modifier = Modifier.width(110.dp)
                )
            }
        }
    }
}
