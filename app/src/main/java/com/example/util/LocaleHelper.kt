package com.example.util

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object LocaleHelper {
    const val LANG_SYSTEM = "system"
    const val LANG_FR = "fr"
    const val LANG_EN = "en"

    fun applyLanguage(context: Context, languageCode: String): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(Context.LOCALE_SERVICE) as? LocaleManager
            if (localeManager != null) {
                if (languageCode == LANG_SYSTEM || languageCode.isBlank()) {
                    localeManager.applicationLocales = LocaleList.getEmptyLocaleList()
                } else {
                    localeManager.applicationLocales = LocaleList(Locale.forLanguageTag(languageCode))
                }
            }
        }

        if (languageCode == LANG_SYSTEM || languageCode.isBlank()) {
            return context
        }

        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
