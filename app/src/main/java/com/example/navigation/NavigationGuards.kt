package com.example.navigation

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavOptionsBuilder

/**
 * Un écran n'est RESUMED qu'une fois au premier plan et sa transition d'entrée terminée.
 * Dès qu'on navigue ou qu'on dépile, il repasse sous RESUMED : les taps suivants qui arrivent
 * pendant le fondu de sortie (~220 ms) sont donc ignorés (#146), comme avec `dropUnlessResumed`.
 */
internal fun NavBackStackEntry.isResumed(): Boolean =
    lifecycle.currentState == Lifecycle.State.RESUMED

/** Navigue vers [route] seulement si [from] est l'écran au premier plan (pas de double empilement). */
fun NavController.navigateFrom(
    from: NavBackStackEntry,
    route: ScreenDestination,
    builder: NavOptionsBuilder.() -> Unit = {}
) {
    if (from.isResumed()) navigate(route, builder)
}

/** Dépile [from] seulement s'il est au premier plan : un double « Retour » ne vide plus la pile. */
fun NavController.popBackStackFrom(from: NavBackStackEntry) {
    if (from.isResumed()) popBackStack()
}

/** Ouvre un onglet principal comme la barre du bas : une seule instance, état sauvegardé/restauré. */
fun NavController.navigateToTab(tab: ScreenDestination) {
    navigate(tab) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
