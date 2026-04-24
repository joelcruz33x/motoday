package com.example.motoday.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.motoday.data.local.dao.ContactDao
import com.example.motoday.data.local.dao.MaintenanceDao
import com.example.motoday.data.local.dao.PassportDao
import com.example.motoday.data.local.dao.PostDao
import com.example.motoday.data.local.dao.RideDao
import com.example.motoday.data.local.dao.SeenStoryDao
import com.example.motoday.data.local.dao.StoryDao
import com.example.motoday.data.local.dao.UserDao
import com.example.motoday.data.local.entities.ContactEntity
import com.example.motoday.data.local.entities.MaintenanceEntity
import com.example.motoday.data.local.entities.PassportStampEntity
import com.example.motoday.data.local.entities.PostEntity
import com.example.motoday.data.local.entities.RideEntity
import com.example.motoday.data.local.entities.SeenStoryEntity
import com.example.motoday.data.local.entities.StoryEntity
import com.example.motoday.data.local.entities.UserEntity

@Database(entities = [PostEntity::class, RideEntity::class, UserEntity::class, ContactEntity::class, MaintenanceEntity::class, PassportStampEntity::class, StoryEntity::class, SeenStoryEntity::class], version = 19)
abstract class AppDatabase : RoomDatabase() {
    abstract fun postDao(): PostDao
    abstract fun rideDao(): RideDao
    abstract fun userDao(): UserDao
    abstract fun contactDao(): ContactDao
    abstract fun maintenanceDao(): MaintenanceDao
    abstract fun passportDao(): PassportDao
    abstract fun storyDao(): StoryDao
    abstract fun seenStoryDao(): SeenStoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "motoday_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
