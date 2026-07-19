package com.example.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.DbLogEntry
import com.example.data.TitleType
import com.example.ui.CineViewModel
import com.example.ui.components.EmptyState
import com.example.ui.components.GroupedDisplay
import com.example.ui.components.HalfStarRatingBar
import com.example.ui.components.TypeBadge
import com.example.ui.components.groupBySaga
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
    onNavigateToDiscover: () -> Unit,
    modifier: Modifier = Modifier
) {
    val logsRaw by viewModel.allLogs.collectAsState()
    val watchlist by viewModel.allWatchlist.collectAsState()
    val collectionCache by viewModel.collectionCache.collectAsState()

    // Backfill collectionId for log entries recorded before the saga cache
    // existed, so they regroup as soon as their saga is known locally.
    val logs = remember(logsRaw, collectionCache) {
        logsRaw.map { entry ->
            if (entry.collectionId == null) {
                collectionCache[entry.titleId]?.let { (id, name) ->
                    entry.copy(collectionId = id, collectionName = name)
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
                posterUrl = { it.titlePosterUrl }
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Stats Panel
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
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

            // Recent activity header
            item {
                Text(
                    text = "Activité Récente",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (logs.isEmpty()) {
                item {
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
                        item(key = "header_${type.name}") {
                            Text(
                                text = "${type.displayName}s (${logsForType.size})",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                color = GrayText
                            )
                        }
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
                                    RecentActivityRow(
                                        log = display.item,
                                        onTitleClick = { onTitleClick(display.item.titleId) }
                                    )
                                }
                                is GroupedDisplay.Grouped -> {
                                    val group = display.group
                                    val latest = group.items.maxByOrNull { it.dateVue }!!
                                    SagaActivityRow(
                                        collectionName = group.collectionName,
                                        posterUrl = group.posterUrl,
                                        titleType = TitleType.valueOf(latest.titleType),
                                        count = group.items.size,
                                        averageNote = group.items.map { it.note }.average().toFloat(),
                                        latestDateVue = latest.dateVue,
                                        onTitleClick = { onTitleClick(latest.titleId) }
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
            .padding(horizontal = 16.dp, vertical = 6.dp)
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
    titleType: TitleType,
    count: Int,
    averageNote: Float,
    latestDateVue: Long,
    onTitleClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.FRENCH) }
    val formattedDate = remember(latestDateVue) { formatter.format(Date(latestDateVue)) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
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
