package com.example.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription

import coil.compose.AsyncImage
import com.example.data.CineTitle
import com.example.data.DbLogEntry
import com.example.data.TitleType
import com.example.ui.home.HomeViewModel
import com.example.ui.CollectionViewMode
import com.example.ui.components.CollapsibleCategoryHeader
import com.example.ui.components.EmptyState
import com.example.ui.components.GroupedDisplay
import com.example.ui.components.HalfStarRatingBar
import com.example.ui.components.LocalSearchBar
import com.example.ui.components.SagaCard
import com.example.ui.components.TitleCard
import com.example.ui.components.TypeBadge
import com.example.ui.components.ViewModeToggle
import com.example.ui.components.groupBySaga
import com.example.ui.theme.CinemaSecondary
import com.example.ui.theme.CinemaSurfaceVariant
import com.example.ui.theme.GrayText
import com.example.ui.theme.StarGold
import com.example.util.DateFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onTitleClick: (String) -> Unit,
    onSagaClick: (Int) -> Unit,
    onNavigateToDiscover: () -> Unit,
    onNavigateToSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val logsRaw by viewModel.allLogs.collectAsStateWithLifecycle()
    val watchlist by viewModel.allWatchlist.collectAsStateWithLifecycle()
    val collectionCache by viewModel.collectionCache.collectAsStateWithLifecycle()
    val sagaSizeCache by viewModel.sagaSizeCache.collectAsStateWithLifecycle()
    val viewMode by viewModel.homeViewMode.collectAsStateWithLifecycle()
    val collapsedCategories by viewModel.homeCollapsedCategories.collectAsStateWithLifecycle()
    val apiKey by viewModel.tmdbApiKey.collectAsStateWithLifecycle()
    val hasDismissedOnboarding by viewModel.hasDismissedOnboarding.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

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

    // Filtrage des visionnages par recherche textuelle (titre, saga, critique)
    val filteredLogs = remember(logs, searchQuery) {
        if (searchQuery.isBlank()) {
            logs
        } else {
            val q = searchQuery.trim().lowercase()
            logs.filter { entry ->
                entry.titleName.lowercase().contains(q) ||
                    entry.collectionName?.lowercase()?.contains(q) == true ||
                    entry.critique.lowercase().contains(q)
            }
        }
    }

    // Group by category (Films / Séries / Animes) for readability, most
    // recently watched first within each group.
    val groupedLogs = remember(filteredLogs) {
        filteredLogs
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
        val columnCount = if (viewMode == CollectionViewMode.GRID) 3 else 1
        val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

        LazyVerticalGrid(
            state = gridState,
            columns = remember(columnCount) { GridCells.Fixed(columnCount) },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            if (apiKey.isEmpty() && !hasDismissedOnboarding) {
                item(span = { GridItemSpan(columnCount) }) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .testTag("onboarding_banner"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Bienvenue sur CinéLog !",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Pour un accès complet et sans restriction à la recherche de films et séries, pensez à configurer votre clé API TMDB dans les Paramètres.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { viewModel.dismissOnboarding() }) {
                                    Text("Plus tard")
                                }
                                if (onNavigateToSettings != null) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = onNavigateToSettings,
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Text("Paramètres", color = MaterialTheme.colorScheme.onPrimary)
                                    }
                                }
                            }
                        }
                    }
                }
            }

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

            // Search bar within home tab
            if (logs.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LocalSearchBar(
                        query = searchQuery,
                        onQueryChange = { viewModel.setSearchQuery(it) },
                        placeholder = "Rechercher dans votre journal...",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        testTag = "home_search_bar"
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
                        text = if (searchQuery.isBlank()) "Activité Récente" else "Résultats (${filteredLogs.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    ViewModeToggle(
                        viewMode = viewMode,
                        onViewModeChange = { viewModel.setHomeViewMode(it) }
                    )
                }
            }

            if (logs.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val trendingFilms by viewModel.trendingFilms.collectAsStateWithLifecycle()
                    val trendingSeries by viewModel.trendingSeries.collectAsStateWithLifecycle()
                    val suggestions = remember(trendingFilms, trendingSeries) {
                        (trendingFilms + trendingSeries).distinctBy { it.id }.take(5)
                    }
                    val isFirstLaunch = watchlist.isEmpty() && !hasDismissedOnboarding
                    val emptyMessage = if (isFirstLaunch) {
                        "Bienvenue sur CineLog ! Votre journal est vide. Explorez nos suggestions ci-dessous ou recherchez vos œuvres préférées."
                    } else {
                        "Aucun visionnage récent dans votre journal. Ajoutez votre dernier film, série ou anime !"
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        EmptyState(
                            title = "Votre journal est vide",
                            message = emptyMessage,
                            icon = Icons.Default.Movie,
                            action = {
                                Button(
                                    onClick = onNavigateToDiscover,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.testTag("empty_state_cta_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Movie,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Qu'avez-vous regardé récemment ?",
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            },
                            extraContent = {
                                if (suggestions.isNotEmpty()) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.Start
                                    ) {
                                        Text(
                                            text = "Suggestions populaires",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onBackground,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                                        )
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp),
                                            modifier = Modifier.testTag("empty_state_suggestions_carousel")
                                        ) {
                                            items(suggestions, key = { "suggestion_${it.id}" }) { item ->
                                                SuggestionItemCard(
                                                    title = item,
                                                    onClick = { onTitleClick(item.id) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            } else if (filteredLogs.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        EmptyState(
                            title = "Aucun résultat",
                            message = "Aucun titre dans votre journal ne correspond à « $searchQuery ».",
                            icon = Icons.Default.Search,
                            action = {
                                Button(
                                    onClick = { viewModel.setSearchQuery("") },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("Effacer la recherche", color = MaterialTheme.colorScheme.onPrimary)
                                }
                            }
                        )
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
                            CollapsibleCategoryHeader(
                                label = stringResource(type.displayNameRes) + "s (${logsForType.size})",
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
                                        if (viewMode == CollectionViewMode.GRID) {
                                            val title = display.item.toCineTitle()
                                            TitleCard(
                                                title = title,
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
                                        LaunchedEffect(group.collectionId) {
                                            viewModel.ensureSagaSizeLoaded(group.collectionId)
                                        }
                                        val watchedInSaga = group.items.map { it.titleId }.distinct().size
                                        val isSagaComplete = sagaSizeCache[group.collectionId]
                                            ?.let { total -> total > 0 && watchedInSaga >= total } == true
                                        if (viewMode == CollectionViewMode.GRID) {
                                            SagaCard(
                                                name = group.collectionName,
                                                posterUrl = group.posterUrl,
                                                filmCount = group.items.size,
                                                onClick = { onSagaClick(group.collectionId) },
                                                modifier = Modifier.padding(vertical = 6.dp),
                                                isComplete = isSagaComplete
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
                color = MaterialTheme.colorScheme.onSurface
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
    val formattedDate = remember(log.dateVue) { DateFormatter.formatDayMonthYear(log.dateVue) }
    val titleType = remember(log.titleType) { TitleType.valueOf(log.titleType) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            // Pas de padding horizontal ici : le conteneur (LazyVerticalGrid
            // sur Home) applique déjà une marge horizontale via son
            // contentPadding, qu'on soit en mode Liste ou Grille.
            .padding(vertical = 6.dp)
            .testTag("log_entry_row_${log.id}")
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onTitleClick() },
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
                    val formattedUrl = androidx.compose.runtime.remember(log.titlePosterUrl) { com.example.util.formatPosterUrl(log.titlePosterUrl, com.example.util.PosterSize.THUMBNAIL) }
                    val posterFallback = com.example.util.ImagePlaceholders.movie()
                    AsyncImage(
                        model = formattedUrl,
                        contentDescription = log.titleName,
                        placeholder = posterFallback,
                        error = posterFallback,
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
                    color = MaterialTheme.colorScheme.onSurface,
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
                        color = if (log.spoiler) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
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
    val formattedDate = remember(latestDateVue) { DateFormatter.formatDayMonthYear(latestDateVue) }

    val filmCountText = if (count > 1) "$count films vus" else "$count film vu"
    val compositeDescription = remember(collectionName, count, averageNote, formattedDate) {
        buildString {
            append("Saga ").append(collectionName)
            append(", ").append(filmCountText)
            append(", dernier vu le ").append(formattedDate)
            append(", note moyenne ").append(String.format(Locale.FRENCH, "%.1f", averageNote))
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clearAndSetSemantics {
                contentDescription = compositeDescription
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() },
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
                    val formattedUrl = androidx.compose.runtime.remember(posterUrl) { com.example.util.formatPosterUrl(posterUrl, com.example.util.PosterSize.CARD) }
                    val posterFallback = com.example.util.ImagePlaceholders.collections()
                    AsyncImage(
                        model = formattedUrl,
                        contentDescription = collectionName,
                        placeholder = posterFallback,
                        error = posterFallback,
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
                    color = MaterialTheme.colorScheme.onSurface,
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

@Composable
fun SuggestionItemCard(
    title: CineTitle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(110.dp)
            .testTag("suggestion_card_${title.id}")
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (title.posterUrl != null) {
                    val formattedUrl = androidx.compose.runtime.remember(title.posterUrl) { com.example.util.formatPosterUrl(title.posterUrl, com.example.util.PosterSize.CARD) }
                    val posterFallback = com.example.util.ImagePlaceholders.movie()
                    AsyncImage(
                        model = formattedUrl,
                        placeholder = posterFallback,
                        error = posterFallback,
                        contentDescription = title.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
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
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                TypeBadge(
                    type = title.type,
                    compact = true,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title.title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (title.voteAverage > 0f) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = StarGold,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = String.format(Locale.FRENCH, "%.1f", title.voteAverage),
                        style = MaterialTheme.typography.labelSmall,
                        color = GrayText
                    )
                }
            }
        }
    }
}
