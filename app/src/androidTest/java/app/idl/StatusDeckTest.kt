package app.idl

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.idl.domain.Availability
import app.idl.domain.AvatarConfig
import app.idl.domain.Mood
import app.idl.domain.QuickState
import app.idl.domain.StatusDraft
import app.idl.ui.status.StatusDeckContent
import app.idl.ui.theme.IdlTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration

@RunWith(AndroidJUnit4::class)
class StatusDeckTest {
    @get:Rule val rule = createComposeRule()

    private var shared: StatusDraft? = null
    private var quick: QuickState? = null
    private var invisible: Boolean? = null

    private fun setUp() {
        rule.setContent {
            var draft by remember { mutableStateOf(StatusDraft()) }
            IdlTheme {
                StatusDeckContent(
                    draft = draft,
                    baseAvatar = AvatarConfig(),
                    invisible = false,
                    message = null,
                    onDraftChange = { draft = it },
                    onQuick = { quick = it },
                    onShare = { shared = draft },
                    onClear = {},
                    onInvisible = { invisible = it },
                )
            }
        }
    }

    @Test fun composeCustomStatusAndShare() {
        setUp()
        rule.onNodeWithTag("share").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("mood:${Mood.SLEEPY.emoji} ${Mood.SLEEPY.label}").performScrollTo().performClick()
        rule.onNodeWithTag("availability:${Availability.TEXT_ONLY.emoji} ${Availability.TEXT_ONLY.label}").performScrollTo().performClick()
        rule.onNodeWithTag("note").performScrollTo().performTextInput("napping, text me")
        rule.onNodeWithTag("duration:8h").performScrollTo().performClick()
        rule.onNodeWithTag("share").performScrollTo().assertIsEnabled().performClick()

        val d = shared!!
        assertEquals(Mood.SLEEPY, d.mood)
        assertEquals(Availability.TEXT_ONLY, d.availability)
        assertEquals("napping, text me", d.note)
        assertEquals(Duration.ofHours(8), d.duration)
    }

    @Test fun quickStateIsOneTap() {
        setUp()
        rule.onNodeWithTag("quick:sleepy").performClick()
        assertEquals("sleepy", quick?.id)
    }

    @Test fun invisibleToggle() {
        setUp()
        rule.onNodeWithTag("invisible").performScrollTo().performClick()
        assertEquals(true, invisible)
    }
}
