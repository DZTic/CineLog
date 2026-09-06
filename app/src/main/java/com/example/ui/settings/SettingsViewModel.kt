package com.example.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ImportSummary
import com.example.data.PreferenceManager
import com.example.data.Repository
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferenceManager: PreferenceManager,
    private val repository: Repository? = null
) : ViewModel() {
    private val _tmdbApiKey = MutableStateFlow(preferenceManager.getTmdbApiKey())
    val tmdbApiKey: StateFlow<String> = _tmdbApiKey.asStateFlow()

    private val _themeMode = MutableStateFlow(
        try {
            AppThemeMode.valueOf(preferenceManager.getThemeMode())
        } catch (_: Exception) {
            AppThemeMode.DARK
        }
    )
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(preferenceManager.isDynamicColorEnabled())
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _appLanguage = MutableStateFlow(preferenceManager.getAppLanguage())
    val appLanguage: StateFlow<String> = _appLanguage.asStateFlow()

    fun setTmdbApiKey(key: String) {
        _tmdbApiKey.value = key
        viewModelScope.launch(Dispatchers.IO) { preferenceManager.updateTmdbApiKey(key) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
        viewModelScope.launch(Dispatchers.IO) { preferenceManager.updateThemeMode(mode.name) }
    }

    fun setDynamicColor(enabled: Boolean) {
        _dynamicColor.value = enabled
        viewModelScope.launch(Dispatchers.IO) { preferenceManager.updateDynamicColorEnabled(enabled) }
    }

    fun setAppLanguage(languageCode: String) {
        _appLanguage.value = languageCode
        viewModelScope.launch(Dispatchers.IO) { preferenceManager.updateAppLanguage(languageCode) }
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
