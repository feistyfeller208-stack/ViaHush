package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.User
import com.example.data.model.ContactRelation
import com.example.data.model.StatusStory
import com.example.data.model.StatusReply
import com.example.data.dao.UserDao
import com.example.data.dao.ContactDao
import com.example.data.dao.StatusDao
import com.example.data.dao.StatusReplyDao

@Database(
    entities = [
        User::class,
        ContactRelation::class,
        StatusStory::class,
        StatusReply::class
    ],
    version = 1,
    exportSchema = false
)
abstract class HushDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun contactDao(): ContactDao
    abstract fun statusDao(): StatusDao
    abstract fun statusReplyDao(): StatusReplyDao

    companion object {
        @Volatile
        private var INSTANCE: HushDatabase? = null

        fun getDatabase(context: Context): HushDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HushDatabase::class.java,
                    "hush_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
