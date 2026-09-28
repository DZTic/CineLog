package com.example.navigation

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * #146 : deux taps rapides (avant la fin du fondu) ne doivent ni dépiler l'écran de départ
 * ni empiler deux fois la même fiche. Les deux appels sont faits dans le même tour de la
 * boucle UI, comme deux événements tactiles arrivés pendant la transition.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationGuardsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var navController: NavHostController
    private lateinit var openDetail: () -> Unit
    private lateinit var openSettings: () -> Unit
    private lateinit var back: () -> Unit

    @Before
    fun setUp() {
        composeTestRule.setContent {
            navController = rememberNavController()
            NavHost(navController = navController, startDestination = ScreenDestination.Home) {
                composable<ScreenDestination.Home> { entry ->
                    openDetail = { navController.navigateFrom(entry, ScreenDestination.Detail("movie_1")) }
                    openSettings = {
                        navController.navigateFrom(entry, ScreenDestination.Settings) { launchSingleTop = true }
                    }
                    Text("home")
                }
                composable<ScreenDestination.Detail> { entry ->
                    back = { navController.popBackStackFrom(entry) }
                    Text("detail ${entry.toRoute<ScreenDestination.Detail>().titleId}")
                }
                composable<ScreenDestination.Settings> { Text("settings") }
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun currentIs(route: kotlin.reflect.KClass<out ScreenDestination>): Boolean =
        navController.currentDestination?.hasRoute(route) == true

    @Test
    fun doubleBack_keepsStartDestination() {
        composeTestRule.runOnIdle { openDetail() }
        composeTestRule.onNodeWithText("detail movie_1").assertExists()

        composeTestRule.runOnIdle {
            back()
            back()
        }

        composeTestRule.onNodeWithText("home").assertExists()
        composeTestRule.runOnIdle {
            assertTrue(currentIs(ScreenDestination.Home::class))
        }
    }

    @Test
    fun doubleTapOnTitle_pushesDetailOnce() {
        composeTestRule.runOnIdle {
            openDetail()
            openDetail()
        }
        composeTestRule.waitForIdle()

        composeTestRule.runOnIdle {
            assertTrue(currentIs(ScreenDestination.Detail::class))
            assertTrue(navController.previousBackStackEntry?.destination?.hasRoute(ScreenDestination.Home::class) == true)
        }

        // Un seul « Retour » suffit pour revenir à l'accueil.
        composeTestRule.runOnIdle { back() }
        composeTestRule.runOnIdle {
            assertTrue(currentIs(ScreenDestination.Home::class))
            assertEquals(null, navController.previousBackStackEntry)
        }
    }

    @Test
    fun doubleTapOnSettings_pushesSettingsOnce() {
        composeTestRule.runOnIdle {
            openSettings()
            openSettings()
        }
        composeTestRule.waitForIdle()

        composeTestRule.runOnIdle {
            assertTrue(currentIs(ScreenDestination.Settings::class))
            assertTrue(navController.previousBackStackEntry?.destination?.hasRoute(ScreenDestination.Home::class) == true)
        }
    }

    @Test
    fun backAfterTransition_stillWorks() {
        composeTestRule.runOnIdle { openDetail() }
        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle { back() }
        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle { openDetail() }

        composeTestRule.onNodeWithText("detail movie_1").assertExists()
    }
}
