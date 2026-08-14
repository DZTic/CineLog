package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.data.*
import com.example.navigation.ScreenDestination
import com.example.ui.CineViewModelFactory
import com.example.ui.detail.DetailScreen
import com.example.ui.detail.DetailViewModel
import com.example.ui.discover.DiscoverScreen
import com.example.ui.discover.DiscoverViewModel
import com.example.ui.home.HomeScreen
import com.example.ui.home.HomeViewModel
import com.example.ui.lists.ListsScreen
import com.example.ui.lists.ListsViewModel
import com.example.ui.log.LogBottomSheet
import com.example.ui.log.LogViewModel
import com.example.ui.profile.ProfileScreen
import com.example.ui.profile.ProfileViewModel
import com.example.ui.saga.SagaDetailScreen
import com.example.ui.saga.SagaDetailViewModel
import com.example.ui.search.SearchScreen
import com.example.ui.search.SearchViewModel
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsViewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.watchlist.WatchlistScreen
import com.example.ui.watchlist.WatchlistViewModel
import com.example.util.ConnectivityNetworkMonitor
import com.example.util.NetworkMonitor
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val networkMonitor: NetworkMonitor = ConnectivityNetworkMonitor(applicationContext)

        setContent {
            val settingsViewModel: SettingsViewModel = koinViewModel()
            val themeMode by settingsViewModel.themeMode.collectAsState()
            val dynamicColor by settingsViewModel.dynamicColor.collectAsState()

            MyApplicationTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                MainAppScaffold(networkMonitor = networkMonitor)
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
                        ScreenDestination.Home -> stringResource(R.string.nav_home)
                        ScreenDestination.Discover -> stringResource(R.string.nav_discover)
                        ScreenDestination.Watchlist -> stringResource(R.string.nav_watchlist)
                        ScreenDestination.Profile -> stringResource(R.string.nav_profile)
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
fun MainAppScaffold(
    viewModelFactory: CineViewModelFactory? = null,
    networkMonitor: NetworkMonitor? = null
) {
    val navController = rememberNavController()

    val isOnline by (networkMonitor?.isOnline ?: remember { mutableStateOf(true) })
        .let { if (it is kotlinx.coroutines.flow.Flow<*>) (it as kotlinx.coroutines.flow.Flow<Boolean>).collectAsState(initial = true) else it as State<Boolean> }

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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            NavHost(
                navController = navController,
                startDestination = ScreenDestination.Home,
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
                    val homeViewModel: HomeViewModel = if (viewModelFactory != null) viewModel(factory = viewModelFactory) else koinViewModel()
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
                    val discoverViewModel: DiscoverViewModel = if (viewModelFactory != null) viewModel(factory = viewModelFactory) else koinViewModel()
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
                    val searchViewModel: SearchViewModel = if (viewModelFactory != null) viewModel(factory = viewModelFactory) else koinViewModel()
                    val logViewModel: LogViewModel = if (viewModelFactory != null) viewModel(factory = viewModelFactory) else koinViewModel()
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
                    val watchlistViewModel: WatchlistViewModel = if (viewModelFactory != null) viewModel(factory = viewModelFactory) else koinViewModel()
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
                    val listsViewModel: ListsViewModel = if (viewModelFactory != null) viewModel(factory = viewModelFactory) else koinViewModel()
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
                        val profileViewModel: ProfileViewModel = if (viewModelFactory != null) viewModel(factory = viewModelFactory) else koinViewModel()
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
                    val settingsViewModel: SettingsViewModel = if (viewModelFactory != null) viewModel(factory = viewModelFactory) else koinViewModel()
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        onCloseClick = { navController.popBackStack() }
                    )
                }

                // Detail View
                composable<ScreenDestination.Detail> { backStackEntry ->
                    val detail: ScreenDestination.Detail = backStackEntry.toRoute()
                    val detailViewModel: DetailViewModel = if (viewModelFactory != null) viewModel(factory = viewModelFactory) else koinViewModel()
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
                    val sagaDetailViewModel: SagaDetailViewModel = if (viewModelFactory != null) viewModel(factory = viewModelFactory) else koinViewModel()
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

            AnimatedVisibility(
                visible = !isOnline,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("offline_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.offline_banner_text),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // Overlay Log dialog when active
        val logTitle = loggingTitle
        if (logTitle != null) {
            val logViewModel: LogViewModel = if (viewModelFactory != null) viewModel(factory = viewModelFactory) else koinViewModel()
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
