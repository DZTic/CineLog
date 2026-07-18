package com.example.ui.detail

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.CineTitle
import com.example.data.DbLogEntry
import com.example.data.TitleType
import com.example.ui.CineViewModel
import com.example.ui.components.HalfStarRatingBar
import com.example.ui.components.TypeBadge
import com.example.ui.components.WatchedBadge
import com.example.ui.theme.CinemaSurfaceVariant
import com.example.ui.theme.GrayText
import com.example.ui.theme.StarGold
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    titleId: String,
    viewModel: CineViewModel,
    onBackClick: () -> Unit,
    onLogClick: (CineTitle) -> Unit,
    onTitleClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentTitle by viewModel.currentTitle.collectAsState()
    val logs by viewModel.currentTitleLogs.collectAsState()
    val loading by viewModel.detailLoading.collectAsState()
    val error by viewModel.detailError.collectAsState()
    val customLists by viewModel.allCustomLists.collectAsState()

    val watchlist by viewModel.allWatchlist.collectAsState()
    val collectionTitles by viewModel.collectionTitles.collectAsState()
    val isInWatchlist = remember(watchlist, titleId) {
        watchlist.any { it.titleId == titleId }
    }

    var showListDialog by remember { mutableStateOf(false) }

    LaunchedEffect(titleId) {
        viewModel.loadTitleDetail(titleId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fiche Détail") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (error != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = error ?: "Une erreur est survenue lors du chargement.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.loadTitleDetail(titleId) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Réessayer", color = Color.Black)
                    }
                }
            }
        } else {
            val title = currentTitle
            if (title != null) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    // Header Poster + Basic info
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            // Poster image
                            Box(
                                modifier = Modifier
                                    .width(120.dp)
                                    .aspectRatio(2f / 3f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                if (title.posterUrl != null) {
                                    AsyncImage(
                                        model = title.posterUrl,
                                        contentDescription = title.title,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Movie,
                                            contentDescription = null,
                                            tint = GrayText,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            // Metadata
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TypeBadge(type = title.type)
                                    if (logs.isNotEmpty()) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        WatchedBadge()
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = title.title,
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Année : ${title.year}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = GrayText
                                )
                                if (!title.studioOrDirector.isNullOrBlank()) {
                                    Text(
                                        text = if (title.type == TitleType.ANIME) "Studio : ${title.studioOrDirector}" else "Réalisation/Casting : ${title.studioOrDirector}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = GrayText,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (title.voteAverage > 0) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = "Note moyenne",
                                            tint = StarGold,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${String.format("%.1f", title.voteAverage)} / 5",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        Text(
                                            text = " (Communauté)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = GrayText
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Genre Chips
                    if (title.genres.isNotEmpty()) {
                        item {
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                title.genres.forEach { genre ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(CinemaSurfaceVariant)
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = genre,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                            color = OnSurfaceColor(genre)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    // Primary Action Buttons
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Button(
                                onClick = { onLogClick(title) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("action_log_title"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Journaliser ce visionnage", color = Color.Black, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.toggleWatchlist(
                                            titleId = title.id,
                                            type = title.type,
                                            name = title.title,
                                            posterUrl = title.posterUrl
                                        )
                                        val msg = if (isInWatchlist) "Retiré de la Watchlist" else "Ajouté à la Watchlist"
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("action_watchlist"),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = if (isInWatchlist) MaterialTheme.colorScheme.primary else Color.White
                                    )
                                ) {
                                    Icon(
                                        imageVector = if (isInWatchlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                        contentDescription = null
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isInWatchlist) "Dans la Watchlist" else "Watchlist")
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                OutlinedButton(
                                    onClick = { showListDialog = true },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Ajouter à une liste")
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Synopsis Section
                    if (title.synopsis.isNotBlank()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            ) {
                                Text(
                                    text = "Synopsis",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = title.synopsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.LightGray,
                                    lineHeight = 20.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }

                    // Saga Section — only for movies belonging to a TMDB collection
                    if (title.collectionId != null && collectionTitles.isNotEmpty()) {
                        item {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Saga",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        if (!title.collectionName.isNullOrBlank()) {
                                            Text(
                                                text = title.collectionName,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = GrayText,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    TextButton(
                                        onClick = {
                                            viewModel.addAllToWatchlist(collectionTitles)
                                            Toast.makeText(
                                                context,
                                                "Saga ajoutée à la Watchlist",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Tout ajouter", fontSize = 13.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(collectionTitles, key = { it.id }) { sagaTitle ->
                                        Column(
                                            modifier = Modifier
                                                .width(100.dp)
                                                .clickable { onTitleClick(sagaTitle.id) },
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(2f / 3f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(CinemaSurfaceVariant)
                                            ) {
                                                if (sagaTitle.posterUrl != null) {
                                                    AsyncImage(
                                                        model = sagaTitle.posterUrl,
                                                        contentDescription = sagaTitle.title,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = sagaTitle.title,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onBackground,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                textAlign = TextAlign.Center
                                            )
                                            Text(
                                                text = sagaTitle.year,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = GrayText
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }

                    // Seasons / Episodes block
                    if (title.seasons.isNotEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            ) {
                                Text(
                                    text = "Saisons & Épisodes",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                title.seasons.forEach { season ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        colors = CardDefaults.cardColors(containerColor = CinemaSurfaceVariant)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = season.name,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = Color.White
                                            )
                                            Text(
                                                text = "${season.episodeCount} épisodes",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = GrayText
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }

                    // User Review Logs History
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Text(
                                text = "Vos visionnages (${logs.size})",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    if (logs.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Vous n'avez pas encore journalisé ce titre. Cliquez sur le bouton ci-dessus pour ajouter votre premier visionnage !",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = GrayText,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    } else {
                        items(logs) { log ->
                            LogItemRow(
                                log = log,
                                onDelete = { viewModel.deleteLog(log.id) }
                            )
                        }
                    }
                }
            }
        }

        // List Selection Dialog
        if (showListDialog) {
            AlertDialog(
                onDismissRequest = { showListDialog = false },
                title = { Text("Ajouter à une liste") },
                text = {
                    if (customLists.isEmpty()) {
                        Text(
                            "Vous n'avez créé aucune liste personnalisée pour l'instant. Allez dans l'onglet 'Mes listes' pour créer votre première liste.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GrayText
                        )
                    } else {
                        Column {
                            customLists.forEach { list ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable {
                                            val title = currentTitle
                                            if (title != null) {
                                                viewModel.addTitleToCustomList(list.id, title)
                                                Toast
                                                    .makeText(
                                                        context,
                                                        "Ajouté à la liste '${list.name}' !",
                                                        Toast.LENGTH_SHORT
                                                    )
                                                    .show()
                                                showListDialog = false
                                            }
                                        },
                                    colors = CardDefaults.cardColors(containerColor = CinemaSurfaceVariant)
                                ) {
                                    Text(
                                        text = list.name,
                                        modifier = Modifier.padding(16.dp),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showListDialog = false }) {
                        Text("Fermer")
                    }
                }
            )
        }
    }
}

@Composable
fun LogItemRow(
    log: DbLogEntry,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formatter = remember { SimpleDateFormat("dd MMMM yyyy", Locale.FRENCH) }
    val formattedDate = remember(log.dateVue) { formatter.format(Date(log.dateVue)) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = CinemaSurfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Vu le $formattedDate",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (log.revisionnage) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = GrayText,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "Revisionnage",
                                style = MaterialTheme.typography.bodySmall,
                                color = GrayText,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                // Delete Button
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Supprimer le log",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                HalfStarRatingBar(rating = log.note, starSize = 16.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${log.note} ★",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = StarGold
                )
            }

            if (log.critique.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                if (log.spoiler) {
                    var revealSpoiler by remember { mutableStateOf(false) }
                    if (revealSpoiler) {
                        Text(
                            text = log.critique,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.LightGray
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                                .clickable { revealSpoiler = true }
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "⚠️ Critique contenant des spoilers. Cliquer pour afficher.",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                } else {
                    Text(
                        text = log.critique,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray
                    )
                }
            }
        }
    }
}

private fun OnSurfaceColor(genre: String): Color {
    return when (genre.lowercase()) {
        "action" -> Color(0xFFFF8A80)
        "science-fiction", "sci-fi" -> Color(0xFF80D8FF)
        "drame" -> Color(0xFFFFD180)
        "animation", "anime" -> Color(0xFFCCFF90)
        "fantastique" -> Color(0xFFEA80FC)
        else -> Color.White
    }
}
