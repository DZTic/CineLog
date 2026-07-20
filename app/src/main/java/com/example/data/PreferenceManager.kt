package com.example.data

import android.content.Context
import android.content.SharedPreferences

class PreferenceManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("cinelog_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_TMDB_API_KEY = "tmdb_api_key"
        private const val KEY_HOME_VIEW_MODE = "home_view_mode"
        private const val KEY_HOME_COLLAPSED_CATEGORIES = "home_collapsed_categories"
    }

    fun getTmdbApiKey(): String {
        return prefs.getString(KEY_TMDB_API_KEY, "") ?: ""
    }

    fun setTmdbApiKey(key: String) {
        prefs.edit().putString(KEY_TMDB_API_KEY, key.trim()).apply()
    }

    // "LIST" (une carte par ligne, pleine largeur) ou "GRID" (grille
    // d'affiches à 3 colonnes). Stocké en String plutôt qu'en enum pour
    // rester tolérant si de nouveaux modes s'ajoutent plus tard.
    fun getHomeViewMode(): String {
        return prefs.getString(KEY_HOME_VIEW_MODE, "LIST") ?: "LIST"
    }

    fun setHomeViewMode(mode: String) {
        prefs.edit().putString(KEY_HOME_VIEW_MODE, mode).apply()
    }

    // Noms des catégories (FILM / SERIE / ANIME) actuellement réduites sur
    // l'écran d'accueil, pour laisser de la place aux autres.
    fun getHomeCollapsedCategories(): Set<String> {
        return prefs.getStringSet(KEY_HOME_COLLAPSED_CATEGORIES, emptySet()) ?: emptySet()
    }

    fun setHomeCollapsedCategories(categories: Set<String>) {
        // SharedPreferences ne permet pas de muter un Set retourné en
        // direct : on passe toujours une copie fraîche à putStringSet.
        prefs.edit().putStringSet(KEY_HOME_COLLAPSED_CATEGORIES, HashSet(categories)).apply()
    }
}
