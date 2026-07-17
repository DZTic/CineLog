package com.example.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.CineViewModel
import com.example.ui.theme.CinemaSurfaceVariant
import com.example.ui.theme.GrayText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: CineViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val savedKey by viewModel.tmdbApiKey.collectAsState()

    var inputKey by remember { mutableStateOf(savedKey) }
    var isKeyVisible by remember { mutableStateOf(false) }

    // Sync inputKey when savedKey changes (e.g. on launch)
    LaunchedEffect(savedKey) {
        inputKey = savedKey
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Paramètres",
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Explanatory Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CinemaSurfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Configuration de la source TMDB",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Pour pouvoir rechercher des films et des séries et consulter leurs détails, vous devez posséder une clé API gratuite de The Movie Database (TMDB).\n\n" +
                                "💡 Les animes (Jikan/MyAnimeList) fonctionnent gratuitement sans clé.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray,
                        lineHeight = 20.sp
                    )
                }
            }

            // Key Input Box
            Text(
                text = "Clé API TMDB (v3)",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )

            OutlinedTextField(
                value = inputKey,
                onValueChange = { inputKey = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tmdb_api_key_field"),
                placeholder = { Text("Collez votre clé API TMDB ici...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                        Icon(
                            imageVector = if (isKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (isKeyVisible) "Masquer la clé" else "Afficher la clé"
                        )
                    }
                },
                singleLine = true,
                visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.DarkGray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            // Dynamic Status Indicator
            if (savedKey.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "✓ Une clé API est actuellement configurée et active.",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "⚠ Clé API absente. Recherche et carrousels de films/séries désactivés.",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Action save button
            Button(
                onClick = {
                    viewModel.setTmdbApiKey(inputKey.trim())
                    Toast.makeText(context, "Clé API enregistrée avec succès !", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("save_api_key_btn")
            ) {
                Text(
                    text = "Enregistrer les modifications",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            // Simple Guide
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CinemaSurfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Comment obtenir une clé ?",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "1. Créez un compte gratuit sur themoviedb.org\n" +
                                "2. Allez dans les Paramètres de votre profil, puis section 'API'\n" +
                                "3. Demandez une clé d'accès développeur\n" +
                                "4. Copiez la clé API (v3 auth) et collez-la ci-dessus !",
                        style = MaterialTheme.typography.bodySmall,
                        color = GrayText,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}
