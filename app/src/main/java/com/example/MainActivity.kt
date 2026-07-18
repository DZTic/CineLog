package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.*
import com.example.ui.CineViewModel
import com.example.ui.CineViewModelFactory
import com.example.ui.detail.DetailScreen
import com.example.ui.discover.DiscoverScreen
import com.example.ui.home.HomeScreen
import com.example.ui.lists.ListsScreen
import com.example.ui.log.LogDialog
import com.example.ui.profile.ProfileScreen
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
            preferenceManager = preferenceManager
        )

        // 2. Instantiate master view model
        val viewModelFactory = CineViewModelFactory(application, repository, preferenceManager)
        val viewModel = ViewModelProvider(this, viewModelFactory)[CineViewModel::class.java]

        setContent {
            MyApplicationTheme {
                MainAppScaffold(viewModel)
            }
        }
    }
}

// Navigation Routes
sealed class Screen(val route: String, val title: String) {
    object Home : Screen("home", "Accueil")
    object Discover : Screen("discover", "Découvrir")
    object Search : Screen("search", "Recherche")
    object Watchlist : Screen("watchlist", "Watchlist")
    object Lists : Screen("lists", "Mes Listes")
    object Profile : Screen("profile", "Profil")
    object Settings : Screen("settings", "Paramètres")
    object Detail : Screen("detail/{titleId}", "Détails") {
        fun createRoute(titleId: String) = "detail/$titleId"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(viewModel: CineViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Logging sheet dialog trigger state
    var loggingTitle by remember { mutableStateOf<CineTitle?>(null) }

    // Core Tab Routes for Bottom Navigation
    val bottomNavItems = listOf(
        Screen.Home,
        Screen.Discover,
        Screen.Search,
        Screen.Watchlist,
        Screen.Lists,
        Screen.Profile
    )

    Scaffold(
        bottomBar = {
            // Only show bottom navigation on primary tabs
            val isPrimaryTab = bottomNavItems.any { it.route == currentRoute }
            if (isPrimaryTab) {
                // Material3's NavigationBar/NavigationBarItem uses a fixed
                // ~64dp active-indicator pill regardless of how narrow each
                // column is. With 6 tabs, columns are narrower than that on
                // basically every phone, so the pill for the edge tabs
                // (Accueil, Profil) always bled past the screen edge no
                // matter how much outer padding/clipping was added, since
                // outer padding doesn't change the pill's fixed width vs.
                // the column's available width.
                //
                // This custom bar sizes the selection pill from its own
                // content (icon + small padding) instead of a fixed token,
                // so it's always narrower than its column and can never
                // overflow, regardless of screen width or item count.
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
                            val selected = currentRoute == screen.route
                            val icon = when (screen) {
                                Screen.Home -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
                                Screen.Discover -> if (selected) Icons.Filled.Search else Icons.Outlined.Search
                                Screen.Search -> if (selected) Icons.Filled.Search else Icons.Outlined.Search
                                Screen.Watchlist -> if (selected) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder
                                Screen.Lists -> if (selected) Icons.Filled.List else Icons.Outlined.List
                                Screen.Profile -> if (selected) Icons.Filled.Person else Icons.Outlined.Person
                                else -> Icons.Filled.Home
                            }

                            val label = when (screen) {
                                Screen.Home -> "Accueil"
                                Screen.Discover -> "Découvrir"
                                Screen.Search -> "Recherche"
                                Screen.Watchlist -> "À Voir"
                                Screen.Lists -> "Listes"
                                Screen.Profile -> "Profil"
                                else -> screen.title
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        if (currentRoute != screen.route) {
                                            navController.navigate(screen.route) {
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
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            // Home View
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = viewModel,
                    onTitleClick = { titleId ->
                        navController.navigate(Screen.Detail.createRoute(titleId))
                    },
                    onNavigateToDiscover = {
                        navController.navigate(Screen.Discover.route)
                    }
                )
            }

            // Discover Carousel / Grids View
            composable(Screen.Discover.route) {
                DiscoverScreen(
                    viewModel = viewModel,
                    onTitleClick = { titleId ->
                        navController.navigate(Screen.Detail.createRoute(titleId))
                    }
                )
            }

            // Global Search View
            composable(Screen.Search.route) {
                SearchScreen(
                    viewModel = viewModel,
                    onTitleClick = { titleId ->
                        navController.navigate(Screen.Detail.createRoute(titleId))
                    }
                )
            }

            // Watchlist View
            composable(Screen.Watchlist.route) {
                WatchlistScreen(
                    viewModel = viewModel,
                    onTitleClick = { titleId ->
                        navController.navigate(Screen.Detail.createRoute(titleId))
                    }
                )
            }

            // Custom user Lists View
            composable(Screen.Lists.route) {
                ListsScreen(
                    viewModel = viewModel,
                    onTitleClick = { titleId ->
                        navController.navigate(Screen.Detail.createRoute(titleId))
                    }
                )
            }

            // Profile Screen with Settings trigger
            composable(Screen.Profile.route) {
                Scaffold(
                    contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                    topBar = {
                        TopAppBar(
                            title = { Text("Mon Profil CinéLog") },
                            actions = {
                                IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
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
                    ProfileScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(padding)
                    )
                }
            }

            // Settings View (API configuration)
            composable(Screen.Settings.route) {
                SettingsScreen(
                    viewModel = viewModel,
                    onCloseClick = { navController.popBackStack() }
                )
            }

            // Detail View
            composable(
                route = Screen.Detail.route,
                arguments = listOf(navArgument("titleId") { type = NavType.StringType })
            ) { backStackEntry ->
                val titleId = backStackEntry.arguments?.getString("titleId") ?: ""
                DetailScreen(
                    titleId = titleId,
                    viewModel = viewModel,
                    onBackClick = { navController.popBackStack() },
                    onLogClick = { title -> loggingTitle = title }
                )
            }
        }

        // Overlay Log dialog when active
        val logTitle = loggingTitle
        if (logTitle != null) {
            LogDialog(
                title = logTitle,
                viewModel = viewModel,
                onDismiss = { loggingTitle = null }
            )
        }
    }
}
