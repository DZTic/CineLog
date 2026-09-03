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

    // Suspend getters for non-blocking asynchronous reads
    suspend fun getTmdbApiKeyAsync(): String = tmdbApiKeyFlow.first()
    suspend fun hasDismissedOnboardingAsync(): Boolean = hasDismissedOnboardingFlow.first()
    suspend fun getThemeModeAsync(): String = themeModeFlow.first()
    suspend fun isDynamicColorEnabledAsync(): Boolean = dynamicColorFlow.first()
    suspend fun getAppLanguageAsync(): String = appLanguageFlow.first()
    suspend fun getHomeViewModeAsync(): String = homeViewModeFlow.first()
    suspend fun getHomeCollapsedCategoriesAsync(): Set<String> = homeCollapsedCategoriesFlow.first()
    suspend fun getWatchlistViewModeAsync(): String = watchlistViewModeFlow.first()
    suspend fun getWatchlistCollapsedCategoriesAsync(): Set<String> = watchlistCollapsedCategoriesFlow.first()
    suspend fun getWatchlistSortAsync(): String = watchlistSortFlow.first()
    suspend fun getWatchlistTypeFilterAsync(): String = watchlistTypeFilterFlow.first()
    suspend fun getWatchlistGenreFilterAsync(): String = watchlistGenreFilterFlow.first()
    suspend fun getWatchlistYearFilterAsync(): String = watchlistYearFilterFlow.first()
    suspend fun getSearchHistoryAsync(): List<String> = searchHistoryFlow.first()
    suspend fun getPinnedSearchesAsync(): List<String> = pinnedSearchesFlow.first()

    // Deprecated synchronous getters (using runBlocking)
    @Deprecated("Utiliser tmdbApiKeyFlow ou getTmdbApiKeyAsync() pour éviter de bloquer le Main Thread.", ReplaceWith("tmdbApiKeyFlow.first()"))
    fun getTmdbApiKey(): String = runBlocking { tmdbApiKeyFlow.first() }

    @Deprecated("Utiliser hasDismissedOnboardingFlow ou hasDismissedOnboardingAsync() pour éviter de bloquer le Main Thread.", ReplaceWith("hasDismissedOnboardingFlow.first()"))
    fun hasDismissedOnboarding(): Boolean = runBlocking { hasDismissedOnboardingFlow.first() }

    @Deprecated("Utiliser themeModeFlow ou getThemeModeAsync() pour éviter de bloquer le Main Thread.", ReplaceWith("themeModeFlow.first()"))
    fun getThemeMode(): String = runBlocking { themeModeFlow.first() }

    @Deprecated("Utiliser dynamicColorFlow ou isDynamicColorEnabledAsync() pour éviter de bloquer le Main Thread.", ReplaceWith("dynamicColorFlow.first()"))
    fun isDynamicColorEnabled(): Boolean = runBlocking { dynamicColorFlow.first() }

    @Deprecated("Utiliser appLanguageFlow ou getAppLanguageAsync() pour éviter de bloquer le Main Thread.", ReplaceWith("appLanguageFlow.first()"))
    fun getAppLanguage(): String = runBlocking { appLanguageFlow.first() }

    @Deprecated("Utiliser homeViewModeFlow ou getHomeViewModeAsync() pour éviter de bloquer le Main Thread.", ReplaceWith("homeViewModeFlow.first()"))
    fun getHomeViewMode(): String = runBlocking { homeViewModeFlow.first() }

    @Deprecated("Utiliser homeCollapsedCategoriesFlow ou getHomeCollapsedCategoriesAsync() pour éviter de bloquer le Main Thread.", ReplaceWith("homeCollapsedCategoriesFlow.first()"))
    fun getHomeCollapsedCategories(): Set<String> = runBlocking { homeCollapsedCategoriesFlow.first() }

    @Deprecated("Utiliser watchlistViewModeFlow ou getWatchlistViewModeAsync() pour éviter de bloquer le Main Thread.", ReplaceWith("watchlistViewModeFlow.first()"))
    fun getWatchlistViewMode(): String = runBlocking { watchlistViewModeFlow.first() }

    @Deprecated("Utiliser watchlistCollapsedCategoriesFlow ou getWatchlistCollapsedCategoriesAsync() pour éviter de bloquer le Main Thread.", ReplaceWith("watchlistCollapsedCategoriesFlow.first()"))
    fun getWatchlistCollapsedCategories(): Set<String> = runBlocking { watchlistCollapsedCategoriesFlow.first() }

    @Deprecated("Utiliser watchlistSortFlow ou getWatchlistSortAsync() pour éviter de bloquer le Main Thread.", ReplaceWith("watchlistSortFlow.first()"))
    fun getWatchlistSort(): String = runBlocking { watchlistSortFlow.first() }

    @Deprecated("Utiliser watchlistTypeFilterFlow ou getWatchlistTypeFilterAsync() pour éviter de bloquer le Main Thread.", ReplaceWith("watchlistTypeFilterFlow.first()"))
    fun getWatchlistTypeFilter(): String = runBlocking { watchlistTypeFilterFlow.first() }

    @Deprecated("Utiliser watchlistGenreFilterFlow ou getWatchlistGenreFilterAsync() pour éviter de bloquer le Main Thread.", ReplaceWith("watchlistGenreFilterFlow.first()"))
    fun getWatchlistGenreFilter(): String = runBlocking { watchlistGenreFilterFlow.first() }

    @Deprecated("Utiliser watchlistYearFilterFlow ou getWatchlistYearFilterAsync() pour éviter de bloquer le Main Thread.", ReplaceWith("watchlistYearFilterFlow.first()"))
    fun getWatchlistYearFilter(): String = runBlocking { watchlistYearFilterFlow.first() }

    @Deprecated("Utiliser searchHistoryFlow ou getSearchHistoryAsync() pour éviter de bloquer le Main Thread.", ReplaceWith("searchHistoryFlow.first()"))
    fun getSearchHistory(): List<String> = runBlocking { searchHistoryFlow.first() }

    @Deprecated("Utiliser pinnedSearchesFlow ou getPinnedSearchesAsync() pour éviter de bloquer le Main Thread.", ReplaceWith("pinnedSearchesFlow.first()"))
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

    // Deprecated synchronous setters (using runBlocking)
    @Deprecated("Utiliser updateTmdbApiKey() dans une coroutine pour éviter de bloquer le Main Thread.", ReplaceWith("updateTmdbApiKey(key)"))
    fun setTmdbApiKey(key: String) = runBlocking {
        updateTmdbApiKey(key)
    }

    @Deprecated("Utiliser updateHasDismissedOnboarding() dans une coroutine pour éviter de bloquer le Main Thread.", ReplaceWith("updateHasDismissedOnboarding(dismissed)"))
    fun setHasDismissedOnboarding(dismissed: Boolean) = runBlocking {
        updateHasDismissedOnboarding(dismissed)
    }

    @Deprecated("Utiliser updateThemeMode() dans une coroutine pour éviter de bloquer le Main Thread.", ReplaceWith("updateThemeMode(mode)"))
    fun setThemeMode(mode: String) = runBlocking {
        updateThemeMode(mode)
    }

    @Deprecated("Utiliser updateDynamicColorEnabled() dans une coroutine pour éviter de bloquer le Main Thread.", ReplaceWith("updateDynamicColorEnabled(enabled)"))
    fun setDynamicColorEnabled(enabled: Boolean) = runBlocking {
        updateDynamicColorEnabled(enabled)
    }

    @Deprecated("Utiliser updateAppLanguage() dans une coroutine pour éviter de bloquer le Main Thread.", ReplaceWith("updateAppLanguage(languageCode)"))
    fun setAppLanguage(languageCode: String) = runBlocking {
        updateAppLanguage(languageCode)
    }

    @Deprecated("Utiliser updateHomeViewMode() dans une coroutine pour éviter de bloquer le Main Thread.", ReplaceWith("updateHomeViewMode(mode)"))
    fun setHomeViewMode(mode: String) = runBlocking {
        updateHomeViewMode(mode)
    }

    @Deprecated("Utiliser updateHomeCollapsedCategories() dans une coroutine pour éviter de bloquer le Main Thread.", ReplaceWith("updateHomeCollapsedCategories(categories)"))
    fun setHomeCollapsedCategories(categories: Set<String>) = runBlocking {
        updateHomeCollapsedCategories(categories)
    }

    @Deprecated("Utiliser updateWatchlistViewMode() dans une coroutine pour éviter de bloquer le Main Thread.", ReplaceWith("updateWatchlistViewMode(mode)"))
    fun setWatchlistViewMode(mode: String) = runBlocking {
        updateWatchlistViewMode(mode)
    }

    @Deprecated("Utiliser updateWatchlistCollapsedCategories() dans une coroutine pour éviter de bloquer le Main Thread.", ReplaceWith("updateWatchlistCollapsedCategories(categories)"))
    fun setWatchlistCollapsedCategories(categories: Set<String>) = runBlocking {
        updateWatchlistCollapsedCategories(categories)
    }

    @Deprecated("Utiliser updateWatchlistSort() dans une coroutine pour éviter de bloquer le Main Thread.", ReplaceWith("updateWatchlistSort(sort)"))
    fun setWatchlistSort(sort: String) = runBlocking {
        updateWatchlistSort(sort)
    }

    @Deprecated("Utiliser updateWatchlistTypeFilter() dans une coroutine pour éviter de bloquer le Main Thread.", ReplaceWith("updateWatchlistTypeFilter(type)"))
    fun setWatchlistTypeFilter(type: String) = runBlocking {
        updateWatchlistTypeFilter(type)
    }

    @Deprecated("Utiliser updateWatchlistGenreFilter() dans une coroutine pour éviter de bloquer le Main Thread.", ReplaceWith("updateWatchlistGenreFilter(genre)"))
    fun setWatchlistGenreFilter(genre: String) = runBlocking {
        updateWatchlistGenreFilter(genre)
    }

    @Deprecated("Utiliser updateWatchlistYearFilter() dans une coroutine pour éviter de bloquer le Main Thread.", ReplaceWith("updateWatchlistYearFilter(year)"))
    fun setWatchlistYearFilter(year: String) = runBlocking {
        updateWatchlistYearFilter(year)
    }

    @Deprecated("Utiliser updateSearchHistory() dans une coroutine pour éviter de bloquer le Main Thread.", ReplaceWith("updateSearchHistory(history)"))
    fun setSearchHistory(history: List<String>) = runBlocking {
        updateSearchHistory(history)
    }

    @Deprecated("Utiliser updatePinnedSearches() dans une coroutine pour éviter de bloquer le Main Thread.", ReplaceWith("updatePinnedSearches(pinned)"))
    fun setPinnedSearches(pinned: List<String>) = runBlocking {
        updatePinnedSearches(pinned)
    }
}
