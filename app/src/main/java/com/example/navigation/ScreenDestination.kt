package com.example.navigation

import kotlinx.serialization.Serializable

sealed interface ScreenDestination {
    @Serializable
    data object Home : ScreenDestination

    @Serializable
    data object Discover : ScreenDestination

    @Serializable
    data object Search : ScreenDestination

    @Serializable
    data object Watchlist : ScreenDestination

    @Serializable
    data object Lists : ScreenDestination

    @Serializable
    data object Profile : ScreenDestination

    @Serializable
    data object Settings : ScreenDestination

    @Serializable
    data class Detail(val titleId: String) : ScreenDestination

    @Serializable
    data class SagaDetail(val collectionId: Int) : ScreenDestination
}
