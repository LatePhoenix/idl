package app.idl.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.idl.domain.BaseForm
import app.idl.domain.wire
import app.idl.domain.avatar.AssetPacks
import app.idl.domain.avatar.AvatarConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Schema 2 avatar blobs become schema 3 teardrops. A newer schema is left byte-for-byte so an
 * older app cannot drop fields it does not know.
 */
@RunWith(AndroidJUnit4::class)
class Migration2To3Test {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val registry = AssetPacks.registry { path ->
        context.assets.open(path).bufferedReader().use { it.readText() }
    }

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        IdlDatabase::class.java,
    )

    @Test fun migrate2To3RewritesLegacyAvatarsAndLeavesANewerSchema() {
        val newer = """{"baseAssetId":"base_blob","paletteAssetId":"palette_sunny","schemaVersion":4,"futureKnob":true}"""
        helper.createDatabase(TEST_DB, 2).apply {
            BaseForm.entries.forEach { form ->
                execSQL(
                    "INSERT INTO avatars (userId, json) VALUES (?, ?)",
                    arrayOf(form.wire, """{"baseForm":"${form.wire}"}"""),
                )
            }
            execSQL(
                "INSERT INTO avatars (userId, json) VALUES (?, ?)",
                arrayOf(
                    "schema3",
                    """{"baseAssetId":"base_critter","paletteAssetId":"palette_sunny","schemaVersion":3,"signatureFeatureAssetIds":["feature_ears_cat"],"itemIds":{"hair":["hair_bob"]}}""",
                ),
            )
            execSQL("INSERT INTO avatars (userId, json) VALUES (?, ?)", arrayOf("newer", newer))
            execSQL(
                "INSERT INTO session (id, userId, username, displayName, invisible) VALUES (?, ?, ?, ?, ?)",
                arrayOf(0, "u_me", "me", "Me", 0),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 3, true, IdlDatabase.migration2To3(registry))
        BaseForm.entries.forEach { form ->
            val recipe = AvatarConfiguration.decode(
                migrated.string("SELECT json FROM avatars WHERE userId = '${form.wire}'"),
                registry.baseFamilies,
            )
            assertEquals(form.name, "base_teardrop", recipe.baseAssetId)
            assertEquals(form.name, 3, recipe.schemaVersion)
        }
        val pixel = AvatarConfiguration.decode(
            migrated.string("SELECT json FROM avatars WHERE userId = 'pixel'"),
            registry.baseFamilies,
        )
        assertEquals("eyefam_pixel", pixel.eyeFamilyAssetId)
        assertEquals("frame_pixel", pixel.defaultFrameAssetId)

        val kept = AvatarConfiguration.decode(
            migrated.string("SELECT json FROM avatars WHERE userId = 'schema3'"),
            registry.baseFamilies,
        )
        assertEquals("base_teardrop", kept.baseAssetId)
        assertEquals(listOf("hair_bob"), kept.itemIds["hair"])
        assertFalse(kept.signatureFeatureAssetIds.contains("feature_ears_cat"))

        assertEquals(newer, migrated.string("SELECT json FROM avatars WHERE userId = 'newer'"))
        assertEquals("Me", migrated.string("SELECT displayName FROM session WHERE userId = 'u_me'"))
        migrated.close()
    }

    private fun SupportSQLiteDatabase.string(sql: String): String =
        query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertFalse(cursor.moveToNext())
            cursor.moveToFirst()
            cursor.getString(0)
        }

    private companion object {
        const val TEST_DB = "migration-2-3"
    }
}
