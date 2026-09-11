package com.jarvis.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        PendingOutboundEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class JarvisDatabase : RoomDatabase() {

    abstract fun dao(): JarvisDao

    companion object {
        @Volatile
        private var instance: JarvisDatabase? = null

        fun get(context: Context): JarvisDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    JarvisDatabase::class.java,
                    "jarvis.db",
                )
                    // Migration tests (Lane C) replace this with orchestrated migrations
                    // once a v2 schema exists; destructive fallback only while V1 schema is single-version.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
