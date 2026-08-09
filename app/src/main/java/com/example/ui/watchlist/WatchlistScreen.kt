package com.example.ui.watchlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import com.example.ui.components.SkeletonWatchlistGrid
import com.example.ui.components.SkeletonWatchlistList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.PopupProperties
import com.example.ui.WatchlistSortOrder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription

import coil.compose.AsyncImage
import com.example.data.CineTitle
import com.example.data.DbWatchlist
import com.example.data.TitleType
import com.example.ui.watchlist.WatchlistViewModel
import com.example.ui.CollectionViewMode
import com.example.ui.components.CollapsibleCategoryHeader
import com.example.ui.components.EmptyState
import com.example.ui.components.GroupedDisplay
import com.example.ui.components.SwipeToDismissContainer
import com.example.ui.components.SagaCard
import com.example.ui.components.TitleCard
import com.example.ui.components.TypeBadge
import com.example.ui.components.ViewModeToggle
import com.example.ui.components.groupBySaga
import com.example.ui.theme.CinemaSurfaceVariant
import com.example.ui.theme.GrayText
import com.example.util.DateFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    viewModel: WatchlistViewModel,
    onTitleClick: (String) -> Unit,
    onSagaClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val watchlistRaw by viewModel.allWatchlist.collectAsState()
    val collectionCache by viewModel.collectionCache.collectAsState()
    val allLogs by viewModel.allLogs.collectAsState()
    val viewMode by viewModel.watchlistViewMode.collectAsState()
    val collapsedCategories by viewModel.watchlistCollapsedCategories.collectAsState()
    val sortOrder by viewModel.watchlistSort.collectAsState()
    val typeFilter by viewModel.watchlistTypeFilter.collectAsState()
    val genreFilter by viewModel.watchlistGenreFilter.collectAsState()
    val yearFilter by viewModel.watchlistYearFilter.collectAsState()
    val isRefreshing by viewModel.watchlistRefreshing.collectAsState()
    val pullToRefreshState = rememberPullToRefreshState()

    // Entries added before the saga cache existed (or via the "Tout
    // ajouter" bug) may have no collectionId stored yet. Backfill it from
    // the local cache at read time so they regroup as soon as their saga is
    // known, without needing to be re-added.
    val backfilledWatchlist = remember(watchlistRaw, collectionCache) {
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

    // Un titre peut rester présent dans la table watchlist tout en étant
    // déjà marqué comme vu (log_entries) — par exemple ré-ajouté manuellement
    // depuis sa fiche détail après visionnage. On le masque ici pour que la
    // Watchlist ne montre jamais de films déjà vus et que, par effet de bord,
    // une saga entièrement vue disparaisse d'elle-même du regroupement par
    // saga puisqu'il ne lui reste alors plus aucune entrée non vue.
    val watchedTitleIds = remember(allLogs) { allLogs.map { it.titleId }.toSet() }
    val watchedFiltered = remember(backfilledWatchlist, watchedTitleIds) {
        backfilledWatchlist.filter { it.titleId !in watchedTitleIds }
    }

    // Filtres de la Watchlist (issue #33). Appliques avant le regroupement
    // par type/saga pour que le nombre affiche dans chaque en-tete reflete
    // bien ce qui reste apres filtrage.
    val watchlist = remember(watchedFiltered, typeFilter, genreFilter, yearFilter) {
        watchedFiltered.filter { entry ->
            val matchesType = typeFilter == null ||
                runCatching { TitleType.valueOf(entry.titleType) }.getOrNull() == typeFilter
            val entryGenres = entry.titleGenres?.split(",")?.map { it.trim() } ?: emptyList()
            val matchesGenre = genreFilter == null || entryGenres.contains(genreFilter)
            val matchesYear = yearFilter == null || entry.titleYear == yearFilter
            matchesType && matchesGenre && matchesYear
        }
    }

    // Genres et annees disponibles : calcules sur la watchlist filtree par
    // type uniquement, pour que les menus ne montrent que des options qui
    // ont encore au moins un resultat.
    val typeScopedEntries = remember(watchedFiltered, typeFilter) {
        watchedFiltered.filter { entry ->
            typeFilter == null ||
                runCatching { TitleType.valueOf(entry.titleType) }.getOrNull() == typeFilter
        }
    }
    val availableGenres = remember(typeScopedEntries) {
        typeScopedEntries
            .flatMap { it.titleGenres?.split(",")?.map { g -> g.trim() } ?: emptyList() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
    val availableYears = remember(typeScopedEntries) {
        typeScopedEntries
            .mapNotNull { it.titleYear }
            .distinct()
            .sortedDescending()
    }

    // Backfill en arriere-plan : les entrees sans metadonnees (creees avant
    // la v6) sont enrichies depuis l'API au premier affichage de l'ecran.
    val watchedTitleIdsKey = remember(watchedFiltered) { watchedFiltered.map { it.titleId } }
    androidx.compose.runtime.LaunchedEffect(watchedTitleIdsKey) {
        viewModel.backfillWatchlistMetadata(watchedFiltered)
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
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refreshWatchlist() },
            state = pullToRefreshState,
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
        if (watchlist.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize(),
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
            val displayItemsByType = remember(groupedWatchlist, sortOrder) {
                groupedWatchlist.mapValues { (_, items) ->
                    val grouped = items.groupBySaga(
                        collectionId = { it.collectionId },
                        collectionName = { it.collectionName },
                        posterUrl = { it.collectionPosterUrl }
                    )
                    // Une saga est un element composite : pour le tri, on
                    // utilise ses items (ex. annee/note min ou max du groupe),
                    // avec les entrees sans metadonnee triees en dernier.
                    when (sortOrder) {
                        WatchlistSortOrder.DATE_ADDED ->
                            grouped.sortedByDescending { display ->
                                when (display) {
                                    is GroupedDisplay.Single -> display.item.dateAdded
                                    is GroupedDisplay.Grouped -> display.group.items.maxOf { it.dateAdded }
                                }
                            }
                        WatchlistSortOrder.TITLE_AZ ->
                            grouped.sortedBy { display ->
                                when (display) {
                                    is GroupedDisplay.Single -> display.item.titleName.lowercase()
                                    is GroupedDisplay.Grouped -> display.group.collectionName.lowercase()
                                }
                            }
                        WatchlistSortOrder.RELEASE_YEAR ->
                            grouped.sortedByDescending { display ->
                                when (display) {
                                    is GroupedDisplay.Single -> display.item.titleYear?.toIntOrNull() ?: Int.MIN_VALUE
                                    is GroupedDisplay.Grouped ->
                                        display.group.items.mapNotNull { it.titleYear?.toIntOrNull() }.maxOrNull() ?: Int.MIN_VALUE
                                }
                            }
                        WatchlistSortOrder.COMMUNITY_RATING ->
                            grouped.sortedByDescending { display ->
                                when (display) {
                                    is GroupedDisplay.Single -> display.item.titleVoteAverage ?: Float.MIN_VALUE
                                    is GroupedDisplay.Grouped ->
                                        display.group.items.mapNotNull { it.titleVoteAverage }.maxOrNull() ?: Float.MIN_VALUE
                                }
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
            ) {
                // Barre du haut : compteur + tri + filtres + vue Liste/Grille.
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(modifier = Modifier.fillMaxWidth()) {
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SortMenuButton(
                                    current = sortOrder,
                                    onSelect = { viewModel.setWatchlistSort(it) }
                                )
                                FilterMenuButton(
                                    typeFilter = typeFilter,
                                    genreFilter = genreFilter,
                                    yearFilter = yearFilter,
                                    availableGenres = availableGenres,
                                    availableYears = availableYears,
                                    onTypeChange = { viewModel.setWatchlistTypeFilter(it) },
                                    onGenreChange = { viewModel.setWatchlistGenreFilter(it) },
                                    onYearChange = { viewModel.setWatchlistYearFilter(it) },
                                    onClearAll = { viewModel.clearWatchlistFilters() }
                                )
                                ViewModeToggle(
                                    viewMode = viewMode,
                                    onViewModeChange = { viewModel.setWatchlistViewMode(it) }
                                )
                            }
                        }

                        // Chips des filtres actifs : l'utilisateur voit en un
                        // coup d'oeil ce qui est applique et peut retirer
                        // chaque filtre d'un tap, sans re-ouvrir le menu.
                        val activeChips = buildList {
                            typeFilter?.let { add("Type: ${it.displayName}" to { viewModel.setWatchlistTypeFilter(null) }) }
                            genreFilter?.let { add("Genre: $it" to { viewModel.setWatchlistGenreFilter(null) }) }
                            yearFilter?.let { add("Ann\u00e9e: $it" to { viewModel.setWatchlistYearFilter(null) }) }
                        }
                        if (activeChips.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                activeChips.forEach { (label, onRemove) ->
                                    InputChip(
                                        selected = true,
                                        onClick = onRemove,
                                        label = { Text(label) },
                                        trailingIcon = {
                                            Text("\u00d7", color = GrayText)
                                        }
                                    )
                                }
                            }
                        }
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
                                        is GroupedDisplay.Single -> "watchlist_single_${display.item.titleId}"
                                        is GroupedDisplay.Grouped -> "watchlist_saga_${display.group.collectionId}"
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
                                            SwipeToDismissContainer(
                                                onDelete = { viewModel.removeFromWatchlist(display.item.titleId) },
                                                cornerRadius = 8.dp
                                            ) {
                                                WatchlistRow(
                                                    entry = display.item,
                                                    onClick = { onTitleClick(display.item.titleId) }
                                                )
                                            }
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
        } // end PullToRefreshBox
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
    val formattedDate = remember(entry.dateAdded) { DateFormatter.formatDayMonthYear(entry.dateAdded) }
    val titleType = remember(entry.titleType) {
        try {
            TitleType.valueOf(entry.titleType)
        } catch (e: Exception) {
            TitleType.FILM
        }
    }

    val typeLabel = remember(titleType) {
        when (titleType) {
            TitleType.FILM -> "Film"
            TitleType.SERIE -> "S?rie"
            TitleType.ANIME -> "Anime"
        }
    }
    val compositeDescription = remember(entry, formattedDate, typeLabel) {
        buildString {
            append(entry.titleName)
            append(", ").append(typeLabel)
            append(", ajout? le ").append(formattedDate)
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
            Box(
                modifier = Modifier
                    .size(width = 50.dp, height = 75.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                if (entry.titlePosterUrl != null) {
                    val formattedUrl = androidx.compose.runtime.remember(entry.titlePosterUrl) { com.example.util.formatPosterUrl(entry.titlePosterUrl, com.example.util.PosterSize.THUMBNAIL) }
                    val posterFallback = com.example.util.ImagePlaceholders.movie()
                    AsyncImage(
                        model = formattedUrl,
                        contentDescription = entry.titleName,
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
                    color = MaterialTheme.colorScheme.onSurface,
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
    val filmCountText = if (count > 1) "$count films ? voir" else "$count film ? voir"
    val compositeDescription = remember(collectionName, count) {
        "Saga $collectionName, $filmCountText"
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
                    color = MaterialTheme.colorScheme.onSurface,
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


/**
 * Bouton ouvrant le menu de tri de la Watchlist (issue #33). L'option
 * active est marquee d'une coche ; le tri est persiste dans les preferences.
 */
@Composable
private fun SortMenuButton(
    current: WatchlistSortOrder,
    onSelect: (WatchlistSortOrder) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.SwapVert,
                contentDescription = "Trier la watchlist",
                tint = GrayText,
                modifier = Modifier.size(20.dp)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            properties = PopupProperties(focusable = true)
        ) {
            WatchlistSortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = order.displayName + if (order == current) "  \u2713" else "",
                            color = if (order == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        onSelect(order)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Bouton ouvrant le menu des filtres (type / genre / annee). Les filtres
 * nullables representent "aucun filtre" ; le bouton est colore quand au
 * moins un filtre est actif pour le rappeler visuellement.
 */
@Composable
private fun FilterMenuButton(
    typeFilter: TitleType?,
    genreFilter: String?,
    yearFilter: String?,
    availableGenres: List<String>,
    availableYears: List<String>,
    onTypeChange: (TitleType?) -> Unit,
    onGenreChange: (String?) -> Unit,
    onYearChange: (String?) -> Unit,
    onClearAll: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val hasActive = typeFilter != null || genreFilter != null || yearFilter != null

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.FilterList,
                contentDescription = "Filtrer la watchlist",
                tint = if (hasActive) MaterialTheme.colorScheme.primary else GrayText,
                modifier = Modifier.size(20.dp)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            properties = PopupProperties(focusable = true)
        ) {
            // --- Type ---
            Text(
                text = "Type",
                style = MaterialTheme.typography.labelSmall,
                color = GrayText,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
            DropdownMenuItem(
                text = { Text("Tous" + if (typeFilter == null) "  \u2713" else "") },
                onClick = { onTypeChange(null) }
            )
            listOf(TitleType.FILM, TitleType.SERIE, TitleType.ANIME).forEach { type ->
                DropdownMenuItem(
                    text = {
                        Text("${type.displayName}s" + if (typeFilter == type) "  \u2713" else "")
                    },
                    onClick = { onTypeChange(type) }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // --- Genre ---
            Text(
                text = "Genre",
                style = MaterialTheme.typography.labelSmall,
                color = GrayText,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
            DropdownMenuItem(
                text = { Text("Tous" + if (genreFilter == null) "  \u2713" else "") },
                onClick = { onGenreChange(null) }
            )
            availableGenres.forEach { genre ->
                DropdownMenuItem(
                    text = { Text(genre + if (genreFilter == genre) "  \u2713" else "") },
                    onClick = { onGenreChange(genre) }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // --- Annee ---
            Text(
                text = "Ann\u00e9e",
                style = MaterialTheme.typography.labelSmall,
                color = GrayText,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
            DropdownMenuItem(
                text = { Text("Toutes" + if (yearFilter == null) "  \u2713" else "") },
                onClick = { onYearChange(null) }
            )
            availableYears.forEach { year ->
                DropdownMenuItem(
                    text = { Text(year + if (yearFilter == year) "  \u2713" else "") },
                    onClick = { onYearChange(year) }
                )
            }

            if (hasActive) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                DropdownMenuItem(
                    text = {
                        Text("R\u00e9initialiser les filtres", color = MaterialTheme.colorScheme.primary)
                    },
                    onClick = {
                        onClearAll()
                        expanded = false
                    }
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
        year = titleYear ?: "",
        posterUrl = titlePosterUrl,
        synopsis = "",
        genres = titleGenres?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList(),
        voteAverage = titleVoteAverage ?: 0f
    )
}

