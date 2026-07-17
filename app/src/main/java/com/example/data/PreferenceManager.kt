package com.example.data

import android.content.Context
import android.content.SharedPreferences

class PreferenceManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("cinelog_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_TMDB_API_KEY = "tmdb_api_key"
    }

    fun getTmdbApiKey(): String {
        return prefs.getString(KEY_TMDB_API_KEY, "") ?: ""
    }

    fun setTmdbApiKey(key: String) {
        prefs.edit().putString(KEY_TMDB_API_KEY, key.trim()).apply()
    }
}
