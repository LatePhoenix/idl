package app.idl.ui.avatar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.idl.IdlApp
import app.idl.domain.avatar.EditorDefaults
import app.idl.domain.avatar.EditorSession
import app.idl.domain.avatar.LocalEntitlements
import app.idl.ui.theme.IdlTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickCreatorTest {
    @get:Rule val rule = createComposeRule()

    @Test fun aFirstRunReachesASavedLookInUnderTwoMinutes() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IdlApp
        val registry = app.container.assetRegistry
        val session = EditorSession(EditorDefaults.starter(registry), registry)
        var saved = false
        val started = System.nanoTime()
        rule.setContent {
            var ui by remember { mutableStateOf(quickUi(session)) }
            var step by remember { mutableIntStateOf(0) }
            fun refresh() { ui = quickUi(session) }
            IdlTheme {
                QuickCreator(
                    state = ui,
                    step = step,
                    registry = registry,
                    entitlements = LocalEntitlements(registry),
                    onStep = { step = it },
                    onWear = { session.wear(it); refresh() },
                    onColor = { slot, hex -> session.setColor(slot, hex); refresh() },
                    onClear = {},
                    onFullEditor = {},
                    onSave = { saved = true },
                )
            }
        }
        rule.onNodeWithTag("swatch:skin_fair_peach").performScrollTo().performClick()
        rule.onNodeWithTag("quick-next").performClick()
        rule.onNodeWithTag("item:hair_bob").performScrollTo().performClick()
        rule.onNodeWithTag("swatch:hair_jet_black").performScrollTo().performClick()
        rule.onNodeWithTag("quick-next").performClick()
        rule.onNodeWithTag("item:top_crew_tee").performScrollTo().performClick()
        rule.onNodeWithTag("save").performClick()
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertTrue("first run took ${elapsedMs}ms", elapsedMs < 120_000)
        assertTrue(saved)
        assertEquals("#F0C9A8", session.configuration.colorOverrides["face.primary"])
        assertEquals("#1A1210", session.configuration.colorOverrides["hair.primary"])
        assertTrue(session.wornIds().contains("hair_bob"))
        assertTrue(session.wornIds().contains("top_crew_tee"))
    }

    private fun quickUi(session: EditorSession) = EditorUi(
        configuration = session.configuration,
        displayed = session.configuration,
        canUndo = session.canUndo,
        canRedo = session.canRedo,
        showingOpened = false,
        notice = null,
        tab = EditorTab.SKIN,
        blocked = false,
    )
}
