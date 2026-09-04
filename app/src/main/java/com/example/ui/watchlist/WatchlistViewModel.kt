package com.example.ui.watchlist

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.ui.CachedSaga
import com.example.ui.CollectionViewMode
import com.example.ui.WatchlistSortOrder
import com.example.ui.components.GroupedDisplay
import com.example.ui.components.groupBySaga
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

@Immutable
data class WatchlistUiState(
    val isLoading: Boolean = true,
    val totalUnwatchedCount: Int = 0,
    val filteredCount: Int = 0,
    val availableGenres: List<String> = emptyList(),
    val availableYears: List<String> = emptyList(),
    val categoryCounts: Map<TitleType, Int> = emptyMap(),
    val displayItemsByType: Map<TitleType, List<GroupedDisplay<DbWatchlist>>> = emptyMap(),
    val unwatchedEntries: List<DbWatchlist> = emptyList()
) {
    val isWatchlistEmpty: Boolean get() = !isLoading && totalUnwatchedCount == 0
    val isFilteredEmpty: Boolean get() = !isLoading && totalUnwatchedCount > 0 && filteredCount == 0
}

private data class FilterSortParams(
    val query: String,
    val sort: WatchlistSortOrder,
    val typeFilter: TitleType?,
    val genreFilter: String?,
    val yearFilter: String?
)

class WatchlistViewModel(
    private val repository: Repository,
    private val preferenceManager: PreferenceManager
) : ViewModel() {
    private val tag = "WatchlistViewModel"

    val allWatchlist: StateFlow<List<DbWatchlist>> = repository.allWatchlist
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allLogs: StateFlow<List<DbLogEntry>> = repository.allLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val collectionCache: StateFlow<Map<String, CachedSaga>> = repository.collectionCache
        .map { list -> list.associate { it.titleId to CachedSaga(it.collectionId, it.collectionName, it.collectionPosterUrl) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _watchlistViewMode = MutableStateFlow(
        runCatching { CollectionViewMode.valueOf(preferenceManager.getWatchlistViewMode()) }
            .getOrDefault(CollectionViewMode.GRID)
    )
    val watchlistViewMode: StateFlow<CollectionViewMode> = _watchlistViewMode.asStateFlow()

    fun setWatchlistViewMode(mode: CollectionViewMode) {
        _watchlistViewMode.value = mode
        preferenceManager.setWatchlistViewMode(mode.name)
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
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

    private val _watchlistSort = MutableStateFlow(
        runCatching { WatchlistSortOrder.valueOf(preferenceManager.getWatchlistSort()) }
            .getOrDefault(WatchlistSortOrder.DATE_ADDED)
    )
    val watchlistSort: StateFlow<WatchlistSortOrder> = _watchlistSort.asStateFlow()

    fun setWatchlistSort(sort: WatchlistSortOrder) {
        _watchlistSort.value = sort
        preferenceManager.setWatchlistSort(sort.name)
    }

    private val _watchlistTypeFilter = MutableStateFlow(
        preferenceManager.getWatchlistTypeFilter()
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { TitleType.valueOf(it) }.getOrNull() }
    )
    val watchlistTypeFilter: StateFlow<TitleType?> = _watchlistTypeFilter.asStateFlow()

    fun setWatchlistTypeFilter(type: TitleType?) {
        _watchlistTypeFilter.value = type
        preferenceManager.setWatchlistTypeFilter(type?.name ?: "")
    }

    private val _watchlistGenreFilter = MutableStateFlow(
        preferenceManager.getWatchlistGenreFilter().takeIf { it.isNotBlank() }
    )
    val watchlistGenreFilter: StateFlow<String?> = _watchlistGenreFilter.asStateFlow()

    fun setWatchlistGenreFilter(genre: String?) {
        _watchlistGenreFilter.value = genre
        preferenceManager.setWatchlistGenreFilter(genre ?: "")
    }

    private val _watchlistYearFilter = MutableStateFlow(
        preferenceManager.getWatchlistYearFilter().takeIf { it.isNotBlank() }
    )
    val watchlistYearFilter: StateFlow<String?> = _watchlistYearFilter.asStateFlow()

    fun setWatchlistYearFilter(year: String?) {
        _watchlistYearFilter.value = year
        preferenceManager.setWatchlistYearFilter(year ?: "")
    }

    val watchlistHasActiveFilters: Flow<Boolean> = combine(
        _watchlistTypeFilter, _watchlistGenreFilter, _watchlistYearFilter, _searchQuery
    ) { type, genre, year, search ->
        type != null || genre != null || year != null || search.isNotBlank()
    }

    fun clearWatchlistFilters() {
        setSearchQuery("")
        setWatchlistTypeFilter(null)
        setWatchlistGenreFilter(null)
        setWatchlistYearFilter(null)
    }

    private val filterSortParams: Flow<FilterSortParams> = combine(
        _searchQuery,
        _watchlistSort,
        _watchlistTypeFilter,
        _watchlistGenreFilter,
        _watchlistYearFilter
    ) { query, sort, type, genre, year ->
        FilterSortParams(query, sort, type, genre, year)
    }

    val uiState: StateFlow<WatchlistUiState> = combine(
        allWatchlist,
        allLogs,
        collectionCache,
        filterSortParams
    ) { rawWatchlist, logs, cache, params ->
        computeWatchlistUiState(rawWatchlist, logs, cache, params)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = WatchlistUiState(isLoading = true)
        )

    private fun computeWatchlistUiState(
        rawWatchlist: List<DbWatchlist>,
        logs: List<DbLogEntry>,
        cache: Map<String, CachedSaga>,
        params: FilterSortParams
    ): WatchlistUiState {
        val backfilledWatchlist = rawWatchlist.map { entry ->
            if (entry.collectionId == null) {
                cache[entry.titleId]?.let { cached ->
                    entry.copy(
                        collectionId = cached.collectionId,
                        collectionName = cached.collectionName,
                        collectionPosterUrl = cached.posterUrl
                    )
                } ?: entry
            } else {
                entry
            }
        }

        val watchedTitleIds = logs.map { it.titleId }.toSet()
        val watchedFiltered = backfilledWatchlist.filter { it.titleId !in watchedTitleIds }

        val typeScopedEntries = watchedFiltered.filter { entry ->
            params.typeFilter == null ||
                runCatching { TitleType.valueOf(entry.titleType) }.getOrNull() == params.typeFilter
        }
        val availableGenres = typeScopedEntries
            .flatMap { it.titleGenres?.split(",")?.map(String::trim) ?: emptyList() }
            .filter(String::isNotBlank)
            .distinct()
            .sorted()

        val availableYears = typeScopedEntries
            .mapNotNull { it.titleYear?.takeIf(String::isNotBlank) }
            .distinct()
            .sortedDescending()

        val trimmedQuery = params.query.trim()
        val hasQuery = trimmedQuery.isNotBlank()

        val filteredWatchlist = watchedFiltered.filter { entry ->
            val matchesType = params.typeFilter == null ||
                runCatching { TitleType.valueOf(entry.titleType) }.getOrNull() == params.typeFilter
            val matchesGenre = params.genreFilter == null ||
                entry.titleGenres?.split(",")?.any { it.trim() == params.genreFilter } == true
            val matchesYear = params.yearFilter == null || entry.titleYear == params.yearFilter
            val matchesQuery = !hasQuery ||
                entry.titleName.contains(trimmedQuery, ignoreCase = true) ||
                entry.collectionName?.contains(trimmedQuery, ignoreCase = true) == true
            matchesType && matchesGenre && matchesYear && matchesQuery
        }

        val groupedWatchlist = filteredWatchlist.groupBy {
            try {
                TitleType.valueOf(it.titleType)
            } catch (e: Exception) {
                TitleType.FILM
            }
        }
        val categoryCounts = groupedWatchlist.mapValues { (_, items) -> items.size }

        val displayItemsByType = groupedWatchlist.mapValues { (_, items) ->
            val grouped = items.groupBySaga(
                collectionId = { it.collectionId },
                collectionName = { it.collectionName },
                posterUrl = { it.collectionPosterUrl }
            )
            when (params.sort) {
                WatchlistSortOrder.DATE_ADDED ->
                    grouped.sortedByDescending { display ->
                        when (display) {
                            is GroupedDisplay.Single -> display.item.dateAdded
                            is GroupedDisplay.Grouped -> display.group.items.maxOf { it.dateAdded }
                        }
                    }
                WatchlistSortOrder.TITLE_AZ ->
                    grouped.sortedBy { display ->
                        when (display) {
                            is GroupedDisplay.Single -> display.item.titleName.lowercase()
                            is GroupedDisplay.Grouped -> display.group.collectionName.lowercase()
                        }
                    }
                WatchlistSortOrder.RELEASE_YEAR ->
                    grouped.sortedByDescending { display ->
                        when (display) {
                            is GroupedDisplay.Single -> display.item.titleYear?.toIntOrNull() ?: Int.MIN_VALUE
                            is GroupedDisplay.Grouped ->
                                display.group.items.mapNotNull { it.titleYear?.toIntOrNull() }.maxOrNull() ?: Int.MIN_VALUE
                        }
                    }
                WatchlistSortOrder.COMMUNITY_RATING ->
                    grouped.sortedByDescending { display ->
                        when (display) {
                            is GroupedDisplay.Single -> display.item.titleVoteAverage ?: Float.MIN_VALUE
                            is GroupedDisplay.Grouped ->
                                display.group.items.mapNotNull { it.titleVoteAverage }.maxOrNull() ?: Float.MIN_VALUE
                        }
                    }
            }
        }

        return WatchlistUiState(
            isLoading = false,
            totalUnwatchedCount = watchedFiltered.size,
            filteredCount = filteredWatchlist.size,
            availableGenres = availableGenres,
            availableYears = availableYears,
            categoryCounts = categoryCounts,
            displayItemsByType = displayItemsByType,
            unwatchedEntries = watchedFiltered
        )
    }

    private val attemptedBackfills = ConcurrentHashMap.newKeySet<String>()

    fun backfillWatchlistMetadata(entries: List<DbWatchlist>) {
        val missing = entries.filter { (it.titleYear == null || it.titleYear == "N/A") && attemptedBackfills.add(it.titleId) }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            missing.forEach { entry ->
                try {
                    repository.backfillWatchlistMetadata(entry.titleId)
                } catch (e: Exception) {
                    Log.e(tag, "Error backfilling ${entry.titleId} metadata: ${e.localizedMessage}")
                }
            }
        }
    }

    fun removeFromWatchlist(titleId: String) {
        viewModelScope.launch {
            try {
                repository.removeFromWatchlist(titleId)
            } catch (e: Exception) {
                Log.e(tag, "Error removing from watchlist: ${e.localizedMessage}")
            }
        }
    }

    private val _watchlistRefreshing = MutableStateFlow(false)
    val watchlistRefreshing: StateFlow<Boolean> = _watchlistRefreshing.asStateFlow()

    fun refreshWatchlist() {
        viewModelScope.launch {
            _watchlistRefreshing.value = true
            try {
                delay(600)
            } finally {
                _watchlistRefreshing.value = false
            }
        }
    }
}
