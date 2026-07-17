package com.example.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DbLogEntry
import com.example.data.TitleType
import com.example.ui.CineViewModel
import com.example.ui.components.EmptyState
import com.example.ui.theme.CinemaSurfaceVariant
import com.example.ui.theme.CinemaTertiary
import com.example.ui.theme.GrayText
import com.example.ui.theme.StarGold
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: CineViewModel,
    modifier: Modifier = Modifier
) {
    val logs by viewModel.allLogs.collectAsState()
    val watchlist by viewModel.allWatchlist.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Profil & Statistiques",
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
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    message = "Aucun visionnage journalisé pour le moment.\nVos graphiques et statistiques apparaîtront dès que vous aurez enregistré votre premier log !"
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Key Stats Rows
                StatsOverviewPanel(logs = logs, watchlistCount = watchlist.size)

                // 1. Segmented Bar: Proportion of FILM / SERIE / ANIME
                TypeDistributionCard(logs = logs)

                // 2. Activity Chart: Month by Month
                MonthlyActivityCard(logs = logs)

                // 3. Score Distribution Histogram
                ScoreDistributionCard(logs = logs)
            }
        }
    }
}

@Composable
fun StatsOverviewPanel(
    logs: List<DbLogEntry>,
    watchlistCount: Int,
    modifier: Modifier = Modifier
) {
    val totalCount = logs.size
    val averageScore = if (logs.isEmpty()) 0f else logs.map { it.note }.average().toFloat()
    val rewatchedCount = logs.count { it.revisionnage }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CinemaSurfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$totalCount",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(text = "Titres vus", style = MaterialTheme.typography.bodySmall, color = GrayText)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = String.format("%.2f", averageScore),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = StarGold
                )
                Text(text = "Note moyenne", style = MaterialTheme.typography.bodySmall, color = GrayText)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$rewatchedCount",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = CinemaTertiary
                )
                Text(text = "Revisionnages", style = MaterialTheme.typography.bodySmall, color = GrayText)
            }
        }
    }
}

@Composable
fun TypeDistributionCard(
    logs: List<DbLogEntry>,
    modifier: Modifier = Modifier
) {
    val total = logs.size.toFloat()
    val films = logs.count { it.titleType == TitleType.FILM.name }.toFloat()
    val series = logs.count { it.titleType == TitleType.SERIE.name }.toFloat()
    val animes = logs.count { it.titleType == TitleType.ANIME.name }.toFloat()

    val filmPercent = if (total > 0) films / total else 0f
    val seriesPercent = if (total > 0) series / total else 0f
    val animePercent = if (total > 0) animes / total else 0f

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CinemaSurfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Répartition par Catégorie",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Segmented progress bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                if (films > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(if (filmPercent > 0) filmPercent else 0.0001f)
                            .background(CinemaTertiary)
                    )
                }
                if (series > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(if (seriesPercent > 0) seriesPercent else 0.0001f)
                            .background(Color(0xFFBB86FC))
                    )
                }
                if (animes > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(if (animePercent > 0) animePercent else 0.0001f)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Legends
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LegendItem(
                    color = CinemaTertiary,
                    label = "Films",
                    count = films.toInt(),
                    percent = (filmPercent * 100).toInt()
                )
                LegendItem(
                    color = Color(0xFFBB86FC),
                    label = "Séries",
                    count = series.toInt(),
                    percent = (seriesPercent * 100).toInt()
                )
                LegendItem(
                    color = MaterialTheme.colorScheme.primary,
                    label = "Animes",
                    count = animes.toInt(),
                    percent = (animePercent * 100).toInt()
                )
            }
        }
    }
}

@Composable
fun LegendItem(
    color: Color,
    label: String,
    count: Int,
    percent: Int
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(text = label, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
            Text(text = "$count ($percent%)", style = MaterialTheme.typography.bodySmall, color = GrayText, fontSize = 10.sp)
        }
    }
}

@Composable
fun MonthlyActivityCard(
    logs: List<DbLogEntry>,
    modifier: Modifier = Modifier
) {
    val monthCount = remember(logs) {
        val calendar = Calendar.getInstance()
        val format = SimpleDateFormat("MMM", Locale.FRENCH)
        val counts = mutableMapOf<String, Int>()

        // Pre-fill last 6 months in chronological order
        val tempCal = Calendar.getInstance()
        val monthsList = mutableListOf<String>()
        for (i in 5 downTo 0) {
            tempCal.time = Date()
            tempCal.add(Calendar.MONTH, -i)
            val monthLabel = format.format(tempCal.time).replaceFirstChar { it.uppercase() }
            monthsList.add(monthLabel)
            counts[monthLabel] = 0
        }

        // Aggregate actual entries
        logs.forEach { log ->
            calendar.timeInMillis = log.dateVue
            val label = format.format(calendar.time).replaceFirstChar { it.uppercase() }
            if (counts.containsKey(label)) {
                counts[label] = (counts[label] ?: 0) + 1
            }
        }

        monthsList.map { it to (counts[it] ?: 0) }
    }

    val maxCount = remember(monthCount) { monthCount.maxOfOrNull { it.second } ?: 1 }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CinemaSurfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Activité des 6 derniers mois",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Bars row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                monthCount.forEach { (month, count) ->
                    val barHeightFraction = if (maxCount > 0) count.toFloat() / maxCount.toFloat() else 0f
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (count > 0) "$count" else "",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 10.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxHeight(0.8f) // reserve space for text
                                .width(16.dp)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                        )
                                    )
                                )
                                .fillMaxHeight(barHeightFraction) // adjust height dynamically
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = month,
                            style = MaterialTheme.typography.bodySmall,
                            color = GrayText,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ScoreDistributionCard(
    logs: List<DbLogEntry>,
    modifier: Modifier = Modifier
) {
    // Collect count for each half-star from 0.5 to 5.0 (10 steps)
    val scoreMap = remember(logs) {
        val steps = listOf(0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 3.5f, 4.0f, 4.5f, 5.0f)
        val counts = steps.associateWith { 0 }.toMutableMap()
        logs.forEach { log ->
            val nearestStep = steps.minByOrNull { Math.abs(it - log.note) } ?: 3.0f
            counts[nearestStep] = (counts[nearestStep] ?: 0) + 1
        }
        counts.toSortedMap()
    }

    val maxScoreCount = remember(scoreMap) { scoreMap.values.maxOrNull() ?: 1 }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CinemaSurfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Distribution de vos notes",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Horizontal distribution bars
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                scoreMap.forEach { (score, count) ->
                    val fraction = if (maxScoreCount > 0) count.toFloat() / maxScoreCount.toFloat() else 0f
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = String.format("%.1f ★", score),
                            style = MaterialTheme.typography.bodySmall,
                            color = StarGold,
                            modifier = Modifier.width(45.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color.DarkGray)
                        ) {
                            if (count > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(fraction)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(StarGold)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$count",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            modifier = Modifier.width(20.dp)
                        )
                    }
                }
            }
        }
    }
}
