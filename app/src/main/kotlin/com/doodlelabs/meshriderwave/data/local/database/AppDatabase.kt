/*
 * Mesh Rider Wave - Room Database
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * SQLite database for offline message queue, call history, and local data
 */

package com.doodlelabs.meshriderwave.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        OfflineMessageEntity::class,
        CallHistoryEntity::class,
        LocationHistoryEntity::class,
        SOSAlertEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun offlineMessageDao(): OfflineMessageDao
    abstract fun callHistoryDao(): CallHistoryDao
    abstract fun locationHistoryDao(): LocationHistoryDao
    abstract fun sosAlertDao(): SOSAlertDao

    companion object {
        private const val DATABASE_NAME = "meshrider_wave.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
