package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.navigation.ScreenDestination
import com.example.data.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.CineViewModelFactory
import com.example.ui.home.HomeViewModel
import com.example.ui.discover.DiscoverViewModel
import com.example.ui.search.SearchViewModel
import com.example.ui.watchlist.WatchlistViewModel
import com.example.ui.lists.ListsViewModel
import com.example.ui.profile.ProfileViewModel
import com.example.ui.settings.SettingsViewModel
import com.example.ui.detail.DetailViewModel
import com.example.ui.saga.SagaDetailViewModel
import com.example.ui.log.LogViewModel
import com.example.ui.detail.DetailScreen
import com.example.ui.discover.DiscoverScreen
import com.example.ui.home.HomeScreen
import com.example.ui.lists.ListsScreen
import com.example.ui.log.LogBottomSheet
import com.example.ui.profile.ProfileScreen
import com.example.ui.saga.SagaDetailScreen
import com.example.ui.search.SearchScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.watchlist.WatchlistScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Initialize Singletons / Databases
        val database = AppDatabase.getDatabase(this)
        val preferenceManager = PreferenceManager(this)
        val repository = Repository(
            logDao = database.logDao(),
            watchlistDao = database.watchlistDao(),
            customListDao = database.customListDao(),
            seasonProgressDao = database.seasonProgressDao(),
            collectionCacheDao = database.collectionCacheDao(),
            sagaSizeDao = database.sagaSizeDao(),
            titleMetaCacheDao = database.titleMetaCacheDao(),
            preferenceManager = preferenceManager,
            context = applicationContext
        )

        // 2. Instantiate master view model
        val viewModelFactory = CineViewModelFactory(application, repository, preferenceManager)

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel(factory = viewModelFactory)
            val themeMode by settingsViewModel.themeMode.collectAsState()
            val dynamicColor by settingsViewModel.dynamicColor.collectAsState()

            MyApplicationTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                MainAppScaffold(viewModelFactory)
            }
        }
    }
}

@Composable
fun CineBottomNavigationBar(
    navController: NavHostController,
    bottomNavItems: List<ScreenDestination>
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val destination = navBackStackEntry?.destination

    val isPrimaryTab = bottomNavItems.any { screen -> destination?.hasRoute(screen::class) == true }
    if (isPrimaryTab) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(72.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                bottomNavItems.forEach { screen ->
                    val selected = destination?.hasRoute(screen::class) == true
                    val icon = when (screen) {
                        ScreenDestination.Home -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
                        ScreenDestination.Discover -> if (selected) Icons.Filled.Explore else Icons.Outlined.Explore
                        ScreenDestination.Watchlist -> if (selected) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder
                        ScreenDestination.Profile -> if (selected) Icons.Filled.Person else Icons.Outlined.Person
                        else -> Icons.Filled.Home
                    }

                    val label = when (screen) {
                        ScreenDestination.Home -> "Accueil"
                        ScreenDestination.Discover -> "Découvrir"
                        ScreenDestination.Watchlist -> "À voir"
                        ScreenDestination.Profile -> "Profil"
                        else -> ""
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (!selected) {
                                    navController.navigate(screen) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.secondaryContainer
                                    else Color.Transparent
                                )
                                .padding(horizontal = 14.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (selected) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        if (selected) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(viewModelFactory: CineViewModelFactory) {
    val navController = rememberNavController()


    // Logging sheet dialog trigger state
    var loggingTitle by remember { mutableStateOf<CineTitle?>(null) }
    // When non-null, the Log dialog opens pre-filled to edit this existing entry instead of creating a new one
    var editingLog by remember { mutableStateOf<DbLogEntry?>(null) }

    // Core 4 Tab Routes for Bottom Navigation (Material Design 3-5 tabs guideline)
    val bottomNavItems = listOf(
        ScreenDestination.Home,
        ScreenDestination.Discover,
        ScreenDestination.Watchlist,
        ScreenDestination.Profile
    )

    Scaffold(
        bottomBar = {
            CineBottomNavigationBar(
                navController = navController,
                bottomNavItems = bottomNavItems
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ScreenDestination.Home,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
            enterTransition = {
                fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing))
            },
            exitTransition = {
                fadeOut(animationSpec = tween(220, easing = FastOutSlowInEasing))
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing))
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(220, easing = FastOutSlowInEasing))
            }
        ) {
            // Home View
            composable<ScreenDestination.Home> {
                val homeViewModel: HomeViewModel = viewModel(factory = viewModelFactory)
                HomeScreen(
                    viewModel = homeViewModel,
                    onTitleClick = { titleId ->
                        navController.navigate(ScreenDestination.Detail(titleId))
                    },
                    onSagaClick = { collectionId ->
                        navController.navigate(ScreenDestination.SagaDetail(collectionId))
                    },
                    onNavigateToDiscover = {
                        navController.navigate(ScreenDestination.Discover)
                    },
                    onNavigateToSettings = {
                        navController.navigate(ScreenDestination.Settings)
                    }
                )
            }

            // Discover Carousel / Grids & Embedded Search View
            composable<ScreenDestination.Discover> {
                val discoverViewModel: DiscoverViewModel = viewModel(factory = viewModelFactory)
                DiscoverScreen(
                    viewModel = discoverViewModel,
                    onTitleClick = { titleId ->
                        navController.navigate(ScreenDestination.Detail(titleId))
                    },
                    onSagaClick = { collectionId ->
                        navController.navigate(ScreenDestination.SagaDetail(collectionId))
                    }
                )
            }

            // Global Search View (Direct route)
            composable<ScreenDestination.Search> {
                val searchViewModel: SearchViewModel = viewModel(factory = viewModelFactory)
                val logViewModel: LogViewModel = viewModel(factory = viewModelFactory)
                SearchScreen(
                    viewModel = searchViewModel,
                    logViewModel = logViewModel,
                    onTitleClick = { titleId ->
                        navController.navigate(ScreenDestination.Detail(titleId))
                    },
                    onSagaClick = { collectionId ->
                        navController.navigate(ScreenDestination.SagaDetail(collectionId))
                    },
                    onNavigateToSettings = {
                        navController.navigate(ScreenDestination.Settings)
                    }
                )
            }

            // Watchlist View
            composable<ScreenDestination.Watchlist> {
                val watchlistViewModel: WatchlistViewModel = viewModel(factory = viewModelFactory)
                WatchlistScreen(
                    viewModel = watchlistViewModel,
                    onTitleClick = { titleId ->
                        navController.navigate(ScreenDestination.Detail(titleId))
                    },
                    onSagaClick = { collectionId ->
                        navController.navigate(ScreenDestination.SagaDetail(collectionId))
                    }
                )
            }

            // Custom user Lists View
            composable<ScreenDestination.Lists> {
                val listsViewModel: ListsViewModel = viewModel(factory = viewModelFactory)
                ListsScreen(
                    viewModel = listsViewModel,
                    onTitleClick = { titleId ->
                        navController.navigate(ScreenDestination.Detail(titleId))
                    },
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }

            // Profile Screen with Settings trigger & List shortcut
            composable<ScreenDestination.Profile> {
                Scaffold(
                    contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                    topBar = {
                        TopAppBar(
                            title = { Text("Mon Profil CinéLog") },
                            actions = {
                                IconButton(onClick = { navController.navigate(ScreenDestination.Settings) }) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Paramètres de la clé API",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background
                            )
                        )
                    }
                ) { padding ->
                    val profileViewModel: ProfileViewModel = viewModel(factory = viewModelFactory)
                    ProfileScreen(
                        viewModel = profileViewModel,
                        onNavigateToLists = {
                            navController.navigate(ScreenDestination.Lists)
                        },
                        modifier = Modifier.padding(padding)
                    )
                }
            }

            // Settings View (API configuration)
            composable<ScreenDestination.Settings> {
                val settingsViewModel: SettingsViewModel = viewModel(factory = viewModelFactory)
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onCloseClick = { navController.popBackStack() }
                )
            }

            // Detail View
            composable<ScreenDestination.Detail> { backStackEntry ->
                val detail: ScreenDestination.Detail = backStackEntry.toRoute()
                val detailViewModel: DetailViewModel = viewModel(factory = viewModelFactory)
                DetailScreen(
                    titleId = detail.titleId,
                    viewModel = detailViewModel,
                    onBackClick = { navController.popBackStack() },
                    onLogClick = { title ->
                        editingLog = null
                        loggingTitle = title
                    },
                    onTitleClick = { otherTitleId ->
                        navController.navigate(ScreenDestination.Detail(otherTitleId))
                    },
                    onSagaClick = { collectionId ->
                        navController.navigate(ScreenDestination.SagaDetail(collectionId))
                    },
                    onEditLogClick = { title, log ->
                        loggingTitle = title
                        editingLog = log
                    }
                )
            }

            // Saga (TMDB collection) Detail View
            composable<ScreenDestination.SagaDetail> { backStackEntry ->
                val saga: ScreenDestination.SagaDetail = backStackEntry.toRoute()
                val sagaDetailViewModel: SagaDetailViewModel = viewModel(factory = viewModelFactory)
                SagaDetailScreen(
                    collectionId = saga.collectionId,
                    viewModel = sagaDetailViewModel,
                    onBackClick = { navController.popBackStack() },
                    onTitleClick = { titleId ->
                        navController.navigate(ScreenDestination.Detail(titleId))
                    }
                )
            }
        }

        // Overlay Log dialog when active
        val logTitle = loggingTitle
        if (logTitle != null) {
            val logViewModel: LogViewModel = viewModel(factory = viewModelFactory)
            LogBottomSheet(
                title = logTitle,
                viewModel = logViewModel,
                existingLog = editingLog,
                onDismiss = {
                    loggingTitle = null
                    editingLog = null
                }
            )
        }
    }
}
