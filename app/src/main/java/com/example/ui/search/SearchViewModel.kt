package com.example.ui.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.ui.CachedSaga
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SearchViewModel(
    private val repository: Repository,
    private val preferenceManager: PreferenceManager
) : ViewModel() {
    private val tag = "SearchViewModel"

    private val _searchLoading = MutableStateFlow(false)
    val searchLoading: StateFlow<Boolean> = _searchLoading.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    private val _searchResults = MutableStateFlow<List<CineTitle>>(emptyList())
    val searchResults: StateFlow<List<CineTitle>> = _searchResults.asStateFlow()

    private val _searchHistory = MutableStateFlow(preferenceManager.getSearchHistory())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    private val _pinnedSearches = MutableStateFlow(preferenceManager.getPinnedSearches())
    val pinnedSearches: StateFlow<List<String>> = _pinnedSearches.asStateFlow()

    private val _tmdbApiKey = MutableStateFlow(preferenceManager.getTmdbApiKey())
    val tmdbApiKey: StateFlow<String> = _tmdbApiKey.asStateFlow()

    private val _trendingFilms = MutableStateFlow<List<CineTitle>>(emptyList())
    val trendingFilms: StateFlow<List<CineTitle>> = _trendingFilms.asStateFlow()

    private val _trendingSeries = MutableStateFlow<List<CineTitle>>(emptyList())
    val trendingSeries: StateFlow<List<CineTitle>> = _trendingSeries.asStateFlow()

    private val _topAnime = MutableStateFlow<List<CineTitle>>(emptyList())
    val topAnime: StateFlow<List<CineTitle>> = _topAnime.asStateFlow()

    val collectionCache: StateFlow<Map<String, CachedSaga>> = repository.collectionCache
        .map { list -> list.associate { it.titleId to CachedSaga(it.collectionId, it.collectionName, it.collectionPosterUrl) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private var searchJob: Job? = null

    init {
        loadPopularSuggestions()
    }

    private fun loadPopularSuggestions() {
        viewModelScope.launch {
            try {
                _trendingFilms.value = repository.getTrendingOrPopular(TitleType.FILM)
                _trendingSeries.value = repository.getTrendingOrPopular(TitleType.SERIE)
                _topAnime.value = repository.getTrendingOrPopular(TitleType.ANIME)
            } catch (e: Exception) {
                Log.e(tag, "Error loading search suggestions: ${e.localizedMessage}")
            }
        }
    }

    fun addSearchHistory(query: String) {
        val q = query.trim()
        if (q.length < 2) return
        val updated = (listOf(q) + _searchHistory.value.filterNot { it.equals(q, ignoreCase = true) }).take(10)
        _searchHistory.value = updated
        preferenceManager.setSearchHistory(updated)
    }

    fun removeSearchHistory(query: String) {
        val updated = _searchHistory.value.filterNot { it.equals(query, ignoreCase = true) }
        _searchHistory.value = updated
        preferenceManager.setSearchHistory(updated)
    }
    fun clearSearchHistory() {
        _searchHistory.value = emptyList()
        preferenceManager.setSearchHistory(emptyList())
    }

    fun togglePinSearch(query: String) {
        val q = query.trim()
        if (q.isBlank()) return
        val current = _pinnedSearches.value.toMutableList()
        val exists = current.any { it.equals(q, ignoreCase = true) }
        if (exists) {
            current.removeAll { it.equals(q, ignoreCase = true) }
        } else {
            current.add(0, q)
        }
        _pinnedSearches.value = current
        preferenceManager.setPinnedSearches(current)
    }

    fun performSearch(query: String, filter: TitleType? = null, debounceMs: Long = 0L) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (debounceMs > 0) {
                delay(debounceMs)
            }
            if (query.trim().isEmpty()) {
                _searchResults.value = emptyList()
                return@launch
            }
            _searchLoading.value = true
            _searchError.value = null
            try {
                val results = repository.searchTitles(query, filter)
                val cache = collectionCache.value
                _searchResults.value = results.map { title ->
                    if (title.collectionId == null) {
                        val cached = cache[title.id]
                        if (cached != null) {
                            title.copy(
                                collectionId = cached.collectionId,
                                collectionName = cached.collectionName,
                                collectionPosterUrl = cached.posterUrl
                            )
                        } else {
                            title
                        }
                    } else {
                        title
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error performing search: ${e.localizedMessage}")
                _searchError.value = "Erreur de connexion. Veuillez réessayer."
            } finally {
                _searchLoading.value = false
            }
        }
    }
}
