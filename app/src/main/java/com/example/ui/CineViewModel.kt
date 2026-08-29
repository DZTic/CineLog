package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.*
import com.example.ui.detail.DetailViewModel
import com.example.ui.discover.DiscoverViewModel
import com.example.ui.home.HomeViewModel
import com.example.ui.lists.ListsViewModel
import com.example.ui.log.LogViewModel
import com.example.ui.profile.ProfileViewModel
import com.example.ui.saga.SagaDetailViewModel
import com.example.ui.search.SearchViewModel
import com.example.ui.settings.SettingsViewModel
import com.example.ui.watchlist.WatchlistViewModel
import com.example.util.NetworkMonitor
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class CachedSaga(val collectionId: Int, val collectionName: String, val posterUrl: String?)

enum class CollectionViewMode { LIST, GRID }

enum class WatchlistSortOrder {
    DATE_ADDED, TITLE_AZ, RELEASE_YEAR, COMMUNITY_RATING;

    val displayNameRes: Int
        get() = when (this) {
            DATE_ADDED -> R.string.watchlist_sort_date_added
            TITLE_AZ -> R.string.watchlist_sort_title_az
            RELEASE_YEAR -> R.string.watchlist_sort_release_year
            COMMUNITY_RATING -> R.string.watchlist_sort_community_rating
        }
}

open class SharedViewModel(
    private val repository: Repository
) : ViewModel() {
    private val tag = "SharedViewModel"

    val collectionCache: StateFlow<Map<String, CachedSaga>> = repository.collectionCache
        .map { list -> list.associate { it.titleId to CachedSaga(it.collectionId, it.collectionName, it.collectionPosterUrl) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val sagaSizeCache: StateFlow<Map<Int, Int>> = repository.sagaSizeCache
        .map { list -> list.associate { it.collectionId to it.totalFilms } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

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

class CineViewModel(
    application: Application? = null,
    private val repository: Repository,
    private val preferenceManager: PreferenceManager? = null
) : SharedViewModel(repository)

class CineViewModelFactory(
    private val application: Application? = null,
    private val repository: Repository,
    private val preferenceManager: PreferenceManager,
    private val networkMonitor: NetworkMonitor? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> HomeViewModel(repository, preferenceManager, networkMonitor) as T
            modelClass.isAssignableFrom(DiscoverViewModel::class.java) -> DiscoverViewModel(repository, networkMonitor) as T
            modelClass.isAssignableFrom(SearchViewModel::class.java) -> SearchViewModel(repository, preferenceManager) as T
            modelClass.isAssignableFrom(DetailViewModel::class.java) -> DetailViewModel(repository, preferenceManager) as T
            modelClass.isAssignableFrom(WatchlistViewModel::class.java) -> WatchlistViewModel(repository, preferenceManager) as T
            modelClass.isAssignableFrom(ProfileViewModel::class.java) -> ProfileViewModel(repository) as T
            modelClass.isAssignableFrom(ListsViewModel::class.java) -> ListsViewModel(repository) as T
            modelClass.isAssignableFrom(SagaDetailViewModel::class.java) -> SagaDetailViewModel(repository) as T
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(preferenceManager, repository) as T
            modelClass.isAssignableFrom(LogViewModel::class.java) -> LogViewModel(repository) as T
            modelClass.isAssignableFrom(SharedViewModel::class.java) -> SharedViewModel(repository) as T
            modelClass.isAssignableFrom(CineViewModel::class.java) -> CineViewModel(application, repository, preferenceManager) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class ${modelClass.name}")
        }
    }
}
