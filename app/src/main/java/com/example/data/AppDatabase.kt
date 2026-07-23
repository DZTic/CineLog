package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// ─────────────────────────────────────────────────────────────────────────
// CHECKLIST — à chaque fois qu'un @Entity change (nouvelle table, nouvelle
// colonne, renommage, etc.), les 3 étapes ci-dessous vont ensemble :
//   1. Ajouter/modifier l'@Entity concerné (Entities.kt).
//   2. Incrémenter `version` juste en dessous.
//   3. Écrire un `MIGRATION_x_y` dans Migrations.kt et l'enregistrer dans
//      `.addMigrations(...)` plus bas.
// Oublier l'étape 2 ou 3 fait planter l'app au lancement pour un appareil
// qui a déjà des données locales (Room valide le schéma à l'ouverture), ou,
// pire, silencieusement effacer les données via fallbackToDestructiveMigration.
// `exportSchema = true` laisse une trace dans schemas/ à chaque version :
// vérifier qu'un commit ajoute bien un NOUVEAU fichier <version>.json plutôt
// que de modifier un fichier existant en place.
// ─────────────────────────────────────────────────────────────────────────
@Database(
    entities = [
        DbLogEntry::class,
        DbWatchlist::class,
        DbCustomList::class,
        DbCustomListTitle::class,
        DbSeasonProgress::class,
        DbCollectionCache::class,
        DbSagaSize::class
    ],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun logDao(): LogDao
    abstract fun watchlistDao(): WatchlistDao
    abstract fun customListDao(): CustomListDao
    abstract fun seasonProgressDao(): SeasonProgressDao
    abstract fun collectionCacheDao(): CollectionCacheDao
    abstract fun sagaSizeDao(): SagaSizeDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cinelog_database"
                )
                .addMigrations(MIGRATION_4_5)
                // Safety net only: covers versions with no explicit migration
                // written yet (or migrations from before this file existed).
                // Every NEW version bump should get its own MIGRATION_x_y
                // above and registered here, so this never has to run for it.
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
