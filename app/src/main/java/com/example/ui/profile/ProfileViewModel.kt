package com.example.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val repository: Repository
) : ViewModel() {
    val allLogs: StateFlow<List<DbLogEntry>> = repository.allLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allWatchlist: StateFlow<List<DbWatchlist>> = repository.allWatchlist
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCustomLists: StateFlow<List<DbCustomList>> = repository.allCustomLists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _profileRefreshing = MutableStateFlow(false)
    val profileRefreshing: StateFlow<Boolean> = _profileRefreshing.asStateFlow()

    fun refreshProfile() {
        viewModelScope.launch {
            _profileRefreshing.value = true
            try {
                delay(600)
            } finally {
                _profileRefreshing.value = false
            }
        }
    }
}
