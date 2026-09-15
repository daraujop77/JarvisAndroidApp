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
         * v1 → v2: `pending_outbound` gained the `attachmentIds` column (AND-W6).
         * Recreate (not ALTER-with-default) so the resulting column carries no
         * SQL default, matching Room's compiled v2 schema exactly.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                applyMigration1to2(db::execSQL)
            }
        }

        fun applyMigration1to2(execSql: (String) -> Unit) {
            execSql(
                """
                CREATE TABLE IF NOT EXISTS `pending_outbound_new` (
                    `clientRequestId` TEXT NOT NULL,
                    `conversationId` TEXT NOT NULL,
                    `text` TEXT NOT NULL,
                    `createdAtMs` INTEGER NOT NULL,
                    `attempts` INTEGER NOT NULL,
                    `attachmentIds` TEXT NOT NULL,
                    PRIMARY KEY(`clientRequestId`)
                )
                """.trimIndent(),
            )
            execSql(
                """
                INSERT INTO `pending_outbound_new` (
                    `clientRequestId`, `conversationId`, `text`, `createdAtMs`, `attempts`, `attachmentIds`
                )
                SELECT
                    `clientRequestId`, `conversationId`, `text`, `createdAtMs`, `attempts`, ''
                FROM `pending_outbound`
                """.trimIndent(),
            )
            execSql("DROP TABLE `pending_outbound`")
            execSql("ALTER TABLE `pending_outbound_new` RENAME TO `pending_outbound`")
        }

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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}
