package app.idl.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class ReactionRulesTest {
    private fun r(id: String, to: String = "me", at: java.time.Instant = T0, dismissed: java.time.Instant? = null) =
        Reaction(id, "ari", to, ReactionTemplate.COFFEE, at, at.plus(ReactionRules.TTL), dismissed)

    @Test fun `inbox shows only my active reactions newest first`() {
        val now = T0.plus(hours(2))
        val list = listOf(
            r("old", at = T0.minus(Duration.ofHours(30))), // expired
            r("dismissed", dismissed = T0.plusSeconds(5)),
            r("other", to = "someone_else"),
            r("a", at = T0),
            r("b", at = T0.plus(hours(1))),
        )
        assertEquals(listOf("b", "a"), ReactionRules.inbox(list, "me", now).map { it.id })
    }

    @Test fun `reactions expire after ttl`() {
        val x = r("x")
        assertTrue(x.isActive(T0.plus(ReactionRules.TTL).minusSeconds(1)))
        assertFalse(x.isActive(T0.plus(ReactionRules.TTL)))
    }

    @Test fun `dismiss is idempotent and keeps first timestamp`() {
        val once = ReactionRules.dismiss(r("x"), T0.plusSeconds(10))
        assertEquals(T0.plusSeconds(10), once.dismissedAt)
        assertFalse(once.isActive(T0.plusSeconds(11)))
        assertSame(once, ReactionRules.dismiss(once, T0.plusSeconds(99)))
    }

    @Test fun `only friends can react and spam is rate limited`() {
        assertFalse(ReactionRules.canSend(Relationship(isFriend = false), emptyList(), T0))
        assertFalse(ReactionRules.canSend(Relationship(isFriend = true, blocked = true), emptyList(), T0))
        val recent = (1..ReactionRules.PER_RECIPIENT_HOURLY_LIMIT).map { r("r$it", at = T0.minusSeconds(it * 60L)) }
        assertFalse(ReactionRules.canSend(Relationship(isFriend = true), recent, T0))
        assertTrue(ReactionRules.canSend(Relationship(isFriend = true), recent, T0.plus(hours(2))))
    }
}

class AvatarSpecTest {

    @Test fun `every expression has a distinct face`() {
        val faces = Expression.entries.map { AvatarSpec.face(it) }
        assertEquals(Expression.entries.size, faces.toSet().size)
    }

    @Test fun `layer order is fixed`() {
        val full = AvatarConfig(
            faceStyle = FaceStyle.BLUSHY, expression = Expression.SLEEPY, headAccessory = HeadAccessory.BEANIE,
            faceAccessory = FaceAccessory.GLASSES, bodyAccessory = BodyAccessory.HOODIE, handProp = Prop.TEA,
        )
        val plan = AvatarSpec.plan(full, 256, showAvailability = true, showActivity = true)
        assertEquals(Layer.entries.toList(), plan.layers)
        assertTrue(plan.sceneDetail)
    }

    @Test fun `small sizes drop decoration but keep expression and availability`() {
        val cfg = AvatarConfig(expression = Expression.SLEEPY, bodyAccessory = BodyAccessory.BLANKET, handProp = Prop.TEA, faceStyle = FaceStyle.FRECKLES)
        val plan = AvatarSpec.plan(cfg, 40, showAvailability = true, showActivity = true)
        assertTrue(Layer.EYES in plan.layers)
        assertTrue(Layer.MOUTH in plan.layers)
        assertTrue(Layer.FACE_EXTRA in plan.layers) // zzz is part of "sleepy"
        assertTrue(Layer.AVAILABILITY_BADGE in plan.layers)
        assertFalse(Layer.ACTIVITY_BADGE in plan.layers)
        assertFalse(Layer.PROP in plan.layers)
        assertFalse(Layer.BODY_ACCESSORY in plan.layers)
        assertFalse(Layer.FACE_STYLE in plan.layers)
        assertFalse(plan.sceneDetail)
    }

    @Test fun `plan is deterministic`() {
        val cfg = AvatarConfig(baseForm = BaseForm.ROBOT, expression = Expression.ANGRY)
        assertEquals(AvatarSpec.plan(cfg, 128, true, false), AvatarSpec.plan(cfg, 128, true, false))
    }
}

class WireFormatTest {

    @Test fun `envelope from the spec decodes`() {
        val json = """
            {
              "mood": "sleepy", "availability": "text_only", "intent": "ask_later",
              "activity": { "type": "vr", "label": "VRChat", "source": "vrcq", "joinable": false, "joinUrl": null },
              "visual": { "expression": "tired", "props": ["blanket_unknown", "tea"], "scene": "cozy_bedroom" },
              "audience": { "type": "circle", "ids": ["inner_circle"] },
              "startedAt": "2026-10-03T14:00:00Z", "expiresAt": "2026-10-04T14:00:00Z",
              "source": "manual", "priority": 100
            }
        """.trimIndent()
        val s = IdlJson.decodeFromString(PresenceState.serializer(), json)
        assertEquals(Mood.SLEEPY, s.mood)
        assertEquals(Availability.TEXT_ONLY, s.availability)
        assertEquals(ActivityType.VR, s.activity?.type)
        assertEquals(PresenceSource.VRCQ, s.activity?.source)
        assertEquals(Expression.TIRED, s.visual?.expression)
        assertEquals(listOf(Prop.NONE, Prop.TEA), s.visual?.props) // unknown value -> safe fallback
        assertEquals(Scene.COZY_BEDROOM, s.visual?.scene)
        assertEquals(T0.plus(Duration.ofDays(1)), s.expiresAt)
    }

    @Test fun `unknown enum values from a newer server fall back safely`() {
        assertEquals(Mood.NEUTRAL, Mood.Serializer.fromWire("ecstatic"))
        assertEquals(Availability.OFFLINE, Availability.Serializer.fromWire(null))
        assertEquals(Audience.ONLY_ME, Audience.Serializer.fromWire("public"))
    }

    @Test fun `presence view round trips`() {
        val v = PresenceView("u_ari", AvatarConfig(baseForm = BaseForm.FOX), mood = Mood.HAPPY, expiresAt = T0)
        val json = IdlJson.encodeToString(PresenceView.serializer(), v)
        assertTrue(json.contains("\"mood\":\"happy\""))
        assertEquals(v, IdlJson.decodeFromString(PresenceView.serializer(), json))
    }
}
