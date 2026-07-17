package com.example.ui.lists

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.CineTitle
import com.example.data.DbCustomListTitle
import com.example.data.TitleType
import com.example.ui.CineViewModel
import com.example.ui.components.EmptyState
import com.example.ui.components.TypeBadge
import com.example.ui.theme.CinemaSurfaceVariant
import com.example.ui.theme.GrayText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsScreen(
    viewModel: CineViewModel,
    onTitleClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeListId by remember { mutableStateOf<Int?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    val customLists by viewModel.allCustomLists.collectAsState()

    if (activeListId == null) {
        // OVERVIEW: List of Custom Lists
        Scaffold(
            contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Mes Listes Thématiques",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.Black,
                    modifier = Modifier.testTag("fab_create_list")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Créer une liste")
                }
            },
            modifier = modifier
        ) { innerPadding ->
            if (customLists.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        message = "Vous n'avez créé aucune liste thématique.\nCliquez sur le bouton + ci-dessous pour créer votre première liste !"
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(customLists) { list ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("custom_list_item_${list.id}")
                                .clickable { activeListId = list.id },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = CinemaSurfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = list.name,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (list.description.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = list.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = GrayText,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Create List Dialog
            if (showCreateDialog) {
                var newListName by remember { mutableStateOf("") }
                var newListDesc by remember { mutableStateOf("") }

                AlertDialog(
                    onDismissRequest = { showCreateDialog = false },
                    title = { Text("Nouvelle Liste") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = newListName,
                                onValueChange = { newListName = it },
                                label = { Text("Nom de la liste") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_new_list_name")
                            )

                            OutlinedTextField(
                                value = newListDesc,
                                onValueChange = { newListDesc = it },
                                label = { Text("Description (optionnelle)") },
                                maxLines = 3,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_new_list_description")
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (newListName.trim().isNotBlank()) {
                                    viewModel.createCustomList(newListName, newListDesc)
                                    showCreateDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("btn_confirm_create_list")
                        ) {
                            Text("Créer", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCreateDialog = false }) {
                            Text("Annuler")
                        }
                    }
                )
            }
        }
    } else {
        // DETAIL VIEW: Content of a specific List
        val listId = activeListId!!
        val listDetail by viewModel.getCustomListDetail(listId).collectAsState(null)
        val listTitles by viewModel.getCustomListTitlesFlow(listId).collectAsState(emptyList())
        val context = LocalContext.current

        Scaffold(
            contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = listDetail?.name ?: "Détails de la liste",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { activeListId = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                viewModel.deleteCustomList(listId)
                                activeListId = null
                                Toast.makeText(context, "Liste supprimée !", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Supprimer la liste entière",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
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
                // List Description Header
                if (!listDetail?.description.isNullOrBlank()) {
                    Text(
                        text = listDetail?.description ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GrayText,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                if (listTitles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyState(
                            message = "Cette liste ne contient aucun titre.\n\nPour en ajouter un, rendez-vous sur la fiche détail d'un titre et cliquez sur 'Ajouter à une liste'."
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(listTitles) { index, item ->
                            val cineTitle = item.toCineTitle()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CinemaSurfaceVariant)
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Mini Poster
                                Box(
                                    modifier = Modifier
                                        .size(width = 40.dp, height = 60.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .clickable { onTitleClick(cineTitle.id) }
                                ) {
                                    if (cineTitle.posterUrl != null) {
                                        AsyncImage(
                                            model = cineTitle.posterUrl,
                                            contentDescription = cineTitle.title,
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
                                                tint = GrayText,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                // Text Title & Type
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onTitleClick(cineTitle.id) }
                                ) {
                                    Text(
                                        text = cineTitle.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    TypeBadge(type = cineTitle.type, compact = true)
                                }

                                // REORDER ACTIONS (Up / Down)
                                IconButton(
                                    onClick = {
                                        if (index > 0) {
                                            val reordered = listTitles.toMutableList()
                                            val current = reordered[index]
                                            reordered[index] = reordered[index - 1]
                                            reordered[index - 1] = current
                                            viewModel.reorderCustomListTitles(listId, reordered)
                                        }
                                    },
                                    enabled = index > 0,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropUp,
                                        contentDescription = "Monter l'élément",
                                        tint = if (index > 0) MaterialTheme.colorScheme.primary else Color.DarkGray
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        if (index < listTitles.size - 1) {
                                            val reordered = listTitles.toMutableList()
                                            val current = reordered[index]
                                            reordered[index] = reordered[index + 1]
                                            reordered[index + 1] = current
                                            viewModel.reorderCustomListTitles(listId, reordered)
                                        }
                                    },
                                    enabled = index < listTitles.size - 1,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Descendre l'élément",
                                        tint = if (index < listTitles.size - 1) MaterialTheme.colorScheme.primary else Color.DarkGray
                                    )
                                }

                                // Delete from list action
                                IconButton(
                                    onClick = { viewModel.removeTitleFromCustomList(item.id) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Retirer de la liste",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
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

private fun DbCustomListTitle.toCineTitle(): CineTitle {
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
