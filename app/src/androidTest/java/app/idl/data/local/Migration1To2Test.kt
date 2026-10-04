package app.idl.data.local

import android.database.Cursor
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Schema 1 rows must survive [IdlDatabase.MIGRATION_1_2], and the Charge tables arrive empty
 * with the nullable tether timestamp defaulting to null.
 */
@RunWith(AndroidJUnit4::class)
class Migration1To2Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        IdlDatabase::class.java,
    )

    @Test fun migrate1To2KeepsRowsAndAddsEmptyEconomyTables() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO session (id, userId, username, displayName, invisible) VALUES (?, ?, ?, ?, ?)",
                arrayOf(0, "u_me", "me", "Me", 0),
            )
            execSQL(
                "INSERT INTO avatars (userId, json) VALUES (?, ?)",
                arrayOf("u_me", """{"baseForm":"BLOB"}"""),
            )
            execSQL(
                "INSERT INTO own_presence (source, json, pendingSync) VALUES (?, ?, ?)",
                arrayOf("manual", """{"mood":"sleepy"}""", 1),
            )
            execSQL(
                "INSERT INTO friends (userId, username, displayName, status, isCloseFriend) VALUES (?, ?, ?, ?, ?)",
                arrayOf("u_ari", "ari", "Ari", "accepted", 1),
            )
            execSQL(
                "INSERT INTO friend_presence (userId, json, fetchedAtMs) VALUES (?, ?, ?)",
                arrayOf("u_ari", """{"userId":"u_ari"}""", 1_700_000_000_000L),
            )
            execSQL(
                "INSERT INTO reactions (id, senderId, recipientId, template, createdAtMs, expiresAtMs, dismissedAtMs) VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf("rx1", "u_ari", "u_me", "wave", 10L, 20L, null),
            )
            execSQL(
                "INSERT INTO privacy_rules (id, json) VALUES (?, ?)",
                arrayOf(0, """{"mood":"friends"}"""),
            )
            execSQL(
                "INSERT INTO widget_subscriptions (appWidgetId, kind, friendUserId) VALUES (?, ?, ?)",
                arrayOf(7, "solo", "u_ari"),
            )
            execSQL(
                "INSERT INTO sync_state (id, lastSuccessMs, lastAttemptMs, lastError) VALUES (?, ?, ?, ?)",
                arrayOf(0, 5L, 6L, null),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 2, true, IdlDatabase.MIGRATION_1_2)
        migrated.query("SELECT userId, username, displayName, invisible FROM session").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("u_me", cursor.getString(0))
            assertEquals("me", cursor.getString(1))
            assertEquals("Me", cursor.getString(2))
            assertEquals(0, cursor.getInt(3))
        }
        assertEquals("""{"baseForm":"BLOB"}""", migrated.string("SELECT json FROM avatars WHERE userId = 'u_me'"))
        assertEquals(1, migrated.int("SELECT pendingSync FROM own_presence WHERE source = 'manual'"))
        assertEquals("Ari", migrated.string("SELECT displayName FROM friends WHERE userId = 'u_ari'"))
        assertEquals(1, migrated.int("SELECT isCloseFriend FROM friends WHERE userId = 'u_ari'"))
        assertEquals(1_700_000_000_000L, migrated.long("SELECT fetchedAtMs FROM friend_presence WHERE userId = 'u_ari'"))
        assertTrue(migrated.isNull("SELECT dismissedAtMs FROM reactions WHERE id = 'rx1'"))
        assertEquals("""{"mood":"friends"}""", migrated.string("SELECT json FROM privacy_rules WHERE id = 0"))
        assertEquals("u_ari", migrated.string("SELECT friendUserId FROM widget_subscriptions WHERE appWidgetId = 7"))
        assertTrue(migrated.isNull("SELECT lastError FROM sync_state WHERE id = 0"))

        assertEquals(0, migrated.int("SELECT COUNT(*) FROM user_economy"))
        assertEquals(0, migrated.int("SELECT COUNT(*) FROM friend_tethers"))
        migrated.execSQL(
            "INSERT INTO friend_tethers (friendUserId, hasLocalWidgetInstalled, hasRemoteWidgetInstalled) VALUES (?, ?, ?)",
            arrayOf("u_ari", 0, 0),
        )
        assertTrue(migrated.isNull("SELECT tetherActivatedEpochMs FROM friend_tethers WHERE friendUserId = 'u_ari'"))
        assertEquals(0, migrated.int("SELECT hasLocalWidgetInstalled FROM friend_tethers WHERE friendUserId = 'u_ari'"))
        assertEquals(0, migrated.int("SELECT hasRemoteWidgetInstalled FROM friend_tethers WHERE friendUserId = 'u_ari'"))
        migrated.close()
    }

    private fun SupportSQLiteDatabase.string(sql: String) = scalar(sql) { getString(0) }

    private fun SupportSQLiteDatabase.int(sql: String) = scalar(sql) { getInt(0) }

    private fun SupportSQLiteDatabase.long(sql: String) = scalar(sql) { getLong(0) }

    private fun SupportSQLiteDatabase.isNull(sql: String) = scalar(sql) { isNull(0) }

    private fun <T> SupportSQLiteDatabase.scalar(sql: String, read: Cursor.() -> T): T =
        query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertFalse(cursor.moveToNext())
            cursor.moveToFirst()
            cursor.read()
        }

    private companion object {
        const val TEST_DB = "migration-1-2"
    }
}
