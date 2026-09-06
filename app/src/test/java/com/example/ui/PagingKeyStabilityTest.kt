package com.example.ui

import com.example.data.CineTitle
import com.example.data.TitleType
import com.example.ui.components.GroupedDisplay
import com.example.ui.components.SagaGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PagingKeyStabilityTest {

    private fun createTitle(id: String, name: String = "Test"): CineTitle {
        return CineTitle(
            id = id,
            type = TitleType.FILM,
            title = name,
            year = "2024",
            posterUrl = null,
            synopsis = "",
            genres = emptyList(),
            voteAverage = 4.0f
        )
    }

    @Test
    fun pagingKey_remainsStable_whenIndexChanges() {
        val itemA = createTitle("movie_123", "Inception")
        val itemB = createTitle("movie_456", "Interstellar")

        // Old key generation logic: "${it.id}_$index"
        val oldKeyAtIndex0 = "${itemA.id}_0"
        val oldKeyAtIndex1 = "${itemA.id}_1"
        assertNotEquals(
            "Ancien pattern instable : la clé change lorsque l'index change",
            oldKeyAtIndex0,
            oldKeyAtIndex1
        )

        // New stable key logic: peek(index)?.id ?: index
        val keyProvider: (CineTitle?, Int) -> Any = { item, index -> item?.id ?: index }

        val stableKeyAtIndex0 = keyProvider(itemA, 0)
        val stableKeyAtIndex1 = keyProvider(itemA, 1)
        val stableKeyAtIndex99 = keyProvider(itemA, 99)

        assertEquals("movie_123", stableKeyAtIndex0)
        assertEquals("movie_123", stableKeyAtIndex1)
        assertEquals("movie_123", stableKeyAtIndex99)
        assertEquals(
            "La clé stable doit être invariante par rapport à la position dans la liste",
            stableKeyAtIndex0,
            stableKeyAtIndex1
        )

        // Différents items doivent avoir des clés différentes
        assertNotEquals(keyProvider(itemA, 0), keyProvider(itemB, 1))
    }

    @Test
    fun pagingKey_fallsBackToIndex_whenPlaceholder() {
        val keyProvider: (CineTitle?, Int) -> Any = { item, index -> item?.id ?: index }

        assertEquals(0, keyProvider(null, 0))
        assertEquals(5, keyProvider(null, 5))
    }

    @Test
    fun contentType_discriminates_singleAndGroupedDisplays() {
        val singleItem = GroupedDisplay.Single(createTitle("movie_123"))
        val groupedItem = GroupedDisplay.Grouped(
            SagaGroup(
                collectionId = 10,
                collectionName = "The Dark Knight Trilogy",
                posterUrl = null,
                items = listOf(createTitle("movie_123"))
            )
        )

        val contentTypeProvider: (GroupedDisplay<*>) -> String = { display ->
            display::class.java.simpleName
        }

        assertEquals("Single", contentTypeProvider(singleItem))
        assertEquals("Grouped", contentTypeProvider(groupedItem))
        assertNotEquals(
            "Compose doit avoir deux types de contenu distincts pour recycler les slots",
            contentTypeProvider(singleItem),
            contentTypeProvider(groupedItem)
        )
    }
}
