package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
    exportSchema = false
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
