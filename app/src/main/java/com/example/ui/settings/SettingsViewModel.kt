package com.example.ui.settings

import androidx.lifecycle.ViewModel
import com.example.data.ImportSummary
import com.example.data.PreferenceManager
import com.example.data.Repository
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
        preferenceManager.setTmdbApiKey(key)
        _tmdbApiKey.value = key
    }

    fun setThemeMode(mode: AppThemeMode) {
        preferenceManager.setThemeMode(mode.name)
        _themeMode.value = mode
    }

    fun setDynamicColor(enabled: Boolean) {
        preferenceManager.setDynamicColorEnabled(enabled)
        _dynamicColor.value = enabled
    }

    fun setAppLanguage(languageCode: String) {
        preferenceManager.setAppLanguage(languageCode)
        _appLanguage.value = languageCode
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
