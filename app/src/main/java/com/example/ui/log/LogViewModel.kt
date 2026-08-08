package com.example.ui.log

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DbLogEntry
import com.example.data.Repository
import kotlinx.coroutines.launch

class LogViewModel(
    private val repository: Repository
) : ViewModel() {
    private val tag = "LogViewModel"

    fun logVisionnage(
        titleId: String,
        titleType: String,
        titleName: String,
        titlePosterUrl: String?,
        dateVue: Long,
        note: Float,
        critique: String,
        revisionnage: Boolean,
        spoiler: Boolean,
        collectionId: Int? = null,
        collectionName: String? = null,
        collectionPosterUrl: String? = null,
        id: Int = 0
    ) {
        viewModelScope.launch {
            try {
                val entry = DbLogEntry(
                    id = id,
                    titleId = titleId,
                    titleType = titleType,
                    titleName = titleName,
                    titlePosterUrl = titlePosterUrl,
                    dateVue = dateVue,
                    note = note,
                    critique = critique,
                    revisionnage = revisionnage,
                    spoiler = spoiler,
                    collectionId = collectionId,
                    collectionName = collectionName,
                    collectionPosterUrl = collectionPosterUrl
                )
                repository.insertLog(entry)
                repository.removeFromWatchlist(titleId)
            } catch (e: Exception) {
                Log.e(tag, "Error saving log entry: ${e.localizedMessage}")
            }
        }
    }
}
