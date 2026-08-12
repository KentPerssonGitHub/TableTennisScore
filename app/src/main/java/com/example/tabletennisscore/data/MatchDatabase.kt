package com.example.tabletennisscore.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [MatchResult::class], version = 1, exportSchema = false)
abstract class MatchDatabase : RoomDatabase() {

    abstract fun matchResultDao(): MatchResultDao

    companion object {
        @Volatile
        private var INSTANCE: MatchDatabase? = null

        fun getInstance(context: Context): MatchDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MatchDatabase::class.java,
                    "match_database",
                ).build().also { INSTANCE = it }
            }
    }
}

