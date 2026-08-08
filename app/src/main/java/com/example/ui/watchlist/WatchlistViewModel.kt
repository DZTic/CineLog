package com.example.ui.watchlist

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.ui.CachedSaga
import com.example.ui.CollectionViewMode
import com.example.ui.WatchlistSortOrder
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

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
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

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
        _watchlistTypeFilter, _watchlistGenreFilter, _watchlistYearFilter
    ) { type, genre, year ->
        type != null || genre != null || year != null
    }

    fun clearWatchlistFilters() {
        setWatchlistTypeFilter(null)
        setWatchlistGenreFilter(null)
        setWatchlistYearFilter(null)
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
