package app.idl.domain.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementsTest {
    private val registry = coreRegistry()
    private val premiumId = "frame_sticker"

    @Test fun `free items are always owned`() {
        val entitlements = LocalEntitlements(registry)
        assertTrue(entitlements.owns("base_teardrop"))
        assertTrue(entitlements.owns("hair_short_crop"))
        assertTrue(entitlements.owns("palette_sunny"))
    }

    @Test fun `premium items are locked until granted or unlock-all`() {
        val entitlements = LocalEntitlements(registry)
        assertEquals(AssetTier.PREMIUM, registry.asset(premiumId)!!.tier)
        assertFalse(entitlements.owns(premiumId))

        entitlements.grant(premiumId)
        assertTrue(entitlements.owns(premiumId))
        entitlements.revoke(premiumId)
        assertFalse(entitlements.owns(premiumId))

        entitlements.unlockAll = true
        assertTrue(entitlements.owns(premiumId))
    }

    @Test fun `prepareForWrite refuses unowned premium then allows after unlock`() {
        val recipe = AvatarConfiguration(
            baseAssetId = "base_teardrop",
            paletteAssetId = "palette_sunny",
            defaultFrameAssetId = premiumId,
            packId = "emoji_core",
            packVersion = 2,
            familyId = "teardrop",
        )
        val entitlements = LocalEntitlements(registry)
        val blocked = recipe.prepareForWrite(registry.baseFamilies, registry, entitlements)
        assertTrue(blocked is AvatarWrite.NeedsEntitlement)
        assertEquals(listOf(premiumId), (blocked as AvatarWrite.NeedsEntitlement).assetIds)

        entitlements.unlockAll = true
        val allowed = recipe.prepareForWrite(registry.baseFamilies, registry, entitlements)
        assertTrue(allowed is AvatarWrite.Ready)
        assertEquals(premiumId, (allowed as AvatarWrite.Ready).configuration.defaultFrameAssetId)
    }

    @Test fun `prepareForWrite without entitlements still migrates for cache rewrites`() {
        val recipe = AvatarConfiguration(
            baseAssetId = "base_teardrop",
            paletteAssetId = "palette_sunny",
            defaultFrameAssetId = premiumId,
            schemaVersion = 2,
            familyId = "",
        )
        val write = recipe.prepareForWrite(registry.baseFamilies)
        assertTrue(write is AvatarWrite.Ready)
    }
}
