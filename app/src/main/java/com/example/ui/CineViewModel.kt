package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CineViewModel(
    application: Application,
    private val repository: Repository,
    private val preferenceManager: PreferenceManager
) : AndroidViewModel(application) {

    private val tag = "CineViewModel"

    // ==========================================
    // ROOM PERSISTENT DATA STATE FLOWS
    // ==========================================

    val allLogs: StateFlow<List<DbLogEntry>> = repository.allLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allWatchlist: StateFlow<List<DbWatchlist>> = repository.allWatchlist
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCustomLists: StateFlow<List<DbCustomList>> = repository.allCustomLists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ==========================================
    // DISCOVER / COLD-START SCREEN STATE
    // ==========================================

    private val _discoverLoading = MutableStateFlow(false)
    val discoverLoading: StateFlow<Boolean> = _discoverLoading.asStateFlow()

    private val _discoverError = MutableStateFlow<String?>(null)
    val discoverError: StateFlow<String?> = _discoverError.asStateFlow()

    private val _trendingFilms = MutableStateFlow<List<CineTitle>>(emptyList())
    val trendingFilms: StateFlow<List<CineTitle>> = _trendingFilms.asStateFlow()

    private val _trendingSeries = MutableStateFlow<List<CineTitle>>(emptyList())
    val trendingSeries: StateFlow<List<CineTitle>> = _trendingSeries.asStateFlow()

    private val _topAnime = MutableStateFlow<List<CineTitle>>(emptyList())
    val topAnime: StateFlow<List<CineTitle>> = _topAnime.asStateFlow()

    // ==========================================
    // GLOBAL SEARCH STATE
    // ==========================================

    private val _searchLoading = MutableStateFlow(false)
    val searchLoading: StateFlow<Boolean> = _searchLoading.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    private val _searchResults = MutableStateFlow<List<CineTitle>>(emptyList())
    val searchResults: StateFlow<List<CineTitle>> = _searchResults.asStateFlow()

    // ==========================================
    // TITLE DETAIL STATE
    // ==========================================

    private val _detailLoading = MutableStateFlow(false)
    val detailLoading: StateFlow<Boolean> = _detailLoading.asStateFlow()

    private val _detailError = MutableStateFlow<String?>(null)
    val detailError: StateFlow<String?> = _detailError.asStateFlow()

    private val _currentTitle = MutableStateFlow<CineTitle?>(null)
    val currentTitle: StateFlow<CineTitle?> = _currentTitle.asStateFlow()

    private val _currentTitleLogs = MutableStateFlow<List<DbLogEntry>>(emptyList())
    val currentTitleLogs: StateFlow<List<DbLogEntry>> = _currentTitleLogs.asStateFlow()

    // ==========================================
    // API KEY MANAGEMENT
    // ==========================================

    private val _tmdbApiKey = MutableStateFlow(preferenceManager.getTmdbApiKey())
    val tmdbApiKey: StateFlow<String> = _tmdbApiKey.asStateFlow()

    init {
        loadDiscoverContent()
    }

    /**
     * Loads initial discover carousel contents from network/fallbacks
     */
    fun loadDiscoverContent() {
        viewModelScope.launch {
            _discoverLoading.value = true
            _discoverError.value = null
            try {
                _trendingFilms.value = repository.getTrendingOrPopular(TitleType.FILM)
                _trendingSeries.value = repository.getTrendingOrPopular(TitleType.SERIE)
                _topAnime.value = repository.getTrendingOrPopular(TitleType.ANIME)
            } catch (e: Exception) {
                Log.e(tag, "Error loading discover content: ${e.localizedMessage}")
                _discoverError.value = "Impossible de récupérer tout le contenu. Veuillez vérifier votre clé TMDB."
            } finally {
                _discoverLoading.value = false
            }
        }
    }

    /**
     * Sets user's TMDB API Key and triggers a reload of TMDB contents
     */
    fun setTmdbApiKey(key: String) {
        preferenceManager.setTmdbApiKey(key)
        _tmdbApiKey.value = key
        loadDiscoverContent()
    }

    /**
     * Global Search with error handling & filters
     */
    fun performSearch(query: String, filter: TitleType? = null) {
        viewModelScope.launch {
            if (query.trim().isEmpty()) {
                _searchResults.value = emptyList()
                return@launch
            }
            _searchLoading.value = true
            _searchError.value = null
            try {
                val results = repository.searchTitles(query, filter)
                _searchResults.value = results
                if (results.isEmpty() && filter != TitleType.ANIME && _tmdbApiKey.value.isEmpty()) {
                    _searchError.value = "Aucun résultat TMDB. Configurez votre clé API TMDB dans les paramètres !"
                }
            } catch (e: Exception) {
                Log.e(tag, "Error performing search: ${e.localizedMessage}")
                _searchError.value = "Erreur de connexion. Veuillez réessayer."
            } finally {
                _searchLoading.value = false
            }
        }
    }

    /**
     * Fetch Details of movie, show, or anime
     */
    fun loadTitleDetail(titleId: String) {
        viewModelScope.launch {
            _detailLoading.value = true
            _detailError.value = null
            _currentTitle.value = null
            try {
                // Read details from API
                val detail = repository.getTitleDetail(titleId)
                _currentTitle.value = detail

                // Read local journal logs for this title in a separate coroutine to avoid blocking
                viewModelScope.launch {
                    repository.getLogsForTitle(titleId).collect { logs ->
                        _currentTitleLogs.value = logs
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error loading title detail: ${e.localizedMessage}")
                _detailError.value = e.localizedMessage ?: "Erreur de chargement des détails."
            } finally {
                _detailLoading.value = false
            }
        }
    }

    // ==========================================
    // LOCAL LOG JOURNAL WRITING ACTIONS
    // ==========================================

    fun logVisionnage(
        titleId: String,
        titleType: String,
        titleName: String,
        titlePosterUrl: String?,
        dateVue: Long,
        note: Float,
        critique: String,
        revisionnage: Boolean,
        spoiler: Boolean
    ) {
        viewModelScope.launch {
            try {
                val entry = DbLogEntry(
                    titleId = titleId,
                    titleType = titleType,
                    titleName = titleName,
                    titlePosterUrl = titlePosterUrl,
                    dateVue = dateVue,
                    note = note,
                    critique = critique,
                    revisionnage = revisionnage,
                    spoiler = spoiler
                )
                repository.insertLog(entry)
                
                // If it was in the watchlist, remove it as it is now viewed! (Standard Letterboxd behavior)
                repository.removeFromWatchlist(titleId)
            } catch (e: Exception) {
                Log.e(tag, "Error saving log entry: ${e.localizedMessage}")
            }
        }
    }

    fun deleteLog(id: Int) {
        viewModelScope.launch {
            try {
                repository.deleteLogById(id)
            } catch (e: Exception) {
                Log.e(tag, "Error deleting log: ${e.localizedMessage}")
            }
        }
    }

    // ==========================================
    // WATCHLIST MANAGEMENT
    // ==========================================

    fun isTitleInWatchlist(titleId: String): StateFlow<Boolean> {
        val flow = MutableStateFlow(false)
        viewModelScope.launch {
            repository.isInWatchlist(titleId).collect {
                flow.value = it
            }
        }
        return flow
    }

    fun toggleWatchlist(titleId: String, type: TitleType, name: String, posterUrl: String?) {
        viewModelScope.launch {
            try {
                val isIn = repository.isInWatchlist(titleId).first()
                if (isIn) {
                    repository.removeFromWatchlist(titleId)
                } else {
                    repository.addToWatchlist(
                        DbWatchlist(
                            titleId = titleId,
                            titleType = type.name,
                            titleName = name,
                            titlePosterUrl = posterUrl
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(tag, "Error toggling watchlist: ${e.localizedMessage}")
            }
        }
    }

    // ==========================================
    // CUSTOM LISTS ACTIONS
    // ==========================================

    fun createCustomList(name: String, description: String) {
        viewModelScope.launch {
            try {
                repository.createCustomList(name, description)
            } catch (e: Exception) {
                Log.e(tag, "Error creating custom list: ${e.localizedMessage}")
            }
        }
    }

    fun deleteCustomList(listId: Int) {
        viewModelScope.launch {
            try {
                repository.deleteCustomList(listId)
            } catch (e: Exception) {
                Log.e(tag, "Error deleting custom list: ${e.localizedMessage}")
            }
        }
    }

    fun addTitleToCustomList(listId: Int, title: CineTitle) {
        viewModelScope.launch {
            try {
                val currentList = repository.getCustomListTitles(listId).first()
                val nextIndex = currentList.size
                repository.addTitleToCustomList(
                    listId = listId,
                    titleId = title.id,
                    titleType = title.type.name,
                    titleName = title.title,
                    titlePosterUrl = title.posterUrl,
                    orderIndex = nextIndex
                )
            } catch (e: Exception) {
                Log.e(tag, "Error adding title to custom list: ${e.localizedMessage}")
            }
        }
    }

    fun getCustomListDetail(listId: Int): StateFlow<DbCustomList?> {
        val state = MutableStateFlow<DbCustomList?>(null)
        viewModelScope.launch {
            repository.getCustomListById(listId).collect {
                state.value = it
            }
        }
        return state
    }

    fun getCustomListTitlesFlow(listId: Int): StateFlow<List<DbCustomListTitle>> {
        val state = MutableStateFlow<List<DbCustomListTitle>>(emptyList())
        viewModelScope.launch {
            repository.getCustomListTitles(listId).collect {
                state.value = it
            }
        }
        return state
    }

    fun removeTitleFromCustomList(id: Int) {
        viewModelScope.launch {
            try {
                repository.removeTitleFromCustomList(id)
            } catch (e: Exception) {
                Log.e(tag, "Error removing item from custom list: ${e.localizedMessage}")
            }
        }
    }

    fun reorderCustomListTitles(listId: Int, items: List<DbCustomListTitle>) {
        viewModelScope.launch {
            try {
                items.forEachIndexed { index, item ->
                    repository.updateCustomListTitleOrder(item.id, index)
                }
            } catch (e: Exception) {
                Log.e(tag, "Error reordering custom list: ${e.localizedMessage}")
            }
        }
    }
}

// Simple Factory provider
class CineViewModelFactory(
    private val application: Application,
    private val repository: Repository,
    private val preferenceManager: PreferenceManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CineViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CineViewModel(application, repository, preferenceManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
