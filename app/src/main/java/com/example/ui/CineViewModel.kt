package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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

    // Local cache of movies whose saga is already known (visited at least
    // once, or fetched as part of a saga listing). Lets Search group a
    // franchise together without extra network calls (TMDB's search
    // endpoint doesn't return saga info, only the detail/collection
    // endpoints do). Uses Eagerly (not WhileSubscribed) because it's read
    // via `.value` from performSearch rather than observed with
    // collectAsState, so it must stay live even with no UI subscriber.
    data class CachedSaga(val collectionId: Int, val collectionName: String, val posterUrl: String?)

    val collectionCache: StateFlow<Map<String, CachedSaga>> = repository.collectionCache
        .map { list -> list.associate { it.titleId to CachedSaga(it.collectionId, it.collectionName, it.collectionPosterUrl) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    // Local cache of collectionId -> total number of films in that saga.
    // Powers the "vue en entier" badge on grouped saga cards (Accueil,
    // Watchlist, Recherche) without a network round-trip on every render.
    val sagaSizeCache: StateFlow<Map<Int, Int>> = repository.sagaSizeCache
        .map { list -> list.associate { it.collectionId to it.totalFilms } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    // Triggers a fetch of a saga's total film count if it isn't cached
    // locally yet. Safe to call once per visible saga card: the repository
    // skips the network call entirely when the value is already cached.
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

    private val _collectionTitles = MutableStateFlow<List<CineTitle>>(emptyList())
    val collectionTitles: StateFlow<List<CineTitle>> = _collectionTitles.asStateFlow()

    // ==========================================
    // SAGA DETAIL STATE (dedicated screen for a whole TMDB collection)
    // ==========================================

    private val _sagaLoading = MutableStateFlow(false)
    val sagaLoading: StateFlow<Boolean> = _sagaLoading.asStateFlow()

    private val _sagaError = MutableStateFlow<String?>(null)
    val sagaError: StateFlow<String?> = _sagaError.asStateFlow()

    private val _sagaInfo = MutableStateFlow<Repository.SagaInfo?>(null)
    val sagaInfo: StateFlow<Repository.SagaInfo?> = _sagaInfo.asStateFlow()

    private val _sagaTitles = MutableStateFlow<List<CineTitle>>(emptyList())
    val sagaTitles: StateFlow<List<CineTitle>> = _sagaTitles.asStateFlow()

    fun loadSagaDetail(collectionId: Int) {
        viewModelScope.launch {
            _sagaLoading.value = true
            _sagaError.value = null
            _sagaInfo.value = null
            _sagaTitles.value = emptyList()
            try {
                val result = repository.getSagaDetail(collectionId)
                if (result != null) {
                    _sagaInfo.value = result.first
                    _sagaTitles.value = result.second
                } else {
                    _sagaError.value = "Impossible de charger cette saga."
                }
            } catch (e: Exception) {
                Log.e(tag, "Error loading saga detail: ${e.localizedMessage}")
                _sagaError.value = e.localizedMessage ?: "Erreur de chargement de la saga."
            } finally {
                _sagaLoading.value = false
            }
        }
    }

    // Maps season number -> status, for the title currently on screen.
    private val _currentSeasonProgress = MutableStateFlow<Map<Int, SeasonStatus>>(emptyMap())
    val currentSeasonProgress: StateFlow<Map<Int, SeasonStatus>> = _currentSeasonProgress.asStateFlow()

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

    // Tracks the background collectors started by loadTitleDetail() so a
    // previous title's collector can be cancelled before starting a new
    // one. Without this, navigating between titles piles up collectors
    // that never stop, and an old one can overwrite the currently
    // displayed title's state with another title's data whenever the
    // underlying table changes (stale/wrong "déjà vu" badge, wrong
    // rating, etc.).
    private var logsJob: Job? = null
    private var seasonProgressJob: Job? = null
    private var collectionJob: Job? = null

    /**
     * Fetch Details of movie, show, or anime
     */
    fun loadTitleDetail(titleId: String) {
        logsJob?.cancel()
        seasonProgressJob?.cancel()
        collectionJob?.cancel()

        viewModelScope.launch {
            _detailLoading.value = true
            _detailError.value = null
            _currentTitle.value = null
            _collectionTitles.value = emptyList()
            _currentTitleLogs.value = emptyList()
            _currentSeasonProgress.value = emptyMap()
            try {
                // Read details from API
                val detail = repository.getTitleDetail(titleId)
                _currentTitle.value = detail

                // Read local journal logs for this title in a separate coroutine to avoid blocking
                logsJob = viewModelScope.launch {
                    repository.getLogsForTitle(titleId).collect { logs ->
                        _currentTitleLogs.value = logs
                    }
                }

                if (detail.seasons.isNotEmpty()) {
                    seasonProgressJob = viewModelScope.launch {
                        repository.getSeasonProgressForTitle(titleId).collect { progress ->
                            _currentSeasonProgress.value = progress.associate { entry ->
                                entry.seasonNumber to (
                                    try {
                                        SeasonStatus.valueOf(entry.status)
                                    } catch (e: Exception) {
                                        SeasonStatus.NOT_WATCHED
                                    }
                                )
                            }
                        }
                    }
                }

                // If this movie belongs to a saga, fetch the rest of it separately
                // so a slow/failed collection lookup never blocks the main detail.
                val collectionId = detail.collectionId
                if (collectionId != null) {
                    collectionJob = viewModelScope.launch {
                        _collectionTitles.value = repository.getCollectionTitles(
                            collectionId,
                            excludeTitleId = titleId
                        )
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
        spoiler: Boolean,
        collectionId: Int? = null,
        collectionName: String? = null,
        collectionPosterUrl: String? = null
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
                    spoiler = spoiler,
                    collectionId = collectionId,
                    collectionName = collectionName,
                    collectionPosterUrl = collectionPosterUrl
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

    fun setSeasonStatus(titleId: String, seasonNumber: Int, status: SeasonStatus) {
        viewModelScope.launch {
            try {
                repository.setSeasonStatus(titleId, seasonNumber, status)
            } catch (e: Exception) {
                Log.e(tag, "Error updating season $seasonNumber status: ${e.localizedMessage}")
            }
        }
    }

    // ==========================================
    // WATCHLIST MANAGEMENT
    // ==========================================

    fun isTitleInWatchlist(titleId: String): Flow<Boolean> = repository.isInWatchlist(titleId)

    // Adds every given title to the watchlist that isn't already in it or
    // already logged as watched (unlike toggleWatchlist, this never
    // removes anything — used by the "Add saga to watchlist" action so
    // repeated taps stay safe).
    fun addAllToWatchlist(titles: List<CineTitle>) {
        viewModelScope.launch {
            titles.forEach { title ->
                try {
                    val alreadyIn = repository.isInWatchlist(title.id).first()
                    if (!alreadyIn) {
                        repository.addToWatchlist(
                            DbWatchlist(
                                titleId = title.id,
                                titleType = title.type.name,
                                titleName = title.title,
                                titlePosterUrl = title.posterUrl,
                                collectionId = title.collectionId,
                                collectionName = title.collectionName,
                                collectionPosterUrl = title.collectionPosterUrl
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error adding ${title.title} to watchlist: ${e.localizedMessage}")
                }
            }
        }
    }

    fun toggleWatchlist(
        titleId: String,
        type: TitleType,
        name: String,
        posterUrl: String?,
        collectionId: Int? = null,
        collectionName: String? = null,
        collectionPosterUrl: String? = null
    ) {
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
                            titlePosterUrl = posterUrl,
                            collectionId = collectionId,
                            collectionName = collectionName,
                            collectionPosterUrl = collectionPosterUrl
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

    // Returns the Flow directly rather than manually collecting it into a
    // freshly-created MutableStateFlow: these getters are called straight
    // from Composable bodies (ListsScreen), so a manual
    // `viewModelScope.launch { flow.collect { ... } } ` here would start a
    // brand new, never-cancelled collector on every recomposition and pile
    // up leaked coroutines over a session. collectAsState() on the Composable
    // side already manages subscription/cancellation safely tied to
    // composition, so this is the correct place to let it do that.
    fun getCustomListDetail(listId: Int): Flow<DbCustomList?> = repository.getCustomListById(listId)

    fun getCustomListTitlesFlow(listId: Int): Flow<List<DbCustomListTitle>> = repository.getCustomListTitles(listId)

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

    // ==========================================
    // HOME SCREEN DISPLAY PREFERENCES
    // ==========================================

    // Persistée via PreferenceManager pour survivre à la fermeture de
    // l'appli : l'utilisateur ne veut pas re-choisir "grille" à chaque
    // ouverture.
    private val _homeViewMode = MutableStateFlow(
        runCatching { CollectionViewMode.valueOf(preferenceManager.getHomeViewMode()) }
            .getOrDefault(CollectionViewMode.LIST)
    )
    val homeViewMode: StateFlow<CollectionViewMode> = _homeViewMode.asStateFlow()

    fun setHomeViewMode(mode: CollectionViewMode) {
        _homeViewMode.value = mode
        preferenceManager.setHomeViewMode(mode.name)
    }

    // Catégories (Films / Séries / Animes) actuellement réduites sur
    // l'accueil, pour laisser de la place aux autres quand la liste d'une
    // catégorie est longue. Clé = TitleType.name.
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

    // ==========================================
    // WATCHLIST SCREEN DISPLAY PREFERENCES
    // ==========================================
    // Même principe que pour l'accueil, mais stocké séparément : rien
    // n'oblige l'utilisateur à vouloir le même mode d'affichage ou les
    // mêmes catégories réduites sur les deux écrans.

    private val _watchlistViewMode = MutableStateFlow(
        runCatching { CollectionViewMode.valueOf(preferenceManager.getWatchlistViewMode()) }
            .getOrDefault(CollectionViewMode.GRID)
    )
    val watchlistViewMode: StateFlow<CollectionViewMode> = _watchlistViewMode.asStateFlow()

    fun setWatchlistViewMode(mode: CollectionViewMode) {
        _watchlistViewMode.value = mode
        preferenceManager.setWatchlistViewMode(mode.name)
    }

    private val _watchlistCollapsedCategories = MutableStateFlow(
        preferenceManager.getWatchlistCollapsedCategories()
    )
    val watchlistCollapsedCategories: StateFlow<Set<String>> = _watchlistCollapsedCategories.asStateFlow()

    fun toggleWatchlistCategoryCollapsed(categoryKey: String) {
        val updated = _watchlistCollapsedCategories.value.toMutableSet().apply {
            if (!add(categoryKey)) remove(categoryKey)
        }
        _watchlistCollapsedCategories.value = updated
        preferenceManager.setWatchlistCollapsedCategories(updated)
    }
}

// Partagé entre l'Accueil et la Watchlist (et potentiellement d'autres
// écrans à l'avenir) : une simple préférence Liste/Grille n'a pas besoin
// d'être dupliquée par écran.
enum class CollectionViewMode { LIST, GRID }

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
