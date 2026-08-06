package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationTest {

  @Test
  fun testScreenRoutes() {
    assertEquals("home", Screen.Home.route)
    assertEquals("discover", Screen.Discover.route)
    assertEquals("search", Screen.Search.route)
    assertEquals("watchlist", Screen.Watchlist.route)
    assertEquals("lists", Screen.Lists.route)
    assertEquals("profile", Screen.Profile.route)
    assertEquals("settings", Screen.Settings.route)
  }

  @Test
  fun testPrimaryBottomNavItemsCount() {
    val bottomNavItems = listOf(
      Screen.Home,
      Screen.Discover,
      Screen.Watchlist,
      Screen.Profile
    )
    assertEquals(4, bottomNavItems.size)
    assertFalse(bottomNavItems.contains(Screen.Search))
    assertFalse(bottomNavItems.contains(Screen.Lists))
    assertTrue(bottomNavItems.contains(Screen.Home))
    assertTrue(bottomNavItems.contains(Screen.Discover))
    assertTrue(bottomNavItems.contains(Screen.Watchlist))
    assertTrue(bottomNavItems.contains(Screen.Profile))
  }
}
