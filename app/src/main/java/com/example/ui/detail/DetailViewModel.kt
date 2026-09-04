package com.example.ui.detail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DetailViewModel(
    private val repository: Repository,
    private val preferenceManager: PreferenceManager? = null
) : ViewModel() {
    private val tag = "DetailViewModel"

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

    private val _currentSeasonProgress = MutableStateFlow<Map<Int, SeasonStatus>>(emptyMap())
    val currentSeasonProgress: StateFlow<Map<Int, SeasonStatus>> = _currentSeasonProgress.asStateFlow()

    val allWatchlist: StateFlow<List<DbWatchlist>> = repository.allWatchlist
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCustomLists: StateFlow<List<DbCustomList>> = repository.allCustomLists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var logsJob: Job? = null
    private var seasonProgressJob: Job? = null
    private var collectionJob: Job? = null

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
                val detail = repository.getTitleDetail(titleId)
                _currentTitle.value = detail

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

    fun toggleWatchlist(
        titleId: String,
        type: TitleType,
        name: String,
        posterUrl: String?,
        collectionId: Int? = null,
        collectionName: String? = null,
        collectionPosterUrl: String? = null,
        year: String? = null,
        genres: List<String> = emptyList(),
        voteAverage: Float = 0f
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
                            titleYear = year?.takeIf { it.isNotBlank() && it != "N/A" },
                            titleGenres = genres.takeIf { it.isNotEmpty() }?.joinToString(","),
                            titleVoteAverage = voteAverage.takeIf { it > 0f },
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

    fun addAllToWatchlist(titles: List<CineTitle>) {
        viewModelScope.launch {
            try {
                repository.addAllToWatchlist(titles)
            } catch (e: Exception) {
                Log.e(tag, "Error adding titles to watchlist: ${e.localizedMessage}")
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

    fun deleteLog(id: Int) {
        viewModelScope.launch {
            try {
                repository.deleteLogById(id)
            } catch (e: Exception) {
                Log.e(tag, "Error deleting log: ${e.localizedMessage}")
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
}
