package com.example.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ImportSummary
import com.example.data.PreferenceManager
import com.example.data.Repository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferenceManager: PreferenceManager,
    private val repository: Repository? = null
) : ViewModel() {
    val tmdbApiKey: StateFlow<String> = preferenceManager.tmdbApiKeyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), preferenceManager.getTmdbApiKey())

    fun setTmdbApiKey(key: String) {
        viewModelScope.launch {
            preferenceManager.updateTmdbApiKey(key)
        }
    }

    suspend fun generateJsonBackup(): String? {
        return repository?.exportBackupJson()
    }

    suspend fun generateCsvExport(): String? {
        return repository?.exportBackupCsv()
    }

    suspend fun importBackup(content: String): Result<ImportSummary> {
        val repo = repository ?: return Result.failure(IllegalStateException("Repository non disponible"))
        return try {
            val summary = repo.importBackup(content)
            Result.success(summary)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
