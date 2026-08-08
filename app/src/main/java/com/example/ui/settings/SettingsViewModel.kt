package com.example.ui.settings

import androidx.lifecycle.ViewModel
import com.example.data.PreferenceManager
import com.example.data.Repository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(
    private val preferenceManager: PreferenceManager,
    private val repository: Repository? = null
) : ViewModel() {
    private val _tmdbApiKey = MutableStateFlow(preferenceManager.getTmdbApiKey())
    val tmdbApiKey: StateFlow<String> = _tmdbApiKey.asStateFlow()

    fun setTmdbApiKey(key: String) {
        preferenceManager.setTmdbApiKey(key)
        _tmdbApiKey.value = key
    }
}
