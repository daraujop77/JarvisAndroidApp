package com.jarvis.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        PendingOutboundEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class JarvisDatabase : RoomDatabase() {

    abstract fun dao(): JarvisDao

    companion object {
        @Volatile
        private var instance: JarvisDatabase? = null

        /**
         * PCB-R2: Long cursor → opaque string token. Conversations, messages,
         * and pending outbound are preserved. Numeric `0` becomes empty token.
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                applyMigration2to3(db::execSQL)
            }
        }

        /** Shared SQL so the JVM migration test and Room stay in lockstep. */
        fun applyMigration2to3(execSql: (String) -> Unit) {
            execSql(
                """
                CREATE TABLE IF NOT EXISTS `conversations_new` (
                    `conversationId` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `createdAtMs` INTEGER NOT NULL,
                    `updatedAtMs` INTEGER NOT NULL,
                    `lastCursorToken` TEXT NOT NULL,
                    PRIMARY KEY(`conversationId`)
                )
                """.trimIndent(),
            )
            execSql(
                """
                INSERT INTO `conversations_new` (
                    `conversationId`, `title`, `createdAtMs`, `updatedAtMs`, `lastCursorToken`
                )
                SELECT
                    `conversationId`,
                    `title`,
                    `createdAtMs`,
                    `updatedAtMs`,
                    CASE
                        WHEN `lastCursor` IS NULL OR `lastCursor` = 0 THEN ''
                        ELSE CAST(`lastCursor` AS TEXT)
                    END
                FROM `conversations`
                """.trimIndent(),
            )
            execSql("DROP TABLE `conversations`")
            execSql("ALTER TABLE `conversations_new` RENAME TO `conversations`")
            execSql(
                "CREATE INDEX IF NOT EXISTS `index_conversations_updatedAtMs` ON `conversations` (`updatedAtMs`)",
            )
        }

        fun get(context: Context): JarvisDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    JarvisDatabase::class.java,
                    "jarvis.db",
                )
                    .addMigrations(MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}
