package com.example

import com.example.navigation.ScreenDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationTest {

  @Test
  fun testScreenDestinations() {
    val detail = ScreenDestination.Detail("movie_123")
    assertEquals("movie_123", detail.titleId)

    val saga = ScreenDestination.SagaDetail(456)
    assertEquals(456, saga.collectionId)
  }

  @Test
  fun testPrimaryBottomNavItemsCount() {
    val bottomNavItems: List<ScreenDestination> = listOf(
      ScreenDestination.Home,
      ScreenDestination.Discover,
      ScreenDestination.Watchlist,
      ScreenDestination.Profile
    )
    assertEquals(4, bottomNavItems.size)
    assertFalse(bottomNavItems.contains(ScreenDestination.Search))
    assertFalse(bottomNavItems.contains(ScreenDestination.Lists))
    assertTrue(bottomNavItems.contains(ScreenDestination.Home))
    assertTrue(bottomNavItems.contains(ScreenDestination.Discover))
    assertTrue(bottomNavItems.contains(ScreenDestination.Watchlist))
    assertTrue(bottomNavItems.contains(ScreenDestination.Profile))
  }
}
