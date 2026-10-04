package app.idl.domain.avatar

import app.idl.domain.AvatarConfig
import app.idl.domain.BaseForm
import app.idl.domain.Expression
import app.idl.domain.FaceStyle
import app.idl.domain.HeadAccessory
import app.idl.domain.Layer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceholderFrameTest {
    private val registry = coreRegistry()
    private val resolver = AvatarResolver(registry)

    @Test fun `a resolved blob keeps its base expression and brows`() {
        val resolved = resolver.resolve(AvatarRenderRequest(config(resting = "happy"), sizePx = 256))
        val frame = PlaceholderFrames.from(resolved, registry)
        assertEquals(BaseForm.BLOB, frame.config.baseForm)
        assertEquals(Expression.HAPPY, frame.config.expression)
        assertTrue(Layer.BROWS in frame.layers)
        assertTrue(Layer.EYES in frame.layers)
        assertTrue(frame.sceneDetail)
    }

    @Test fun `a migrated fox is still a fox`() {
        val v2 = LegacyAvatarMigration.migrate(AvatarConfig(baseForm = BaseForm.FOX, expression = Expression.SLEEPY), registry)
        val frame = PlaceholderFrames.from(resolver.resolve(AvatarRenderRequest(v2, sizePx = 256)), registry)
        assertEquals(BaseForm.FOX, frame.config.baseForm)
        assertEquals(Expression.SLEEPY, frame.config.expression)
    }

    @Test fun `a visor removes the eyes from the placeholder frame`() {
        val resolved = resolver.resolve(AvatarRenderRequest(config(head = "head_vr_headset"), sizePx = 256))
        val frame = PlaceholderFrames.from(resolved, registry)
        assertEquals(HeadAccessory.VR_HEADSET, frame.config.headAccessory)
        assertFalse(Layer.EYES in frame.layers)
        assertFalse(Layer.BROWS in frame.layers)
    }

    @Test fun `compact size drops the prop the resolver already removed`() {
        val resolved = resolver.resolve(
            AvatarRenderRequest(
                config(features = listOf("feature_blush")),
                target = RenderTarget.COMPACT_WIDGET,
                sizePx = 48,
            ),
        )
        val frame = PlaceholderFrames.from(resolved, registry)
        assertEquals(FaceStyle.CLASSIC, frame.config.faceStyle)
        assertFalse(frame.sceneDetail)
        assertTrue(Layer.EYES in frame.layers)
    }
}
