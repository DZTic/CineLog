package com.example.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.ui.CachedSaga
import com.example.ui.CollectionViewMode
import com.example.util.NetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: Repository,
    private val preferenceManager: PreferenceManager,
    private val networkMonitor: NetworkMonitor? = null
) : ViewModel() {
    private val tag = "HomeViewModel"

    val allLogs: StateFlow<List<DbLogEntry>> = repository.allLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allWatchlist: StateFlow<List<DbWatchlist>> = repository.allWatchlist
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val collectionCache: StateFlow<Map<String, CachedSaga>> = repository.collectionCache
        .map { list -> list.associate { it.titleId to CachedSaga(it.collectionId, it.collectionName, it.collectionPosterUrl) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val sagaSizeCache: StateFlow<Map<Int, Int>> = repository.sagaSizeCache
        .map { list -> list.associate { it.collectionId to it.totalFilms } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _homeViewMode = MutableStateFlow(
        runCatching { CollectionViewMode.valueOf(preferenceManager.getHomeViewMode()) }
            .getOrDefault(CollectionViewMode.LIST)
    )
    val homeViewMode: StateFlow<CollectionViewMode> = _homeViewMode.asStateFlow()

    fun setHomeViewMode(mode: CollectionViewMode) {
        _homeViewMode.value = mode
        preferenceManager.setHomeViewMode(mode.name)
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private val _homeCollapsedCategories = MutableStateFlow(
        preferenceManager.getHomeCollapsedCategories()
    )
    val homeCollapsedCategories: StateFlow<Set<String>> = _homeCollapsedCategories.asStateFlow()

    fun toggleHomeCategoryCollapsed(categoryKey: String) {
        val updated = _homeCollapsedCategories.value.toMutableSet().apply {
            if (!add(categoryKey)) remove(categoryKey)
        }
        _homeCollapsedCategories.value = updated
        preferenceManager.setHomeCollapsedCategories(updated)
    }

    private val _tmdbApiKey = MutableStateFlow(preferenceManager.getTmdbApiKey())
    val tmdbApiKey: StateFlow<String> = _tmdbApiKey.asStateFlow()

    private val _hasDismissedOnboarding = MutableStateFlow(preferenceManager.hasDismissedOnboarding())
    val hasDismissedOnboarding: StateFlow<Boolean> = _hasDismissedOnboarding.asStateFlow()

    fun dismissOnboarding() {
        preferenceManager.setHasDismissedOnboarding(true)
        _hasDismissedOnboarding.value = true
    }

    private val _trendingFilms = MutableStateFlow<List<CineTitle>>(emptyList())
    val trendingFilms: StateFlow<List<CineTitle>> = _trendingFilms.asStateFlow()

    private val _trendingSeries = MutableStateFlow<List<CineTitle>>(emptyList())
    val trendingSeries: StateFlow<List<CineTitle>> = _trendingSeries.asStateFlow()

    init {
        loadSuggestions()
        if (networkMonitor != null) {
            viewModelScope.launch {
                networkMonitor.isOnline
                    .drop(1)
                    .filter { it }
                    .collect {
                        loadSuggestions()
                    }
            }
        }
    }

    fun refreshSuggestions() {
        loadSuggestions()
    }

    private fun loadSuggestions() {
        viewModelScope.launch {
            try {
                coroutineScope {
                    val filmsDeferred = async(Dispatchers.IO) { repository.getUnwatchedTrendingOrPopular(TitleType.FILM, 10) }
                    val seriesDeferred = async(Dispatchers.IO) { repository.getUnwatchedTrendingOrPopular(TitleType.SERIE, 10) }
                    _trendingFilms.value = filmsDeferred.await()
                    _trendingSeries.value = seriesDeferred.await()
                }
            } catch (e: Exception) {
                Log.e(tag, "Error loading home suggestions: ${e.localizedMessage}")
            }
        }
    }

    fun ensureSagaSizeLoaded(collectionId: Int) {
        if (sagaSizeCache.value.containsKey(collectionId)) return
        viewModelScope.launch {
            try {
                repository.ensureSagaSizeCached(collectionId)
            } catch (e: Exception) {
                Log.e(tag, "Error caching saga size for $collectionId: ${e.localizedMessage}")
            }
        }
    }
}
