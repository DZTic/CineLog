package com.example.ui.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.example.data.*
import com.example.ui.CachedSaga
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val repository: Repository,
    private val preferenceManager: PreferenceManager
) : ViewModel() {
    private val tag = "SearchViewModel"

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow<TitleType?>(null)
    val selectedFilter: StateFlow<TitleType?> = _selectedFilter.asStateFlow()

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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val searchPagingFlow: Flow<PagingData<CineTitle>> = combine(_searchQuery, _selectedFilter) { query, filter ->
        Pair(query.trim(), filter)
    }
        .debounce(350)
        .flatMapLatest { (query, filter) ->
            if (query.length < 2) {
                flowOf(PagingData.empty())
            } else {
                Pager(
                    config = PagingConfig(pageSize = 20, enablePlaceholders = false),
                    pagingSourceFactory = { SearchPagingSource(repository, query, filter) }
                ).flow.map { pagingData ->
                    val cache = collectionCache.value
                    pagingData.map { title ->
                        if (title.collectionId == null) {
                            val cached = cache[title.id]
                            if (cached != null) {
                                title.copy(
                                    collectionId = cached.collectionId,
                                    collectionName = cached.collectionName,
                                    collectionPosterUrl = cached.posterUrl
                                )
                            } else title
                        } else title
                    }
                }
            }
        }
        .cachedIn(viewModelScope)

    init {
        loadPopularSuggestions()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: TitleType?) {
        _selectedFilter.value = filter
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
        _searchQuery.value = query
        _selectedFilter.value = filter
    }
}
