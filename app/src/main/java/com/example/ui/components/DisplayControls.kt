package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.CollectionViewMode
import com.example.ui.theme.GrayText

/**
 * Sélecteur Liste / Grille réutilisé partout où l'utilisateur choisit
 * comment afficher une collection de titres (Accueil, Watchlist, ...) :
 * une carte pleine largeur par titre contre une grille d'affiches à 3
 * colonnes.
 */
@Composable
fun ViewModeToggle(
    viewMode: CollectionViewMode,
    onViewModeChange: (CollectionViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        ViewModeToggleButton(
            icon = Icons.Default.ViewList,
            contentDescription = "Afficher en liste",
            selected = viewMode == CollectionViewMode.LIST,
            onClick = { onViewModeChange(CollectionViewMode.LIST) }
        )
        ViewModeToggleButton(
            icon = Icons.Default.GridView,
            contentDescription = "Afficher en grille",
            selected = viewMode == CollectionViewMode.GRID,
            onClick = { onViewModeChange(CollectionViewMode.GRID) }
        )
    }
}

@Composable
private fun ViewModeToggleButton(
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
 * En-tête de catégorie cliquable (Films / Séries / Animes) : un chevron
 * pivote pour indiquer si la section est développée ou réduite. Réduire une
 * catégorie permet de faire de la place à l'écran pour mieux voir les
 * autres, sans rien supprimer : l'état est retenu et les items
 * réapparaissent en un tap. Partagé entre Accueil et Watchlist.
 */
@Composable
fun CollapsibleCategoryHeader(
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
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
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
