package com.jarvis.android.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/**
 * Lane C — Room schema migrations, exercised on a real SQLite engine
 * (sqlite-jdbc) so the exact SQL that runs on-device is what is tested.
 */
class CursorMigrationTest {

    private fun exec(conn: Connection, migrate: ((String) -> Unit) -> Unit) {
        conn.autoCommit = false
        migrate { sql -> conn.createStatement().use { it.execute(sql) } }
        conn.commit()
    }

    private fun columns(conn: Connection, table: String): List<String> =
        conn.createStatement().use { st ->
            st.executeQuery("PRAGMA table_info(`$table`)").let { rs ->
                buildList { while (rs.next()) add(rs.getString("name")) }
            }
        }

    private fun memoryDb(): Connection {
        Class.forName("org.sqlite.JDBC")
        return DriverManager.getConnection("jdbc:sqlite::memory:")
    }

    private fun Connection.createV1Tables() = createStatement().use { st ->
        // v1: conversations.lastCursor is INTEGER; pending_outbound has NO
        // attachmentIds column (that column arrived in v2 with AND-W6).
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
                PRIMARY KEY(`clientRequestId`)
            )
            """.trimIndent(),
        )
    }

    /**
     * PCB-R2: Long lastCursor → opaque string lastCursorToken without dropping
     * conversations, messages, or pending outbound.
     */
    @Test
    fun migration2to3PreservesRowsAndConvertsCursor() {
        memoryDb().use { conn ->
            conn.createV1Tables()
            // Bring pending_outbound up to v2 first (the input of MIGRATION_2_3).
            exec(conn) { JarvisDatabase.applyMigration1to2(it) }
            conn.createStatement().use { st ->
                st.execute("INSERT INTO conversations VALUES ('cA','Chat A',1,2,42)")
                st.execute("INSERT INTO conversations VALUES ('cB','Chat B',3,4,0)")
                st.execute("INSERT INTO messages VALUES (1,'cA','r1','user','hello','Pending',1,'')")
                st.execute("INSERT INTO pending_outbound VALUES ('r1','cA','hello',1,0,'')")
            }

            exec(conn) { JarvisDatabase.applyMigration2to3(it) }

            conn.createStatement().use { st ->
                val rs = st.executeQuery(
                    "SELECT conversationId, lastCursorToken, title FROM conversations ORDER BY conversationId",
                )
                assertTrue(rs.next())
                assertEquals("cA", rs.getString("conversationId"))
                assertEquals("42", rs.getString("lastCursorToken"))
                assertEquals("Chat A", rs.getString("title"))
                assertTrue(rs.next())
                assertEquals("cB", rs.getString("conversationId"))
                assertEquals("", rs.getString("lastCursorToken"))
                rs.close()

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
            assertTrue("lastCursorToken" in columns(conn, "conversations"))
            assertTrue("lastCursor" !in columns(conn, "conversations"))
        }
    }

    /**
     * Lane C: the entire v1 -> v2 -> v3 chain. v1 is reconstructed from the
     * first-shipped schema (pending_outbound without attachmentIds). Nothing is
     * lost: conversations, messages, pending outbound, cursor and attempts.
     */
    @Test
    fun migration1to2to3ChainPreservesEverything() {
        memoryDb().use { conn ->
            conn.createV1Tables()
            conn.createStatement().use { st ->
                st.execute("INSERT INTO conversations VALUES ('cA','Chat A',1,2,77)")
                st.execute("INSERT INTO messages VALUES (1,'cA','r1','user','hi','Completed',1,'')")
                st.execute("INSERT INTO pending_outbound VALUES ('r2','cA','queued',2,3)")
            }
            // v1 really lacked the column.
            assertTrue("attachmentIds" !in columns(conn, "pending_outbound"))

            exec(conn) { JarvisDatabase.applyMigration1to2(it) }

            // v2 shape: pending gained attachmentIds defaulting to "".
            val v2Pending = columns(conn, "pending_outbound")
            assertTrue("attachmentIds" in v2Pending)
            conn.createStatement().use { st ->
                val rs = st.executeQuery("SELECT attachmentIds, attempts, text FROM pending_outbound")
                assertTrue(rs.next())
                assertEquals("", rs.getString(1))
                assertEquals(3, rs.getInt(2))
                assertEquals("queued", rs.getString(3))
                rs.close()
            }

            exec(conn) { JarvisDatabase.applyMigration2to3(it) }

            // v3 shape: opaque cursor token, numeric cursor gone.
            val v3Conv = columns(conn, "conversations")
            assertTrue("lastCursorToken" in v3Conv)
            assertTrue("lastCursor" !in v3Conv)

            conn.createStatement().use { st ->
                val rs = st.executeQuery(
                    "SELECT lastCursorToken FROM conversations WHERE conversationId='cA'",
                )
                assertTrue(rs.next())
                assertEquals("77", rs.getString(1))
                rs.close()

                val msgs = st.executeQuery("SELECT COUNT(*) FROM messages")
                assertTrue(msgs.next())
                assertEquals(1, msgs.getInt(1))
                msgs.close()

                val pend = st.executeQuery("SELECT text, attempts, attachmentIds FROM pending_outbound")
                assertTrue(pend.next())
                assertEquals("queued", pend.getString(1))
                assertEquals(3, pend.getInt(2))
                assertEquals("", pend.getString(3))
                pend.close()
            }
        }
    }

    /**
     * v1 → v5: local pin/hide chrome is additive. Conversations, messages,
     * pending outbound, cursor token and drafts stay put. No destructive
     * fallback.
     */
    @Test
    fun migration1to5ChainPreservesConversationsMessagesPendingCursorAndDrafts() {
        memoryDb().use { conn ->
            conn.createV1Tables()
            conn.createStatement().use { st ->
                st.execute("INSERT INTO conversations VALUES ('cA','Chat A',1,2,77)")
                st.execute("INSERT INTO messages VALUES (1,'cA','r1','user','hi','Completed',1,'')")
                st.execute("INSERT INTO pending_outbound VALUES ('r2','cA','queued',2,3)")
            }
            exec(conn) { JarvisDatabase.applyMigration1to2(it) }
            exec(conn) { JarvisDatabase.applyMigration2to3(it) }
            exec(conn) { JarvisDatabase.applyMigration3to4(it) }
            conn.createStatement().use { st ->
                st.execute("INSERT INTO conversation_drafts VALUES ('cA','unsent',9)")
            }
            exec(conn) { JarvisDatabase.applyMigration4to5(it) }

            conn.createStatement().use { st ->
                val cursor = st.executeQuery(
                    "SELECT lastCursorToken, title FROM conversations WHERE conversationId='cA'",
                )
                assertTrue(cursor.next())
                assertEquals("77", cursor.getString(1))
                assertEquals("Chat A", cursor.getString(2))
                cursor.close()

                val messages = st.executeQuery("SELECT COUNT(*) FROM messages")
                assertTrue(messages.next())
                assertEquals(1, messages.getInt(1))
                messages.close()

                val pending = st.executeQuery("SELECT text, attempts, attachmentIds FROM pending_outbound")
                assertTrue(pending.next())
                assertEquals("queued", pending.getString(1))
                assertEquals(3, pending.getInt(2))
                assertEquals("", pending.getString(3))
                pending.close()

                val drafts = st.executeQuery("SELECT text FROM conversation_drafts WHERE conversationId='cA'")
                assertTrue(drafts.next())
                assertEquals("unsent", drafts.getString(1))
                drafts.close()

                val meta = st.executeQuery("SELECT COUNT(*) FROM conversation_local_meta")
                assertTrue(meta.next())
                assertEquals(0, meta.getInt(1))
                meta.close()
            }
            assertTrue("pinned" in columns(conn, "conversation_local_meta"))
            assertTrue("archived" in columns(conn, "conversation_local_meta"))
            assertTrue("localTitle" in columns(conn, "conversation_local_meta"))
            assertTrue("lastOpenedAtMs" in columns(conn, "conversation_local_meta"))
        }
    }
}
