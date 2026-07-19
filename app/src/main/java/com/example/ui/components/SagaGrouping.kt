package com.example.ui.components

/**
 * A saga (TMDB "collection") made of several underlying items (log entries,
 * watchlist entries, or search results) that all share the same collectionId.
 */
data class SagaGroup<T>(
    val collectionId: Int,
    val collectionName: String,
    val posterUrl: String?,
    val items: List<T>
)

/**
 * Either a single, standalone item, or a saga grouping several items
 * together. Used so the Watchlist, "Activité Récente" (Home) and Search
 * screens can all show one entry per franchise instead of one per movie.
 */
sealed class GroupedDisplay<T> {
    data class Single<T>(val item: T) : GroupedDisplay<T>()
    data class Grouped<T>(val group: SagaGroup<T>) : GroupedDisplay<T>()
}

/**
 * Groups a list of items by their TMDB saga (collection), when known.
 * Items with no collectionId are kept as standalone entries. The poster used
 * for a group is taken from the first item encountered in that group.
 *
 * Order is not guaranteed: callers should sort the result as needed for
 * their screen (e.g. by most recent date, by title, ...).
 */
fun <T> List<T>.groupBySaga(
    collectionId: (T) -> Int?,
    collectionName: (T) -> String?,
    posterUrl: (T) -> String?
): List<GroupedDisplay<T>> {
    val (withSaga, withoutSaga) = partition { collectionId(it) != null }

    val groups = withSaga
        .groupBy { collectionId(it) }
        .map { (id, groupItems) ->
            val name = groupItems.firstNotNullOfOrNull { collectionName(it)?.takeIf(String::isNotBlank) }
                ?: "Saga"
            GroupedDisplay.Grouped(
                SagaGroup(
                    collectionId = id!!,
                    collectionName = name,
                    posterUrl = posterUrl(groupItems.first()),
                    items = groupItems
                )
            )
        }

    val singles = withoutSaga.map { GroupedDisplay.Single(it) }

    return groups + singles
}
