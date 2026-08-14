package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/**
 * Sans cette classe, Coil utilise un ImageLoader par défaut sans fondu :
 * chaque affiche "pop" brutalement dès qu'elle finit de charger/décoder,
 * ce qui contribue à une sensation de manque de fluidité, en particulier
 * en changeant d'onglet quand plusieurs affiches se chargent d'un coup.
 * En déclarant cette Application (voir AndroidManifest.xml), Coil détecte
 * automatiquement l'ImageLoaderFactory et l'utilise pour tous les
 * AsyncImage de l'app, sans avoir à toucher chaque écran individuellement.
 */
class CineLogApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        if (org.koin.core.context.GlobalContext.getOrNull() == null) {
            startKoin {
                androidLogger(Level.ERROR)
                androidContext(this@CineLogApplication)
                modules(appModules)
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .crossfade(200)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024)
                    .build()
            }
            .build()
    }
}
