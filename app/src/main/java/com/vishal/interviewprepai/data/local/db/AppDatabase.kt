package com.vishal.interviewprepai.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vishal.interviewprepai.data.local.dao.QuestionDao
import com.vishal.interviewprepai.data.local.dao.ResumeDao
import com.vishal.interviewprepai.data.local.dao.UserDao
import com.vishal.interviewprepai.data.local.entity.QuestionEntity
import com.vishal.interviewprepai.data.local.entity.ResumeEntity
import com.vishal.interviewprepai.data.local.entity.UserEntity

@Database(
    entities = [UserEntity::class, ResumeEntity::class, QuestionEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun resumeDao(): ResumeDao
    abstract fun questionDao(): QuestionDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE questions ADD COLUMN roundType TEXT")
                database.execSQL("ALTER TABLE questions ADD COLUMN difficultyLevel TEXT")
                database.execSQL("ALTER TABLE questions ADD COLUMN domain TEXT")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            val existing = INSTANCE
            if (existing != null) return existing
            return synchronized(this) {
                val again = INSTANCE
                if (again != null) again
                else {
                    Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "interview_prep.db",
                    )
                        .addMigrations(MIGRATION_1_2)
                        .build()
                        .also { INSTANCE = it }
                }
            }
        }
    }
}
