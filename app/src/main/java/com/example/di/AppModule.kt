package com.example.di

import com.example.data.*
import com.example.data.repository.BackupRepository
import com.example.data.repository.CustomListRepository
import com.example.data.repository.LogRepository
import com.example.data.repository.WatchlistRepository
import com.example.ui.CineViewModel
import com.example.ui.SharedViewModel
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
import com.example.util.ConnectivityNetworkMonitor
import com.example.util.NetworkMonitor
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val databaseModule = module {
    single { AppDatabase.getDatabase(androidContext()) }
    single { get<AppDatabase>().logDao() }
    single { get<AppDatabase>().watchlistDao() }
    single { get<AppDatabase>().customListDao() }
    single { get<AppDatabase>().seasonProgressDao() }
    single { get<AppDatabase>().collectionCacheDao() }
    single { get<AppDatabase>().sagaSizeDao() }
    single { get<AppDatabase>().titleMetaCacheDao() }
    single { PreferenceManager(androidContext()) }
    single<NetworkMonitor> { ConnectivityNetworkMonitor(androidContext()) }
}

val repositoryModule = module {
    single { LogRepository(logDao = get()) }
    single { WatchlistRepository(watchlistDao = get()) }
    single { CustomListRepository(customListDao = get()) }
    single {
        BackupRepository(
            logDao = get(),
            watchlistDao = get(),
            customListDao = get(),
            seasonProgressDao = get()
        )
    }
    single {
        Repository(
            logDao = get(),
            watchlistDao = get(),
            customListDao = get(),
            seasonProgressDao = get(),
            collectionCacheDao = get(),
            sagaSizeDao = get(),
            titleMetaCacheDao = get(),
            preferenceManager = get(),
            context = androidContext()
        )
    }
}

val viewModelModule = module {
    viewModel { HomeViewModel(repository = get(), preferenceManager = get(), networkMonitor = get()) }
    viewModel { DiscoverViewModel(repository = get(), networkMonitor = get()) }
    viewModel { SearchViewModel(repository = get(), preferenceManager = get()) }
    viewModel { WatchlistViewModel(repository = get(), preferenceManager = get()) }
    viewModel { ListsViewModel(repository = get()) }
    viewModel { ProfileViewModel(repository = get()) }
    viewModel { SettingsViewModel(preferenceManager = get(), repository = get()) }
    viewModel { DetailViewModel(repository = get(), preferenceManager = get()) }
    viewModel { SagaDetailViewModel(repository = get()) }
    viewModel { LogViewModel(repository = get()) }
    viewModel { SharedViewModel(repository = get()) }
    viewModel { CineViewModel(application = null, repository = get(), preferenceManager = get()) }
}

val appModules = listOf(databaseModule, repositoryModule, viewModelModule)
