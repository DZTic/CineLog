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
//   3. Écrire un `MIGRATION_x_y` dans Migrations.kt et l'ajouter à
//      `ALL_MIGRATIONS` (même fichier).
// Oublier l'étape 2 ou 3 fait planter l'app au lancement pour un appareil
// qui a déjà des données locales (Room valide le schéma à l'ouverture). Il
// n'y a volontairement aucun repli destructif à partir de la version 4 :
// mieux vaut un plantage (données intactes) qu'un effacement silencieux.
// MigrationTest échoue si la chaîne de migrations a un trou.
// `exportSchema = true` laisse une trace dans schemas/ à chaque version :
// commiter le NOUVEAU fichier <version>.json, ne jamais modifier un fichier
// existant en place (la CI échoue dans les deux cas).
// ─────────────────────────────────────────────────────────────────────────
@Database(
    entities = [
        DbLogEntry::class,
        DbWatchlist::class,
        DbCustomList::class,
        DbCustomListTitle::class,
        DbSeasonProgress::class,
        DbCollectionCache::class,
        DbSagaSize::class,
        DbTitleMetaCache::class
    ],
    version = 10,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun logDao(): LogDao
    abstract fun watchlistDao(): WatchlistDao
    abstract fun customListDao(): CustomListDao
    abstract fun seasonProgressDao(): SeasonProgressDao
    abstract fun collectionCacheDao(): CollectionCacheDao
    abstract fun sagaSizeDao(): SagaSizeDao
    abstract fun titleMetaCacheDao(): TitleMetaCacheDao

    companion object {
        private const val DATABASE_NAME = "cinelog_database"

        // Versions publiées avant l'écriture de la première migration
        // (MIGRATION_4_5) : aucun chemin n'existe pour elles, la base est
        // recréée. Ne jamais ajouter ici une version >= 4.
        private val VERSIONS_WITHOUT_MIGRATION = intArrayOf(1, 2, 3)

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = build(context, DATABASE_NAME)
                INSTANCE = instance
                instance
            }
        }

        // Configuration réelle du builder, exposée pour MigrationTest.
        // Pas de fallbackToDestructiveMigration() global ni sur
        // rétrogradation : une migration manquante, invalide ou une build
        // plus ancienne installée par-dessus lève une exception à
        // l'ouverture au lieu d'effacer journal, watchlist et listes.
        internal fun build(context: Context, name: String): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, name)
                .addMigrations(*ALL_MIGRATIONS)
                .fallbackToDestructiveMigrationFrom(dropAllTables = true, *VERSIONS_WITHOUT_MIGRATION)
                .build()
    }
}
