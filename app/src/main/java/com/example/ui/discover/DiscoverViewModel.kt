package com.example.ui.discover

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
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

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow<TitleType?>(null)
    val selectedFilter: StateFlow<TitleType?> = _selectedFilter.asStateFlow()

    val allWatchlist: StateFlow<List<DbWatchlist>> = repository.allWatchlist
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allLogs: StateFlow<List<DbLogEntry>> = repository.allLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    val discoverPagingFlow: Flow<PagingData<CineTitle>> = _selectedFilter
        .flatMapLatest { filter ->
            if (filter == null) {
                flowOf(PagingData.empty())
            } else {
                Pager(
                    config = PagingConfig(pageSize = 20, enablePlaceholders = false),
                    pagingSourceFactory = { DiscoverPagingSource(repository, filter) }
                ).flow
            }
        }
        .cachedIn(viewModelScope)

    init {
        loadDiscoverContent()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: TitleType?) {
        _selectedFilter.value = filter
    }

    fun loadDiscoverContent() {
        viewModelScope.launch {
            _discoverLoading.value = true
            _discoverError.value = null
            try {
                coroutineScope {
                    val filmsDeferred = async(Dispatchers.IO) { repository.getTrendingOrPopular(TitleType.FILM) }
                    val seriesDeferred = async(Dispatchers.IO) { repository.getTrendingOrPopular(TitleType.SERIE) }
                    val animeDeferred = async(Dispatchers.IO) { repository.getTrendingOrPopular(TitleType.ANIME) }
                    _trendingFilms.value = filmsDeferred.await()
                    _trendingSeries.value = seriesDeferred.await()
                    _topAnime.value = animeDeferred.await()
                }
            } catch (e: Exception) {
                Log.e(tag, "Error loading discover content: ${e.localizedMessage}")
                _discoverError.value = "Impossible de récupérer tout le contenu. Veuillez vérifier votre clé TMDB."
            } finally {
                _discoverLoading.value = false
            }
        }
    }

    fun performSearch(query: String, filter: TitleType? = null) {
        _searchQuery.value = query
        _selectedFilter.value = filter
    }
}
