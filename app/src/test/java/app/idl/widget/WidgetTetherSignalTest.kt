package app.idl.widget

import app.idl.avatar.AvatarBadges
import app.idl.domain.ActivityType
import app.idl.domain.Availability
import app.idl.domain.AvatarConfig
import app.idl.domain.Mood
import app.idl.domain.PresenceView
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetTetherSignalTest {
    @Test fun `no widget model or badge carries a per-friend tether`() {
        assertNull(WidgetModel::class.java.declaredFields.find { it.name == "resonating" })
        assertNull(AvatarBadges::class.java.declaredFields.find { it.name == "resonating" })
        val view = PresenceView(
            userId = "ari",
            identity = app.idl.domain.avatar.AvatarConfiguration(
                baseAssetId = "base_teardrop",
                paletteAssetId = "palette_sunny",
            ),
            availability = Availability.TEXT_ONLY,
            mood = Mood.SLEEPY,
        )
        val models = listOf(
            WidgetModel(title = "Ari", friendView = view, availability = Availability.TEXT_ONLY, mood = Mood.SLEEPY, deepLink = "idl://friend/ari"),
            WidgetModel(title = "You", selfPresence = null, invisible = true, avatarDescription = "Orb avatar, sleepy", deepLink = "idl://status"),
            WidgetModel(title = "Choose a friend", deepLink = "idl://widgets"),
        )
        models.forEach { model ->
            val text = model.contentDescription.lowercase()
            listOf("resonat", "tether", "pinned").forEach { word ->
                assertFalse("${model.title} description contains $word: $text", text.contains(word))
            }
        }
        val badges = AvatarBadges(Availability.BUSY, ActivityType.VR)
        assertFalse(badges.toString().lowercase().contains("resonat"))
    }
}
