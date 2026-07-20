package com.example.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.CineTitle
import com.example.data.DbLogEntry
import com.example.data.TitleType
import com.example.ui.CineViewModel
import com.example.ui.HomeViewMode
import com.example.ui.components.EmptyState
import com.example.ui.components.GroupedDisplay
import com.example.ui.components.HalfStarRatingBar
import com.example.ui.components.SagaCard
import com.example.ui.components.TitleCard
import com.example.ui.components.TypeBadge
import com.example.ui.components.groupBySaga
import com.example.ui.theme.CinemaSecondary
import com.example.ui.theme.CinemaSurfaceVariant
import com.example.ui.theme.GrayText
import com.example.ui.theme.StarGold
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: CineViewModel,
    onTitleClick: (String) -> Unit,
    onSagaClick: (Int) -> Unit,
    onNavigateToDiscover: () -> Unit,
    modifier: Modifier = Modifier
) {
    val logsRaw by viewModel.allLogs.collectAsState()
    val watchlist by viewModel.allWatchlist.collectAsState()
    val collectionCache by viewModel.collectionCache.collectAsState()
    val viewMode by viewModel.homeViewMode.collectAsState()
    val collapsedCategories by viewModel.homeCollapsedCategories.collectAsState()

    // Backfill collectionId for log entries recorded before the saga cache
    // existed, so they regroup as soon as their saga is known locally.
    val logs = remember(logsRaw, collectionCache) {
        logsRaw.map { entry ->
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

    // Calculated Statistics
    val totalWatched = logs.size
    val averageScore = remember(logs) {
        if (logs.isEmpty()) 0f else logs.map { it.note }.average().toFloat()
    }
    val watchlistCount = watchlist.size

    // Group by category (Films / Séries / Animes) for readability, most
    // recently watched first within each group. Computed here (not inside
    // LazyColumn's content lambda, which isn't a @Composable context) so
    // remember() is valid.
    val groupedLogs = remember(logs) {
        logs
            .sortedByDescending { it.dateVue }
            .groupBy { TitleType.valueOf(it.titleType) }
    }
    // Within each category, movies from the same TMDB saga are collapsed
    // into a single "Activité Récente" row instead of one row per film.
    val displayItemsByType = remember(groupedLogs) {
        groupedLogs.mapValues { (_, logsForType) ->
            logsForType.groupBySaga(
                collectionId = { it.collectionId },
                collectionName = { it.collectionName },
                posterUrl = { it.collectionPosterUrl }
            ).sortedByDescending { display ->
                when (display) {
                    is GroupedDisplay.Single -> display.item.dateVue
                    is GroupedDisplay.Grouped -> display.group.items.maxOf { it.dateVue }
                }
            }
        }
    }
    val categoryOrder = listOf(TitleType.FILM, TitleType.SERIE, TitleType.ANIME)

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "CinéLog",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        // Le nombre de colonnes pilote à la fois la mise en page ET le
        // style de carte utilisé plus bas (lignes pleine largeur en mode
        // Liste, affiches compactes en mode Grille) : garder les deux
        // synchronisés au même endroit évite qu'ils se désaccordent.
        val columnCount = if (viewMode == HomeViewMode.GRID) 3 else 1

        LazyVerticalGrid(
            columns = GridCells.Fixed(columnCount),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Stats Panel
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        count = "$totalWatched",
                        label = "Vus",
                        icon = Icons.Default.Movie,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        count = String.format("%.1f", averageScore),
                        label = "Note Moy.",
                        icon = Icons.Default.Star,
                        tint = StarGold,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        count = "$watchlistCount",
                        label = "À Voir",
                        icon = Icons.Default.Bookmark,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Recent activity header, with the list/grid display switch
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Activité Récente",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    HomeViewModeToggle(
                        viewMode = viewMode,
                        onViewModeChange = { viewModel.setHomeViewMode(it) }
                    )
                }
            }

            if (logs.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        EmptyState(
                            message = "Aucun visionnage journalisé pour l'instant.\nPrêt à ajouter votre premier film, série ou anime ?"
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onNavigateToDiscover,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Découvrir des titres", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                categoryOrder.forEach { type ->
                    val logsForType = groupedLogs[type]
                    val displayItems = displayItemsByType[type]
                    if (!logsForType.isNullOrEmpty() && !displayItems.isNullOrEmpty()) {
                        val isCollapsed = collapsedCategories.contains(type.name)
                        item(
                            key = "header_${type.name}",
                            span = { GridItemSpan(maxLineSpan) }
                        ) {
                            CategoryHeader(
                                label = "${type.displayName}s (${logsForType.size})",
                                collapsed = isCollapsed,
                                onToggle = { viewModel.toggleHomeCategoryCollapsed(type.name) }
                            )
                        }
                        // Catégorie réduite : on n'émet aucun item, ce qui
                        // libère immédiatement la place à l'écran pour les
                        // catégories suivantes, sans les recharger.
                        if (!isCollapsed) {
                            items(
                                displayItems,
                                key = { display ->
                                    when (display) {
                                        is GroupedDisplay.Single -> "log_${display.item.id}"
                                        is GroupedDisplay.Grouped -> "saga_${display.group.collectionId}"
                                    }
                                }
                            ) { display ->
                                when (display) {
                                    is GroupedDisplay.Single -> {
                                        if (viewMode == HomeViewMode.GRID) {
                                            TitleCard(
                                                title = display.item.toCineTitle(),
                                                onClick = { onTitleClick(display.item.titleId) },
                                                modifier = Modifier.padding(vertical = 6.dp)
                                            )
                                        } else {
                                            RecentActivityRow(
                                                log = display.item,
                                                onTitleClick = { onTitleClick(display.item.titleId) }
                                            )
                                        }
                                    }
                                    is GroupedDisplay.Grouped -> {
                                        val group = display.group
                                        if (viewMode == HomeViewMode.GRID) {
                                            SagaCard(
                                                name = group.collectionName,
                                                posterUrl = group.posterUrl,
                                                filmCount = group.items.size,
                                                onClick = { onSagaClick(group.collectionId) },
                                                modifier = Modifier.padding(vertical = 6.dp)
                                            )
                                        } else {
                                            val latest = group.items.maxByOrNull { it.dateVue }!!
                                            SagaActivityRow(
                                                collectionName = group.collectionName,
                                                posterUrl = group.posterUrl,
                                                count = group.items.size,
                                                averageNote = group.items.map { it.note }.average().toFloat(),
                                                latestDateVue = latest.dateVue,
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
 * Petit sélecteur Liste / Grille pour la page d'accueil : une carte pleine
 * largeur par titre (facile à lire, note et critique visibles) contre une
 * grille d'affiches à 3 colonnes (vue d'ensemble plus dense, comme sur
 * Watchlist/Découvrir).
 */
@Composable
private fun HomeViewModeToggle(
    viewMode: HomeViewMode,
    onViewModeChange: (HomeViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        HomeViewModeButton(
            icon = Icons.Default.ViewList,
            contentDescription = "Afficher en liste",
            selected = viewMode == HomeViewMode.LIST,
            onClick = { onViewModeChange(HomeViewMode.LIST) }
        )
        HomeViewModeButton(
            icon = Icons.Default.GridView,
            contentDescription = "Afficher en grille",
            selected = viewMode == HomeViewMode.GRID,
            onClick = { onViewModeChange(HomeViewMode.GRID) }
        )
    }
}

@Composable
private fun HomeViewModeButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (selected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * En-tête de catégorie cliquable : un chevron pivote pour indiquer si la
 * section est développée ou réduite. Réduire une catégorie permet de faire
 * de la place à l'écran pour mieux voir les autres, sans rien supprimer :
 * l'état est retenu et les items réapparaissent en un tap.
 */
@Composable
private fun CategoryHeader(
    label: String,
    collapsed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rotation by animateFloatAsState(targetValue = if (collapsed) -90f else 0f, label = "chevron_rotation")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable { onToggle() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = GrayText
        )
        Icon(
            imageVector = Icons.Default.ExpandMore,
            contentDescription = if (collapsed) "Développer la catégorie" else "Réduire la catégorie",
            tint = GrayText,
            modifier = Modifier
                .size(20.dp)
                .rotate(rotation)
        )
    }
}

private fun DbLogEntry.toCineTitle(): CineTitle {
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

@Composable
fun StatCard(
    count: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CinemaSurfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = count,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = GrayText
            )
        }
    }
}

@Composable
fun RecentActivityRow(
    log: DbLogEntry,
    onTitleClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.FRENCH) }
    val formattedDate = remember(log.dateVue) { formatter.format(Date(log.dateVue)) }
    val titleType = remember(log.titleType) { TitleType.valueOf(log.titleType) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            // Pas de padding horizontal ici : le conteneur (LazyVerticalGrid
            // sur Home) applique déjà une marge horizontale via son
            // contentPadding, qu'on soit en mode Liste ou Grille.
            .padding(vertical = 6.dp)
            .testTag("log_entry_row_${log.id}")
            .clickable { onTitleClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CinemaSurfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Mini Poster
            Box(
                modifier = Modifier
                    .size(width = 50.dp, height = 75.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                if (log.titlePosterUrl != null) {
                    AsyncImage(
                        model = log.titlePosterUrl,
                        contentDescription = log.titleName,
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

            // Information details
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TypeBadge(type = titleType, compact = true)
                    Text(
                        text = "Vu le $formattedDate",
                        style = MaterialTheme.typography.bodySmall,
                        color = GrayText
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = log.titleName,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    HalfStarRatingBar(rating = log.note, starSize = 14.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${log.note} ★",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = StarGold
                    )
                }

                if (log.critique.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (log.spoiler) "⚠️ [Critique contient des spoilers]" else log.critique,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (log.spoiler) MaterialTheme.colorScheme.error else Color.LightGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Row for a whole saga (TMDB collection): shown instead of one
 * RecentActivityRow per movie once two or more films from the same
 * franchise have been logged.
 */
@Composable
fun SagaActivityRow(
    collectionName: String,
    posterUrl: String?,
    count: Int,
    averageNote: Float,
    latestDateVue: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.FRENCH) }
    val formattedDate = remember(latestDateVue) { formatter.format(Date(latestDateVue)) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            // Idem RecentActivityRow : la marge horizontale vient du
            // contentPadding du conteneur, pas d'ici.
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
            // Affiche de la saga (pas celle d'un film en particulier)
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(CinemaSecondary.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "SAGA",
                            color = CinemaSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                    Text(
                        text = "Dernier vu le $formattedDate",
                        style = MaterialTheme.typography.bodySmall,
                        color = GrayText
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = collectionName,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    HalfStarRatingBar(rating = averageNote, starSize = 14.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${String.format("%.1f", averageNote)} ★ moy.",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = StarGold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "$count films vus de la saga",
                    style = MaterialTheme.typography.bodySmall,
                    color = GrayText
                )
            }
        }
    }
}
