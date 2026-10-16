package com.example.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ImportSummary
import com.example.data.PreferenceManager
import com.example.data.Repository
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

enum class BackupFormat { JSON, CSV }

/** One-shot outcome of an export/import, shown once by the screen then cleared. */
sealed interface BackupResult {
    data class Exported(val format: BackupFormat) : BackupResult
    data class Imported(val summary: ImportSummary) : BackupResult
    data class ExportFailed(val message: String?) : BackupResult
    data class ImportFailed(val message: String?) : BackupResult
    data object FileTooLarge : BackupResult
}

class BackupFileTooLargeException : IllegalArgumentException("Fichier trop volumineux")

/**
 * Reads at most [maxBytes] from [input] as UTF-8. Throws [BackupFileTooLargeException]
 * instead of buffering an arbitrarily large file (e.g. a video picked by mistake), which
 * used to end in an OutOfMemoryError (#158).
 */
internal fun readBoundedUtf8(input: InputStream, maxBytes: Int): String {
    val buffer = java.io.ByteArrayOutputStream()
    val chunk = ByteArray(8 * 1024)
    var total = 0
    while (true) {
        val read = input.read(chunk)
        if (read < 0) break
        total += read
        if (total > maxBytes) throw BackupFileTooLargeException()
        buffer.write(chunk, 0, read)
    }
    return buffer.toString(Charsets.UTF_8.name())
}

class SettingsViewModel(
    private val preferenceManager: PreferenceManager,
    private val repository: Repository? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
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

    fun setTmdbApiKey(key: String): Job {
        _tmdbApiKey.value = key
        return viewModelScope.launch(ioDispatcher) { preferenceManager.updateTmdbApiKey(key) }
    }

    fun setThemeMode(mode: AppThemeMode): Job {
        _themeMode.value = mode
        return viewModelScope.launch(ioDispatcher) { preferenceManager.updateThemeMode(mode.name) }
    }

    fun setDynamicColor(enabled: Boolean): Job {
        _dynamicColor.value = enabled
        return viewModelScope.launch(ioDispatcher) { preferenceManager.updateDynamicColorEnabled(enabled) }
    }

    fun setAppLanguage(languageCode: String): Job {
        _appLanguage.value = languageCode
        return viewModelScope.launch(ioDispatcher) { preferenceManager.updateAppLanguage(languageCode) }
    }

    suspend fun generateJsonBackup(): String? {
        return repository?.exportBackupJson()
    }

    suspend fun generateCsvExport(): String? {
        return repository?.exportBackupCsv()
    }

    private val _backupResult = MutableStateFlow<BackupResult?>(null)
    val backupResult: StateFlow<BackupResult?> = _backupResult.asStateFlow()

    private val _backupInProgress = MutableStateFlow(false)
    val backupInProgress: StateFlow<Boolean> = _backupInProgress.asStateFlow()

    /**
     * Writes a backup to the stream returned by [openOutput]. Runs in [viewModelScope] on
     * [ioDispatcher] so it neither blocks the UI thread nor gets cancelled when the screen
     * leaves composition (#158).
     */
    fun exportBackup(format: BackupFormat, openOutput: () -> OutputStream?): Job =
        runBackupOperation {
            try {
                val content = when (format) {
                    BackupFormat.JSON -> generateJsonBackup()
                    BackupFormat.CSV -> generateCsvExport()
                } ?: throw IllegalStateException("Repository non disponible")
                withContext(ioDispatcher) {
                    val stream = openOutput() ?: throw IllegalStateException("Fichier inaccessible")
                    stream.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                }
                BackupResult.Exported(format)
            } catch (e: Exception) {
                BackupResult.ExportFailed(e.localizedMessage)
            }
        }

    /** Reads (size-capped) and imports the file returned by [openInput], off the UI thread (#158). */
    fun importBackupFrom(openInput: () -> InputStream?): Job =
        runBackupOperation {
            try {
                val content = withContext(ioDispatcher) {
                    val stream = openInput() ?: throw IllegalStateException("Fichier inaccessible")
                    stream.use { readBoundedUtf8(it, MAX_IMPORT_BYTES) }
                }
                if (content.isBlank()) throw IllegalArgumentException("Fichier vide")
                importBackup(content).fold(
                    onSuccess = { BackupResult.Imported(it) },
                    onFailure = { BackupResult.ImportFailed(it.localizedMessage) }
                )
            } catch (e: BackupFileTooLargeException) {
                BackupResult.FileTooLarge
            } catch (e: Exception) {
                BackupResult.ImportFailed(e.localizedMessage)
            }
        }

    fun onBackupResultShown() {
        _backupResult.value = null
    }

    private fun runBackupOperation(block: suspend () -> BackupResult): Job =
        viewModelScope.launch {
            _backupInProgress.value = true
            try {
                _backupResult.value = block()
            } finally {
                _backupInProgress.value = false
            }
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

    companion object {
        /** Largest backup the import accepts; real exports are far smaller. */
        const val MAX_IMPORT_BYTES = 20 * 1024 * 1024
    }
}
