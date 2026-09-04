package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------------------
// Shimmer brush helper
// ---------------------------------------------------------------------------

private val SHIMMER_COLORS = listOf(
    Color(0xFF2A2A2A),
    Color(0xFF3D3D3D),
    Color(0xFF2A2A2A),
)

/**
 * Retourne un [Brush] anime en degrade horizontal (shimmer) qui se deplace
 * en continu de gauche a droite, donnant l'illusion d'une lumiere qui balaie
 * le squelette de contenu.
 */
@Composable
fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    return Brush.linearGradient(
        colors = SHIMMER_COLORS,
        start = Offset(translateAnim - 300f, 0f),
        end = Offset(translateAnim, 0f)
    )
}

// ---------------------------------------------------------------------------
// Skeleton individuel (carte d'affiche)
// ---------------------------------------------------------------------------

/**
 * Squelette anime d'une [TitleCard] — meme ratio d'aspect 2:3, meme
 * structure avec bloc image + deux lignes de texte en dessous.
 */
@Composable
fun SkeletonTitleCard(
    modifier: Modifier = Modifier,
    brush: Brush = shimmerBrush()
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(brush)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(10.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )
    }
}

// ---------------------------------------------------------------------------
// Skeleton pour les lignes de liste (Watchlist list-mode)
// ---------------------------------------------------------------------------

/**
 * Squelette anime d'une ligne pleine largeur de la Watchlist (mode Liste).
 */
@Composable
fun SkeletonListRow(
    modifier: Modifier = Modifier,
    brush: Brush = shimmerBrush()
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier
                .width(50.dp)
                .height(75.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Skeleton pour Discover : CarouselSection + grille
// ---------------------------------------------------------------------------

/**
 * Squelette d'une section Carousel (titre de section + rangee de cartes).
 */
@Composable
fun SkeletonCarouselSection(
    cardCount: Int = 5,
    modifier: Modifier = Modifier,
    brush: Brush = shimmerBrush()
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .width(140.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            userScrollEnabled = false,
            modifier = Modifier.fillMaxWidth()
        ) {
            items(cardCount) {
                SkeletonTitleCard(modifier = Modifier.width(110.dp), brush = brush)
            }
        }
    }
}

/**
 * Squelette complet de l'ecran Decouvrir (3 sections carrousel).
 */
@Composable
fun SkeletonDiscoverContent(
    modifier: Modifier = Modifier
) {
    val brush = shimmerBrush()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SkeletonCarouselSection(brush = brush)
        SkeletonCarouselSection(brush = brush)
        SkeletonCarouselSection(brush = brush)
    }
}

/**
 * Squelette de la vue Grille filtree de l'ecran Decouvrir.
 */
@Composable
fun SkeletonDiscoverGrid(
    itemCount: Int = 9,
    modifier: Modifier = Modifier
) {
    val brush = shimmerBrush()
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        userScrollEnabled = false,
        modifier = modifier.fillMaxSize()
    ) {
        items(itemCount) {
            SkeletonTitleCard(brush = brush)
        }
    }
}

// ---------------------------------------------------------------------------
// Skeleton pour la Watchlist
// ---------------------------------------------------------------------------

/**
 * Squelette complet de la Watchlist (grille).
 */
@Composable
fun SkeletonWatchlistGrid(
    itemCount: Int = 9,
    modifier: Modifier = Modifier
) {
    val brush = shimmerBrush()
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        userScrollEnabled = false,
        modifier = modifier.fillMaxSize()
    ) {
        items(itemCount) {
            SkeletonTitleCard(brush = brush)
        }
    }
}

/**
 * Squelette complet de la Watchlist (liste).
 */
@Composable
fun SkeletonWatchlistList(
    itemCount: Int = 6,
    modifier: Modifier = Modifier
) {
    val brush = shimmerBrush()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        repeat(itemCount) {
            SkeletonListRow(brush = brush)
        }
    }
}

// ---------------------------------------------------------------------------
// Skeleton pour le Profil
// ---------------------------------------------------------------------------

/**
 * Squelette d'une carte de statistiques ou de graphique.
 */
@Composable
fun SkeletonStatCard(
    height: Int = 100,
    modifier: Modifier = Modifier,
    brush: Brush = shimmerBrush()
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(brush)
    )
}

/**
 * Squelette complet de l'ecran Profil (panneau stats + 3 graphiques).
 */
@Composable
fun SkeletonProfileContent(
    modifier: Modifier = Modifier
) {
    val brush = shimmerBrush()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SkeletonStatCard(height = 100, brush = brush)
        SkeletonStatCard(height = 120, brush = brush)
        SkeletonStatCard(height = 200, brush = brush)
        SkeletonStatCard(height = 220, brush = brush)
    }
}
