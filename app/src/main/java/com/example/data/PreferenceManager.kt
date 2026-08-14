package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cinelog_preferences")

class PreferenceManager(context: Context) {

    private val dataStore: DataStore<Preferences> = context.dataStore

    companion object {
        val KEY_TMDB_API_KEY = stringPreferencesKey("tmdb_api_key")
        val KEY_HOME_VIEW_MODE = stringPreferencesKey("home_view_mode")
        val KEY_HOME_COLLAPSED_CATEGORIES = stringSetPreferencesKey("home_collapsed_categories")
        val KEY_WATCHLIST_VIEW_MODE = stringPreferencesKey("watchlist_view_mode")
        val KEY_WATCHLIST_COLLAPSED_CATEGORIES = stringSetPreferencesKey("watchlist_collapsed_categories")
        val KEY_WATCHLIST_SORT = stringPreferencesKey("watchlist_sort")
        val KEY_WATCHLIST_TYPE_FILTER = stringPreferencesKey("watchlist_type_filter")
        val KEY_WATCHLIST_GENRE_FILTER = stringPreferencesKey("watchlist_genre_filter")
        val KEY_WATCHLIST_YEAR_FILTER = stringPreferencesKey("watchlist_year_filter")
        val KEY_HAS_DISMISSED_ONBOARDING = booleanPreferencesKey("has_dismissed_onboarding")
        val KEY_SEARCH_HISTORY = stringPreferencesKey("search_history")
        val KEY_PINNED_SEARCHES = stringPreferencesKey("pinned_searches")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")
    }

    private val preferencesFlow: Flow<Preferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }

    val tmdbApiKeyFlow: Flow<String> = preferencesFlow.map { prefs ->
        prefs[KEY_TMDB_API_KEY] ?: ""
    }.distinctUntilChanged()

    val hasDismissedOnboardingFlow: Flow<Boolean> = preferencesFlow.map { prefs ->
        prefs[KEY_HAS_DISMISSED_ONBOARDING] ?: false
    }.distinctUntilChanged()

    val themeModeFlow: Flow<String> = preferencesFlow.map { prefs ->
        prefs[KEY_THEME_MODE] ?: "DARK"
    }.distinctUntilChanged()

    val dynamicColorFlow: Flow<Boolean> = preferencesFlow.map { prefs ->
        prefs[KEY_DYNAMIC_COLOR] ?: false
    }.distinctUntilChanged()

    val appLanguageFlow: Flow<String> = preferencesFlow.map { prefs ->
        prefs[KEY_APP_LANGUAGE] ?: "system"
    }.distinctUntilChanged()

    val homeViewModeFlow: Flow<String> = preferencesFlow.map { prefs ->
        prefs[KEY_HOME_VIEW_MODE] ?: "LIST"
    }.distinctUntilChanged()

    val homeCollapsedCategoriesFlow: Flow<Set<String>> = preferencesFlow.map { prefs ->
        prefs[KEY_HOME_COLLAPSED_CATEGORIES] ?: emptySet()
    }.distinctUntilChanged()

    val watchlistViewModeFlow: Flow<String> = preferencesFlow.map { prefs ->
        prefs[KEY_WATCHLIST_VIEW_MODE] ?: "GRID"
    }.distinctUntilChanged()

    val watchlistCollapsedCategoriesFlow: Flow<Set<String>> = preferencesFlow.map { prefs ->
        prefs[KEY_WATCHLIST_COLLAPSED_CATEGORIES] ?: emptySet()
    }.distinctUntilChanged()

    val watchlistSortFlow: Flow<String> = preferencesFlow.map { prefs ->
        prefs[KEY_WATCHLIST_SORT] ?: "DATE_ADDED"
    }.distinctUntilChanged()

    val watchlistTypeFilterFlow: Flow<String> = preferencesFlow.map { prefs ->
        prefs[KEY_WATCHLIST_TYPE_FILTER] ?: ""
    }.distinctUntilChanged()

    val watchlistGenreFilterFlow: Flow<String> = preferencesFlow.map { prefs ->
        prefs[KEY_WATCHLIST_GENRE_FILTER] ?: ""
    }.distinctUntilChanged()

    val watchlistYearFilterFlow: Flow<String> = preferencesFlow.map { prefs ->
        prefs[KEY_WATCHLIST_YEAR_FILTER] ?: ""
    }.distinctUntilChanged()

    val searchHistoryFlow: Flow<List<String>> = preferencesFlow.map { prefs ->
        val raw = prefs[KEY_SEARCH_HISTORY] ?: ""
        if (raw.isBlank()) emptyList()
        else runCatching {
            val array = JSONArray(raw)
            List(array.length()) { array.getString(it) }
        }.getOrDefault(emptyList())
    }.distinctUntilChanged()

    val pinnedSearchesFlow: Flow<List<String>> = preferencesFlow.map { prefs ->
        val raw = prefs[KEY_PINNED_SEARCHES] ?: ""
        if (raw.isBlank()) emptyList()
        else runCatching {
            val array = JSONArray(raw)
            List(array.length()) { array.getString(it) }
        }.getOrDefault(emptyList())
    }.distinctUntilChanged()

    // Synchronous compatibility getters
    fun getTmdbApiKey(): String = runBlocking { tmdbApiKeyFlow.first() }
    fun hasDismissedOnboarding(): Boolean = runBlocking { hasDismissedOnboardingFlow.first() }
    fun getThemeMode(): String = runBlocking { themeModeFlow.first() }
    fun isDynamicColorEnabled(): Boolean = runBlocking { dynamicColorFlow.first() }
    fun getAppLanguage(): String = runBlocking { appLanguageFlow.first() }
    fun getHomeViewMode(): String = runBlocking { homeViewModeFlow.first() }
    fun getHomeCollapsedCategories(): Set<String> = runBlocking { homeCollapsedCategoriesFlow.first() }
    fun getWatchlistViewMode(): String = runBlocking { watchlistViewModeFlow.first() }
    fun getWatchlistCollapsedCategories(): Set<String> = runBlocking { watchlistCollapsedCategoriesFlow.first() }
    fun getWatchlistSort(): String = runBlocking { watchlistSortFlow.first() }
    fun getWatchlistTypeFilter(): String = runBlocking { watchlistTypeFilterFlow.first() }
    fun getWatchlistGenreFilter(): String = runBlocking { watchlistGenreFilterFlow.first() }
    fun getWatchlistYearFilter(): String = runBlocking { watchlistYearFilterFlow.first() }
    fun getSearchHistory(): List<String> = runBlocking { searchHistoryFlow.first() }
    fun getPinnedSearches(): List<String> = runBlocking { pinnedSearchesFlow.first() }

    // Suspend setters
    suspend fun updateTmdbApiKey(key: String) {
        dataStore.edit { prefs ->
            prefs[KEY_TMDB_API_KEY] = key.trim()
        }
    }

    suspend fun updateHasDismissedOnboarding(dismissed: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_HAS_DISMISSED_ONBOARDING] = dismissed
        }
    }

    suspend fun updateThemeMode(mode: String) {
        dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode
        }
    }

    suspend fun updateDynamicColorEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_DYNAMIC_COLOR] = enabled
        }
    }

    suspend fun updateAppLanguage(languageCode: String) {
        dataStore.edit { prefs ->
            prefs[KEY_APP_LANGUAGE] = languageCode
        }
    }

    suspend fun updateHomeViewMode(mode: String) {
        dataStore.edit { prefs ->
            prefs[KEY_HOME_VIEW_MODE] = mode
        }
    }

    suspend fun updateHomeCollapsedCategories(categories: Set<String>) {
        dataStore.edit { prefs ->
            prefs[KEY_HOME_COLLAPSED_CATEGORIES] = categories
        }
    }

    suspend fun updateWatchlistViewMode(mode: String) {
        dataStore.edit { prefs ->
            prefs[KEY_WATCHLIST_VIEW_MODE] = mode
        }
    }

    suspend fun updateWatchlistCollapsedCategories(categories: Set<String>) {
        dataStore.edit { prefs ->
            prefs[KEY_WATCHLIST_COLLAPSED_CATEGORIES] = categories
        }
    }

    suspend fun updateWatchlistSort(sort: String) {
        dataStore.edit { prefs ->
            prefs[KEY_WATCHLIST_SORT] = sort
        }
    }

    suspend fun updateWatchlistTypeFilter(type: String) {
        dataStore.edit { prefs ->
            prefs[KEY_WATCHLIST_TYPE_FILTER] = type
        }
    }

    suspend fun updateWatchlistGenreFilter(genre: String) {
        dataStore.edit { prefs ->
            prefs[KEY_WATCHLIST_GENRE_FILTER] = genre
        }
    }

    suspend fun updateWatchlistYearFilter(year: String) {
        dataStore.edit { prefs ->
            prefs[KEY_WATCHLIST_YEAR_FILTER] = year
        }
    }

    suspend fun updateSearchHistory(history: List<String>) {
        val array = JSONArray()
        history.take(10).forEach { array.put(it) }
        dataStore.edit { prefs ->
            prefs[KEY_SEARCH_HISTORY] = array.toString()
        }
    }

    suspend fun updatePinnedSearches(pinned: List<String>) {
        val array = JSONArray()
        pinned.forEach { array.put(it) }
        dataStore.edit { prefs ->
            prefs[KEY_PINNED_SEARCHES] = array.toString()
        }
    }

    // Synchronous compatibility setters
    fun setTmdbApiKey(key: String) = runBlocking { updateTmdbApiKey(key) }
    fun setHasDismissedOnboarding(dismissed: Boolean) = runBlocking { updateHasDismissedOnboarding(dismissed) }
    fun setThemeMode(mode: String) = runBlocking { updateThemeMode(mode) }
    fun setDynamicColorEnabled(enabled: Boolean) = runBlocking { updateDynamicColorEnabled(enabled) }
    fun setAppLanguage(languageCode: String) = runBlocking { updateAppLanguage(languageCode) }
    fun setHomeViewMode(mode: String) = runBlocking { updateHomeViewMode(mode) }
    fun setHomeCollapsedCategories(categories: Set<String>) = runBlocking { updateHomeCollapsedCategories(categories) }
    fun setWatchlistViewMode(mode: String) = runBlocking { updateWatchlistViewMode(mode) }
    fun setWatchlistCollapsedCategories(categories: Set<String>) = runBlocking { updateWatchlistCollapsedCategories(categories) }
    fun setWatchlistSort(sort: String) = runBlocking { updateWatchlistSort(sort) }
    fun setWatchlistTypeFilter(type: String) = runBlocking { updateWatchlistTypeFilter(type) }
    fun setWatchlistGenreFilter(genre: String) = runBlocking { updateWatchlistGenreFilter(genre) }
    fun setWatchlistYearFilter(year: String) = runBlocking { updateWatchlistYearFilter(year) }
    fun setSearchHistory(history: List<String>) = runBlocking { updateSearchHistory(history) }
    fun setPinnedSearches(pinned: List<String>) = runBlocking { updatePinnedSearches(pinned) }
}
