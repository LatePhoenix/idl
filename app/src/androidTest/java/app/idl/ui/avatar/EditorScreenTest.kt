package app.idl.ui.avatar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.idl.IdlApp
import app.idl.domain.avatar.EditorDefaults
import app.idl.domain.avatar.EditorSession
import app.idl.domain.avatar.LocalEntitlements
import app.idl.domain.avatar.saveRefusal
import app.idl.ui.theme.IdlTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun changeHairUndoAndRefuseALockedItemByItsLabel() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IdlApp
        val registry = app.container.assetRegistry
        val entitlements = LocalEntitlements(registry)
        val session = EditorSession(EditorDefaults.starter(registry), registry)
        var notice: String? = null
        rule.setContent {
            var ui by remember { mutableStateOf(ui(session, null)) }
            fun refresh(message: String? = ui.notice) {
                ui = ui(session, message)
            }
            IdlTheme {
                EditorBody(
                    state = ui,
                    registry = registry,
                    entitlements = entitlements,
                    firstRun = true,
                    onTab = { ui = ui.copy(tab = it) },
                    onWear = { session.wear(it); refresh(null) },
                    onExpression = { session.setExpression(it); refresh(null) },
                    onColor = { slot, hex -> session.setColor(slot, hex); refresh(null) },
                    onToggleLink = { session.setUnlinked(it, it !in session.configuration.unlinkedSlots); refresh() },
                    onClear = { ui.tab.categories.forEach { session.clearCategory(it) }; refresh(null) },
                    onUndo = { session.undo(); refresh() },
                    onRedo = { session.redo(); refresh() },
                    onRandom = { session.randomize(1); refresh(null) },
                    onReset = { session.resetAll(); refresh(null) },
                    onBefore = { session.toggleBefore(); refresh() },
                    onSave = {
                        notice = saveRefusal(session.configuration, registry, entitlements)
                        refresh(notice)
                    },
                )
            }
        }
        rule.onNodeWithTag("undo").assertIsNotEnabled()
        rule.onNodeWithTag("export-image").fetchSemanticsNode()
        rule.onNodeWithTag("export-recipe").fetchSemanticsNode()
        rule.onNodeWithTag("export-size:2048").fetchSemanticsNode()
        rule.onNodeWithTag("item:hair_bob").performScrollTo().performClick()
        rule.onNodeWithTag("undo").assertIsEnabled()
        assertTrue(session.wornIds().contains("hair_bob"))
        rule.onNodeWithTag("undo").performClick()
        assertFalse(session.wornIds().contains("hair_bob"))
        rule.onNodeWithTag("tab:Eyewear").performScrollTo().performClick()
        rule.onNodeWithTag("item:face_visor").performScrollTo().performClick()
        rule.onNodeWithTag("save").performClick()
        rule.onNodeWithText("Unlock Face visor to save this avatar").fetchSemanticsNode()
        assertFalse(notice.orEmpty().contains("face_visor"))
    }

    private fun ui(session: EditorSession, notice: String?) = EditorUi(
        configuration = session.configuration,
        displayed = if (session.showingOpened) session.opened else session.configuration,
        canUndo = session.canUndo,
        canRedo = session.canRedo,
        showingOpened = session.showingOpened,
        notice = notice,
        tab = EditorTab.HAIR,
        blocked = false,
    )
}
