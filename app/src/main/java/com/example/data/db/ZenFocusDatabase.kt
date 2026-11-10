package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.SessionLogDao
import com.example.data.dao.TaskDao
import com.example.data.model.SessionLogEntity
import com.example.data.model.TaskEntity

@Database(entities = [TaskEntity::class, SessionLogEntity::class], version = 2, exportSchema = false)
abstract class ZenFocusDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun sessionLogDao(): SessionLogDao

    companion object {
        @Volatile
        private var INSTANCE: ZenFocusDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN priority INTEGER NOT NULL DEFAULT 1")
            }
        }

        fun getDatabase(context: Context): ZenFocusDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ZenFocusDatabase::class.java,
                    "zenfocus_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
