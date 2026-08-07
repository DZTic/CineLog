package com.example.ui.lists

import androidx.compose.foundation.lazy.LazyListState
import kotlinx.coroutines.MainScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListsScreenTest {

    @Test
    fun dragDropState_initialState_isNotDragging() {
        val state = DragDropState(
            lazyListState = LazyListState(),
            coroutineScope = MainScope(),
            onMove = { _, _ -> },
            onDragEnd = {}
        )

        assertNull(state.draggingItemIndex)
        assertEquals(0f, state.draggingItemOffset, 0.001f)
    }

    @Test
    fun dragDropState_onDragStart_setsDraggingIndex() {
        val state = DragDropState(
            lazyListState = LazyListState(),
            coroutineScope = MainScope(),
            onMove = { _, _ -> },
            onDragEnd = {}
        )

        state.onDragStart(2)

        assertEquals(2, state.draggingItemIndex)
        assertEquals(0f, state.draggingItemOffset, 0.001f)
    }

    @Test
    fun dragDropState_onDragInterrupted_resetsStateAndCallsDragEnd() {
        var dragEndCalled = false
        val state = DragDropState(
            lazyListState = LazyListState(),
            coroutineScope = MainScope(),
            onMove = { _, _ -> },
            onDragEnd = { dragEndCalled = true }
        )

        state.onDragStart(1)
        state.onDragInterrupted()

        assertNull(state.draggingItemIndex)
        assertEquals(0f, state.draggingItemOffset, 0.001f)
        assertEquals(true, dragEndCalled)
    }
}
