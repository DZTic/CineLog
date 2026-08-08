package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory

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
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .crossfade(200)
            .build()
    }
}
