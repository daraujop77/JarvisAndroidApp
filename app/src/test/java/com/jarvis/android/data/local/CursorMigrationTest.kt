package com.jarvis.android.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.sql.DriverManager

/**
 * PCB-R2: Long lastCursor → opaque string lastCursorToken without dropping
 * conversations, messages, or pending outbound.
 */
class CursorMigrationTest {

    @Test
    fun migration2to3PreservesRowsAndConvertsCursor() {
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite::memory:").use { conn ->
            conn.createStatement().use { st ->
                st.execute(
                    """
                    CREATE TABLE `conversations` (
                        `conversationId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `createdAtMs` INTEGER NOT NULL,
                        `updatedAtMs` INTEGER NOT NULL,
                        `lastCursor` INTEGER NOT NULL,
                        PRIMARY KEY(`conversationId`)
                    )
                    """.trimIndent(),
                )
                st.execute(
                    """
                    CREATE TABLE `messages` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `conversationId` TEXT NOT NULL,
                        `clientRequestId` TEXT NOT NULL,
                        `role` TEXT NOT NULL,
                        `text` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `createdAtMs` INTEGER NOT NULL,
                        `attachmentIds` TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
                st.execute(
                    """
                    CREATE TABLE `pending_outbound` (
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
                st.execute("INSERT INTO conversations VALUES ('cA','Chat A',1,2,42)")
                st.execute("INSERT INTO conversations VALUES ('cB','Chat B',3,4,0)")
                st.execute("INSERT INTO messages VALUES (1,'cA','r1','user','hello','Pending',1,'')")
                st.execute("INSERT INTO pending_outbound VALUES ('r1','cA','hello',1,0,'')")
            }

            conn.autoCommit = false
            JarvisDatabase.applyMigration2to3 { sql -> conn.createStatement().use { it.execute(sql) } }
            conn.commit()

            conn.createStatement().use { st ->
                val rs = st.executeQuery("SELECT conversationId, lastCursorToken, title FROM conversations ORDER BY conversationId")
                assertTrue(rs.next())
                assertEquals("cA", rs.getString("conversationId"))
                assertEquals("42", rs.getString("lastCursorToken"))
                assertEquals("Chat A", rs.getString("title"))
                assertTrue(rs.next())
                assertEquals("cB", rs.getString("conversationId"))
                assertEquals("", rs.getString("lastCursorToken"))
                rs.close()

                val cols = st.executeQuery("PRAGMA table_info(conversations)")
                val names = mutableListOf<String>()
                while (cols.next()) names += cols.getString("name")
                cols.close()
                assertTrue("lastCursorToken" in names)
                assertTrue("lastCursor" !in names)

                val messages = st.executeQuery("SELECT COUNT(*) FROM messages")
                assertTrue(messages.next())
                assertEquals(1, messages.getInt(1))
                messages.close()

                val pending = st.executeQuery("SELECT conversationId, text FROM pending_outbound")
                assertTrue(pending.next())
                assertEquals("cA", pending.getString(1))
                assertEquals("hello", pending.getString(2))
                pending.close()
            }
        }
    }
}
