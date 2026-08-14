package com.example.data

import android.content.Context
import android.content.SharedPreferences

class PreferenceManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("cinelog_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_TMDB_API_KEY = "tmdb_api_key"
        private const val KEY_HOME_VIEW_MODE = "home_view_mode"
        private const val KEY_HOME_COLLAPSED_CATEGORIES = "home_collapsed_categories"
        private const val KEY_WATCHLIST_VIEW_MODE = "watchlist_view_mode"
        private const val KEY_WATCHLIST_COLLAPSED_CATEGORIES = "watchlist_collapsed_categories"
        private const val KEY_WATCHLIST_SORT = "watchlist_sort"
        private const val KEY_WATCHLIST_TYPE_FILTER = "watchlist_type_filter"
        private const val KEY_WATCHLIST_GENRE_FILTER = "watchlist_genre_filter"
        private const val KEY_WATCHLIST_YEAR_FILTER = "watchlist_year_filter"
        private const val KEY_HAS_DISMISSED_ONBOARDING = "has_dismissed_onboarding"
        private const val KEY_SEARCH_HISTORY = "search_history"
        private const val KEY_PINNED_SEARCHES = "pinned_searches"
        private const val KEY_APP_LANGUAGE = "app_language"
    }

    fun getTmdbApiKey(): String {
        return prefs.getString(KEY_TMDB_API_KEY, "") ?: ""
    }

    fun setTmdbApiKey(key: String) {
        prefs.edit().putString(KEY_TMDB_API_KEY, key.trim()).apply()
    }

    fun hasDismissedOnboarding(): Boolean {
        return prefs.getBoolean(KEY_HAS_DISMISSED_ONBOARDING, false)
    }

    fun setHasDismissedOnboarding(dismissed: Boolean) {
        prefs.edit().putBoolean(KEY_HAS_DISMISSED_ONBOARDING, dismissed).apply()
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

    // Même principe que pour l'accueil, mais stocké sous une clé séparée :
    // Watchlist n'a pas forcément le même mode d'affichage préféré.
    fun getWatchlistViewMode(): String {
        return prefs.getString(KEY_WATCHLIST_VIEW_MODE, "GRID") ?: "GRID"
    }

    fun setWatchlistViewMode(mode: String) {
        prefs.edit().putString(KEY_WATCHLIST_VIEW_MODE, mode).apply()
    }

    fun getWatchlistCollapsedCategories(): Set<String> {
        return prefs.getStringSet(KEY_WATCHLIST_COLLAPSED_CATEGORIES, emptySet()) ?: emptySet()
    }

    fun setWatchlistCollapsedCategories(categories: Set<String>) {
        prefs.edit().putStringSet(KEY_WATCHLIST_COLLAPSED_CATEGORIES, HashSet(categories)).apply()
    }

    // Tri et filtres de la Watchlist (issue #33). Tous stockes en String
    // (noms d'enum ou "" pour "aucun filtre"), ce qui reste tolerant aux
    // valeurs ajoutees ou retirees plus tard : cote lecture on fait un
    // runCatching valueOf.
    fun getWatchlistSort(): String {
        return prefs.getString(KEY_WATCHLIST_SORT, "DATE_ADDED") ?: "DATE_ADDED"
    }

    fun setWatchlistSort(sort: String) {
        prefs.edit().putString(KEY_WATCHLIST_SORT, sort).apply()
    }

    fun getWatchlistTypeFilter(): String {
        return prefs.getString(KEY_WATCHLIST_TYPE_FILTER, "") ?: ""
    }

    fun setWatchlistTypeFilter(type: String) {
        prefs.edit().putString(KEY_WATCHLIST_TYPE_FILTER, type).apply()
    }

    fun getWatchlistGenreFilter(): String {
        return prefs.getString(KEY_WATCHLIST_GENRE_FILTER, "") ?: ""
    }

    fun setWatchlistGenreFilter(genre: String) {
        prefs.edit().putString(KEY_WATCHLIST_GENRE_FILTER, genre).apply()
    }

    fun getWatchlistYearFilter(): String {
        return prefs.getString(KEY_WATCHLIST_YEAR_FILTER, "") ?: ""
    }

   fun setWatchlistYearFilter(year: String) {
       prefs.edit().putString(KEY_WATCHLIST_YEAR_FILTER, year).apply()
   }

    // Historique de recherche (Issue #32) - stocke sous forme de JSON array
    fun getSearchHistory(): List<String> {
        val raw = prefs.getString(KEY_SEARCH_HISTORY, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return try {
            val array = org.json.JSONArray(raw)
            List(array.length()) { array.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun setSearchHistory(history: List<String>) {
        val array = org.json.JSONArray()
        history.take(10).forEach { array.put(it) }
        prefs.edit().putString(KEY_SEARCH_HISTORY, array.toString()).apply()
    }

    // Recherches sauvegardees / epinglees (Issue #32)
    fun getPinnedSearches(): List<String> {
        val raw = prefs.getString(KEY_PINNED_SEARCHES, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return try {
            val array = org.json.JSONArray(raw)
            List(array.length()) { array.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun setPinnedSearches(pinned: List<String>) {
        val array = org.json.JSONArray()
        pinned.forEach { array.put(it) }
        prefs.edit().putString(KEY_PINNED_SEARCHES, array.toString()).apply()
    }

    fun getAppLanguage(): String {
        return prefs.getString(KEY_APP_LANGUAGE, "system") ?: "system"
    }

    fun setAppLanguage(language: String) {
        prefs.edit().putString(KEY_APP_LANGUAGE, language).apply()
    }
}
