package com.example.ui.discover

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.ui.CachedSaga
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DiscoverViewModel(
    private val repository: Repository
) : ViewModel() {
    private val tag = "DiscoverViewModel"

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

    val allWatchlist: StateFlow<List<DbWatchlist>> = repository.allWatchlist
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allLogs: StateFlow<List<DbLogEntry>> = repository.allLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val collectionCache: StateFlow<Map<String, CachedSaga>> = repository.collectionCache
        .map { list -> list.associate { it.titleId to CachedSaga(it.collectionId, it.collectionName, it.collectionPosterUrl) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _searchLoading = MutableStateFlow(false)
    val searchLoading: StateFlow<Boolean> = _searchLoading.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    private val _searchResults = MutableStateFlow<List<CineTitle>>(emptyList())
    val searchResults: StateFlow<List<CineTitle>> = _searchResults.asStateFlow()

    init {
        loadDiscoverContent()
    }

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
