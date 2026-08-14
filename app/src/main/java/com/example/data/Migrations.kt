package com.example.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit Room migrations, one per version bump. Adding a migration here
 * (instead of relying on fallbackToDestructiveMigration) lets existing users
 * keep their local data — their logs, watchlist, ratings, etc. — when the
 * app updates the database schema.
 *
 * Rule of thumb: every time AppDatabase's `version` increases, add a new
 * `MIGRATION_x_y` here describing the exact SQL change, and register it in
 * AppDatabase's `.addMigrations(...)`. If a version bump ships without a
 * matching migration, fallbackToDestructiveMigration() silently wipes the
 * local database for anyone updating from that version.
 */

// v4 -> v5: adds the saga_size_cache table (total film count per TMDB saga,
// used to show the "vue en entier" badge on grouped saga cards).
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `saga_size_cache` (
                `collectionId` INTEGER NOT NULL,
                `totalFilms` INTEGER NOT NULL,
                PRIMARY KEY(`collectionId`)
            )
            """.trimIndent()
        )
    }
}
// v5 -> v6: ajoute les colonnes de tri/filtre a la table watchlist
// (annee de sortie, genres, note communaute). Toutes nullables : les
// entrees existantes les gardent a NULL jusqu'a ce que le code les
// re-remplisse progressivement depuis l'API.
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `watchlist` ADD COLUMN `titleYear` TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE `watchlist` ADD COLUMN `titleGenres` TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE `watchlist` ADD COLUMN `titleVoteAverage` REAL DEFAULT NULL")
    }
}


// v6 -> v7: ajoute la table title_meta_cache pour persister le cache de
// metadonnees enrichies du profil (genres, studio/realisateur, note, duree).
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `title_meta_cache` (
                `titleId` TEXT NOT NULL,
                `genres` TEXT NOT NULL DEFAULT '',
                `studioOrDirector` TEXT DEFAULT NULL,
                `voteAverage` REAL NOT NULL DEFAULT 0,
                `runtime` INTEGER DEFAULT NULL,
                PRIMARY KEY(`titleId`)
            )
            """.trimIndent()
        )
    }
}

// v7 -> v8: ajoute un index sur titleId dans la table log_entries
// pour accelerer les requetes getLogsForTitle (fiche detail).
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_log_entries_titleId` ON `log_entries` (`titleId`)")
    }
}

// v8 -> v9: ajoute les index sur custom_list_titles (listId, orderIndex / titleId) et watchlist (collectionId),
// et ajoute la colonne cachedAt sur title_meta_cache et collection_cache pour la strategie TTL.
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_custom_list_titles_listId_orderIndex` ON `custom_list_titles` (`listId`, `orderIndex`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_custom_list_titles_titleId` ON `custom_list_titles` (`titleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_watchlist_collectionId` ON `watchlist` (`collectionId`)")
        db.execSQL("ALTER TABLE `title_meta_cache` ADD COLUMN `cachedAt` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `collection_cache` ADD COLUMN `cachedAt` INTEGER NOT NULL DEFAULT 0")
    }
}

