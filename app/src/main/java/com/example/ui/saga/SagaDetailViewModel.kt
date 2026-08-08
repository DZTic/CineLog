package com.example.ui.saga

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SagaDetailViewModel(
    private val repository: Repository
) : ViewModel() {
    private val tag = "SagaDetailViewModel"

    private val _sagaLoading = MutableStateFlow(false)
    val sagaLoading: StateFlow<Boolean> = _sagaLoading.asStateFlow()

    private val _sagaError = MutableStateFlow<String?>(null)
    val sagaError: StateFlow<String?> = _sagaError.asStateFlow()

    private val _sagaInfo = MutableStateFlow<Repository.SagaInfo?>(null)
    val sagaInfo: StateFlow<Repository.SagaInfo?> = _sagaInfo.asStateFlow()

    private val _sagaTitles = MutableStateFlow<List<CineTitle>>(emptyList())
    val sagaTitles: StateFlow<List<CineTitle>> = _sagaTitles.asStateFlow()

    val allLogs: StateFlow<List<DbLogEntry>> = repository.allLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allWatchlist: StateFlow<List<DbWatchlist>> = repository.allWatchlist
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadSagaDetail(collectionId: Int) {
        viewModelScope.launch {
            _sagaLoading.value = true
            _sagaError.value = null
            _sagaInfo.value = null
            _sagaTitles.value = emptyList()
            try {
                val result = repository.getSagaDetail(collectionId)
                if (result != null) {
                    _sagaInfo.value = result.first
                    _sagaTitles.value = result.second
                } else {
                    _sagaError.value = "Impossible de charger cette saga."
                }
            } catch (e: Exception) {
                Log.e(tag, "Error loading saga detail: ${e.localizedMessage}")
                _sagaError.value = e.localizedMessage ?: "Erreur de chargement de la saga."
            } finally {
                _sagaLoading.value = false
            }
        }
    }

    fun addAllToWatchlist(titles: List<CineTitle>) {
        viewModelScope.launch {
            titles.forEach { title ->
                try {
                    val alreadyIn = repository.isInWatchlist(title.id).first()
                    if (!alreadyIn) {
                        repository.addToWatchlist(title)
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error adding ${title.title} to watchlist: ${e.localizedMessage}")
                }
            }
        }
    }
}
