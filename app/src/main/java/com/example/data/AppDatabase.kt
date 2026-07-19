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
        DbCollectionCache::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun logDao(): LogDao
    abstract fun watchlistDao(): WatchlistDao
    abstract fun customListDao(): CustomListDao
    abstract fun seasonProgressDao(): SeasonProgressDao
    abstract fun collectionCacheDao(): CollectionCacheDao

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
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
