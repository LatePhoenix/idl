package app.idl

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.idl.domain.Availability
import app.idl.domain.AvatarConfig
import app.idl.domain.ChargeState
import app.idl.domain.Mood
import app.idl.domain.QuickState
import app.idl.domain.StatusDraft
import app.idl.ui.status.StatusDeckContent
import app.idl.ui.theme.IdlTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    private fun setUp(charge: ChargeState = ChargeState.EMPTY) {
        rule.setContent {
            var draft by remember { mutableStateOf(StatusDraft()) }
            IdlTheme {
                StatusDeckContent(
                    draft = draft,
                    baseAvatar = AvatarConfig(),
                    invisible = false,
                    message = null,
                    charge = charge,
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

    @Test fun quickStatesStayTheFirstActionableRow() {
        setUp(ChargeState(currentCharge = 40, hourlyRate = 28, activeTethers = 3))
        val quickTop = rule.onNodeWithTag("quick:${QuickState.ALL.first().id}").fetchSemanticsNode().boundsInRoot.top
        val tagged = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes().mapNotNull { node ->
            val tag = node.config.getOrNull(SemanticsProperties.TestTag) ?: return@mapNotNull null
            val bounds = node.boundsInRoot
            if (bounds.height <= 0f || bounds.width <= 0f) return@mapNotNull null
            Triple(tag, bounds.top, bounds)
        }
        val first = tagged.minBy { it.second }
        val dump = tagged.joinToString { "${it.first}@${it.second}" }
        assertTrue("first action was ${first.first}; quickTop=$quickTop; $dump", first.first.startsWith("quick:"))
        assertTrue(
            "a control sits above the quick states; quickTop=$quickTop; $dump",
            tagged.filter { !it.first.startsWith("quick:") }.all { it.second > quickTop + 0.5f },
        )
        val charge = rule.onNodeWithTag("charge")
        charge.assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        rule.onNodeWithText("40").fetchSemanticsNode()
        rule.onNodeWithText("Earning").fetchSemanticsNode()
        val shown = shownText(charge.fetchSemanticsNode())
        assertFalse(shown.contains("28"))
        assertFalse(shown.contains("/hr"))
        assertFalse(shown.contains("tether", ignoreCase = true))
        assertFalse(shown.contains("per hour"))
        val image = charge.captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "idl-charge-banner.png")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        val out = context.contentResolver.openOutputStream(uri!!)!!
        out.use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Log.i("idl.charge.shot", uri.toString())
    }

    @Test fun quietBalanceSaysNotEarning() {
        setUp(ChargeState(currentCharge = 12, hourlyRate = 0, activeTethers = 0))
        rule.onNodeWithText("12").fetchSemanticsNode()
        rule.onNodeWithText("Not earning").fetchSemanticsNode()
        val charge = rule.onNodeWithTag("charge")
        val shown = shownText(charge.fetchSemanticsNode())
        assertFalse(shown.contains("/hr"))
        assertFalse(shown.contains("tether", ignoreCase = true))
    }

    private fun shownText(node: androidx.compose.ui.semantics.SemanticsNode): String {
        val text = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ")
        val desc = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")
        return "$text $desc"
    }

    @Test fun invisibleToggle() {
        setUp()
        rule.onNodeWithTag("invisible").performScrollTo().performClick()
        assertEquals(true, invisible)
    }
}
