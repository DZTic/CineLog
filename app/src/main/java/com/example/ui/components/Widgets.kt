package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.CineTitle
import com.example.data.TitleType
import com.example.ui.theme.CinemaPrimary
import com.example.ui.theme.CinemaSecondary
import com.example.ui.theme.CinemaTertiary
import com.example.ui.theme.GrayText
import com.example.ui.theme.OnCinemaBackground
import com.example.ui.theme.StarGold

/**
 * Badge visuel pour identifier le type de titre.
 */
@Composable
fun TypeBadge(
    type: TitleType,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val (backgroundColor, textColor, label) = when (type) {
        TitleType.FILM -> Triple(CinemaTertiary.copy(alpha = 0.15f), CinemaTertiary, "FILM")
        TitleType.SERIE -> Triple(Color(0xFFBB86FC).copy(alpha = 0.15f), Color(0xFFBB86FC), "SÉRIE")
        TitleType.ANIME -> Triple(CinemaPrimary.copy(alpha = 0.15f), CinemaPrimary, "ANIME")
    }

    val paddingValues = if (compact) {
        Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
    } else {
        Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    }

    val fontSize = if (compact) 9.sp else 11.sp

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .then(paddingValues)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

/**
 * Badge visuel affiché sur la fiche détail quand le titre a déjà été
 * journalisé au moins une fois, pour le voir en un coup d'œil sans avoir
 * à faire défiler jusqu'à la section "Vos visionnages".
 */
@Composable
fun WatchedBadge(
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val watchedGreen = Color(0xFF4CAF50)
    val paddingValues = if (compact) {
        Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
    } else {
        Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    }
    val fontSize = if (compact) 9.sp else 11.sp

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(watchedGreen.copy(alpha = 0.15f))
            .then(paddingValues),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = watchedGreen,
            modifier = Modifier.size(if (compact) 11.dp else 13.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "DÉJÀ VU",
            color = watchedGreen,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

/**
 * Composant de notation en demi-étoiles interactif (ou en lecture seule).
 */
@Composable
fun HalfStarRatingBar(
    rating: Float,
    onRatingChanged: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier,
    starSize: Dp = 24.dp
) {
    Row(modifier = modifier) {
        for (i in 1..5) {
            val starIndex = i.toFloat()
            Box(modifier = Modifier.size(starSize)) {
                val icon = when {
                    rating >= starIndex -> Icons.Filled.Star
                    rating >= starIndex - 0.5f -> Icons.AutoMirrored.Filled.StarHalf
                    else -> Icons.Filled.StarBorder
                }
                Icon(
                    imageVector = icon,
                    contentDescription = "Étoile $i",
                    tint = StarGold,
                    modifier = Modifier.fillMaxSize()
                )
                if (onRatingChanged != null) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(1f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    onRatingChanged(starIndex - 0.5f)
                                }
                        )
                        Box(
                            modifier = Modifier
                                  .fillMaxHeight()
                                  .weight(1f)
                                  .clickable(
                                      interactionSource = remember { MutableInteractionSource() },
                                      indication = null
                                  ) {
                                      onRatingChanged(starIndex)
                                  }
                          )
                      }
                  }
              }
          }
      }
  }

/**
 * Carte de titre pour afficher une affiche de film, série, anime sous forme de grille.
 */
@Composable
fun TitleCard(
    title: CineTitle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("title_card_${title.id}")
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
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
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = GrayText.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                
                // Overlay de type badge
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
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title.year,
                    style = MaterialTheme.typography.bodySmall,
                    color = GrayText
                )
                if (title.voteAverage > 0f) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = StarGold,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = String.format("%.1f", title.voteAverage),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = OnCinemaBackground
                    )
                }
            }
        }
    }
}

/**
 * État vide pour les listes.
 */
@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Movie,
                contentDescription = null,
                tint = GrayText.copy(alpha = 0.3f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = GrayText,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
