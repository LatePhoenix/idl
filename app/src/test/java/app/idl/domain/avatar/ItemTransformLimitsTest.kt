package app.idl.domain.avatar

import org.junit.Assert.assertEquals
import org.junit.Test

class ItemTransformLimitsTest {
    @Test fun `eyewear may nudge vertically only`() {
        val clamped = ItemTransformLimits.clamp(
            AssetCategory.FACE_ACCESSORY,
            ItemTransform(translateX = 40f, translateY = 100f, scale = 1.5f, rotationDeg = 12f, flipHorizontal = true),
        )
        assertEquals(ItemTransform(translateY = 24f), clamped)
        assertEquals(
            ItemTransform(translateY = -24f),
            ItemTransformLimits.clamp(AssetCategory.FACE_ACCESSORY, ItemTransform(translateY = -90f)),
        )
    }

    @Test fun `headwear may scale within a narrow band`() {
        assertEquals(
            ItemTransform(scale = 1.1f),
            ItemTransformLimits.clamp(AssetCategory.HEAD_ACCESSORY, ItemTransform(scale = 2f, translateY = 8f)),
        )
        assertEquals(
            ItemTransform(scale = 0.9f),
            ItemTransformLimits.clamp(AssetCategory.HEAD_ACCESSORY, ItemTransform(scale = 0.5f)),
        )
    }

    @Test fun `other categories drop transforms`() {
        assertEquals(
            ItemTransform(),
            ItemTransformLimits.clamp(AssetCategory.HAIR, ItemTransform(translateX = 4f, scale = 1.2f)),
        )
    }
}
