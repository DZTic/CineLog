package com.example.ui.lists

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ListsViewModel(
    private val repository: Repository
) : ViewModel() {
    private val tag = "ListsViewModel"

    val allCustomLists: StateFlow<List<DbCustomList>> = repository.allCustomLists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createCustomList(name: String, description: String) {
        viewModelScope.launch {
            try {
                repository.createCustomList(name, description)
            } catch (e: Exception) {
                Log.e(tag, "Error creating custom list: ${e.localizedMessage}")
            }
        }
    }

    fun deleteCustomList(listId: Int) {
        viewModelScope.launch {
            try {
                repository.deleteCustomList(listId)
            } catch (e: Exception) {
                Log.e(tag, "Error deleting custom list: ${e.localizedMessage}")
            }
        }
    }

    fun getCustomListDetail(listId: Int): Flow<DbCustomList?> = repository.getCustomListById(listId)

    fun getCustomListTitlesFlow(listId: Int): Flow<List<DbCustomListTitle>> = repository.getCustomListTitles(listId)

    fun removeTitleFromCustomList(id: Int) {
        viewModelScope.launch {
            try {
                repository.removeTitleFromCustomList(id)
            } catch (e: Exception) {
                Log.e(tag, "Error removing item from custom list: ${e.localizedMessage}")
            }
        }
    }

    fun reorderCustomListTitles(listId: Int, items: List<DbCustomListTitle>) {
        viewModelScope.launch {
            try {
                items.forEachIndexed { index, item ->
                    repository.updateCustomListTitleOrder(item.id, index)
                }
            } catch (e: Exception) {
                Log.e(tag, "Error reordering custom list: ${e.localizedMessage}")
            }
        }
    }
}
