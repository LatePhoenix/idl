package app.idl.ui.avatar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.idl.AppContainer
import app.idl.avatar.AvatarImage
import app.idl.domain.avatar.AssetCategory
import app.idl.domain.avatar.AssetDef
import app.idl.domain.avatar.AssetRegistry
import app.idl.domain.avatar.AssetTier
import app.idl.domain.avatar.AvatarConfiguration
import app.idl.domain.avatar.EditorDefaults
import app.idl.domain.avatar.EditorSession
import app.idl.domain.avatar.Entitlements
import app.idl.domain.avatar.LoadedAvatar
import app.idl.domain.avatar.RenderTarget
import app.idl.domain.avatar.saveRefusal
import app.idl.ui.components.IdlTopBar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal enum class EditorTab(
    val title: String,
    val categories: List<AssetCategory>,
    val palette: String?,
    val colorSlot: String?,
    val unlinkSlots: List<String> = emptyList(),
) {
    SKIN("Skin", emptyList(), "skin", "face.primary"),
    HAIR("Hair", listOf(AssetCategory.HAIR), "hair", "hair.primary", listOf("hair.highlight", "brow.primary")),
    FACIAL_HAIR("Facial hair", listOf(AssetCategory.FACIAL_HAIR), "hair", "beard.primary", listOf("beard.primary")),
    EYES("Eyes", emptyList(), "eye", "eye.primary"),
    FACE("Face details", listOf(AssetCategory.SIGNATURE_FEATURE), null, null),
    EYEWEAR("Eyewear", listOf(AssetCategory.FACE_ACCESSORY), null, null),
    HEADWEAR("Headwear", listOf(AssetCategory.HEAD_ACCESSORY), null, null),
    JEWELRY("Jewelry", listOf(AssetCategory.JEWELRY), null, null),
    TOPS("Tops", listOf(AssetCategory.TOP), "clothing", "top.primary"),
    OUTERWEAR("Outerwear", listOf(AssetCategory.OUTERWEAR), "clothing", "outerwear.primary"),
    PROPS("Props", listOf(AssetCategory.FOREGROUND_PROP, AssetCategory.BODY_ACCESSORY), null, null),
    BACKGROUND("Background", listOf(AssetCategory.SCENE), null, null),
    FRAME("Frame", listOf(AssetCategory.FRAME), null, null),
    EXPRESSION("Expression", emptyList(), null, null),
}

internal data class EditorUi(
    val configuration: AvatarConfiguration,
    val displayed: AvatarConfiguration,
    val canUndo: Boolean,
    val canRedo: Boolean,
    val showingOpened: Boolean,
    val notice: String?,
    val tab: EditorTab,
    val blocked: Boolean,
)

internal class AvatarStudioViewModel(private val c: AppContainer) : ViewModel() {
    val ui = MutableStateFlow<EditorUi?>(null)
    private var session: EditorSession? = null
    private var userId: String? = null
    private var tab = EditorTab.HAIR
    private var notice: String? = null
    private var blocked = false

    init {
        viewModelScope.launch {
            val me = c.session.me.filterNotNull().first()
            userId = me.userId
            val registry = c.assetRegistry
            when (val loaded = c.avatars.loadForEdit(me.userId)) {
                LoadedAvatar.Missing -> session = EditorSession(EditorDefaults.starter(registry), registry)
                LoadedAvatar.NeedsAppUpdate -> {
                    blocked = true
                    notice = "Update the app to edit this avatar"
                }
                is LoadedAvatar.Editable -> session = EditorSession(loaded.recipe, registry)
            }
            publish()
        }
    }

    fun selectTab(next: EditorTab) {
        tab = next
        publish()
    }

    fun wear(id: String) {
        session?.wear(id)
        notice = null
        publish()
    }

    fun setExpression(id: String) {
        session?.setExpression(id)
        notice = null
        publish()
    }

    fun setColor(slot: String, hex: String) {
        session?.setColor(slot, hex)
        notice = null
        publish()
    }

    fun toggleLink(slot: String) {
        val current = session?.configuration ?: return
        session?.setUnlinked(slot, slot !in current.unlinkedSlots)
        publish()
    }

    fun clearCategory() {
        tab.categories.forEach { session?.clearCategory(it) }
        notice = null
        publish()
    }

    fun clearTab(which: EditorTab) {
        which.categories.forEach { session?.clearCategory(it) }
        notice = null
        publish()
    }

    fun undo() {
        session?.undo()
        publish()
    }

    fun redo() {
        session?.redo()
        publish()
    }

    fun randomize() {
        session?.randomize(System.nanoTime())
        notice = null
        publish()
    }

    fun resetAll() {
        session?.resetAll()
        notice = null
        publish()
    }

    fun toggleBefore() {
        session?.toggleBefore()
        publish()
    }

    fun save(then: () -> Unit) = viewModelScope.launch {
        val id = userId ?: return@launch
        val current = session ?: return@launch
        if (blocked) return@launch
        val refusal = saveRefusal(current.configuration, c.assetRegistry, c.entitlements)
        if (refusal != null) {
            notice = refusal
            publish()
            return@launch
        }
        val saved = c.avatars.saveRecipe(id, current.configuration)
        saved.onSuccess { then() }.onFailure {
            notice = it.message
            publish()
        }
    }

    private fun publish() {
        val current = session
        if (current == null) {
            if (!blocked) {
                ui.value = null
                return
            }
            val starter = EditorDefaults.starter(c.assetRegistry)
            ui.value = EditorUi(
                configuration = starter,
                displayed = starter,
                canUndo = false,
                canRedo = false,
                showingOpened = false,
                notice = notice,
                tab = tab,
                blocked = true,
            )
            return
        }
        val shown = if (current.showingOpened) current.opened else current.configuration
        ui.value = EditorUi(
            configuration = current.configuration,
            displayed = shown,
            canUndo = current.canUndo,
            canRedo = current.canRedo,
            showingOpened = current.showingOpened,
            notice = notice,
            tab = tab,
            blocked = blocked,
        )
    }
}

@Composable
internal fun AvatarStudioScreen(
    c: AppContainer,
    firstRun: Boolean,
    onDone: () -> Unit,
    vm: AvatarStudioViewModel = viewModel { AvatarStudioViewModel(c) },
) {
    val state by vm.ui.collectAsState()
    val loaded = state
    var quick by remember { mutableStateOf(firstRun) }
    var step by remember { mutableIntStateOf(0) }
    Scaffold(topBar = {
        IdlTopBar(
            if (quick) "Build your iDL" else "Avatar",
            onBack = if (firstRun || quick) null else onDone,
        )
    }) { pad ->
        if (loaded == null) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading your avatar")
            }
        } else if (quick) {
            QuickCreator(
                state = loaded,
                step = step,
                registry = c.assetRegistry,
                entitlements = c.entitlements,
                modifier = Modifier.padding(pad),
                onStep = { step = it },
                onWear = vm::wear,
                onColor = vm::setColor,
                onClear = { vm.clearTab(quickSteps[step]) },
                onFullEditor = { quick = false },
                onSave = { vm.save(onDone) },
            )
        } else {
            EditorBody(
                state = loaded,
                registry = c.assetRegistry,
                entitlements = c.entitlements,
                firstRun = firstRun,
                modifier = Modifier.padding(pad),
                onTab = vm::selectTab,
                onWear = vm::wear,
                onExpression = vm::setExpression,
                onColor = vm::setColor,
                onToggleLink = vm::toggleLink,
                onClear = vm::clearCategory,
                onUndo = vm::undo,
                onRedo = vm::redo,
                onRandom = vm::randomize,
                onReset = vm::resetAll,
                onBefore = vm::toggleBefore,
                onStartOver = {
                    vm.resetAll()
                    step = 0
                    quick = true
                },
                onSave = { vm.save(onDone) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditorBody(
    state: EditorUi,
    registry: AssetRegistry,
    entitlements: Entitlements,
    firstRun: Boolean,
    modifier: Modifier = Modifier,
    onTab: (EditorTab) -> Unit,
    onWear: (String) -> Unit,
    onExpression: (String) -> Unit,
    onColor: (String, String) -> Unit,
    onToggleLink: (String) -> Unit,
    onClear: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onRandom: () -> Unit,
    onReset: () -> Unit,
    onBefore: () -> Unit,
    onStartOver: () -> Unit = {},
    onSave: () -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Bottom,
        ) {
            AvatarImage(
                state.displayed,
                "Avatar preview",
                size = 180.dp,
                target = RenderTarget.PROFILE,
            )
            AvatarImage(
                state.displayed,
                "Avatar at widget size",
                size = 48.dp,
                target = RenderTarget.COMPACT_WIDGET,
            )
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EditorTab.entries.forEach { tab ->
                FilterChip(
                    selected = tab == state.tab,
                    onClick = { onTab(tab) },
                    label = { Text(tab.title) },
                    modifier = Modifier.testTag("tab:${tab.title}"),
                )
            }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.notice?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("notice")) }
            ColorRow(state, registry, onColor, onToggleLink)
            if (state.tab == EditorTab.EXPRESSION) {
                ExpressionGrid(state, registry, onExpression)
            } else {
                ItemGrid(state, registry, entitlements, onWear, onClear)
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onUndo, enabled = state.canUndo && !state.blocked, modifier = Modifier.testTag("undo")) { Text("Undo") }
            TextButton(onClick = onRedo, enabled = state.canRedo && !state.blocked, modifier = Modifier.testTag("redo")) { Text("Redo") }
            TextButton(onClick = onRandom, enabled = !state.blocked, modifier = Modifier.testTag("randomize")) { Text("Random") }
            TextButton(onClick = onClear, enabled = state.tab.categories.isNotEmpty() && !state.blocked, modifier = Modifier.testTag("reset-category")) { Text("Clear") }
            TextButton(onClick = onReset, enabled = !state.blocked, modifier = Modifier.testTag("reset-all")) { Text("Reset") }
            TextButton(onClick = onBefore, enabled = !state.blocked, modifier = Modifier.testTag("before-after")) {
                Text(if (state.showingOpened) "After" else "Before")
            }
            TextButton(onClick = onStartOver, enabled = !state.blocked, modifier = Modifier.testTag("start-over")) { Text("Start over") }
        }
        Button(
            onClick = onSave,
            enabled = !state.blocked,
            modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("save"),
        ) {
            Text(if (firstRun) "This is me" else "Save")
        }
    }
}

private val quickSteps = listOf(EditorTab.SKIN, EditorTab.HAIR, EditorTab.TOPS)

@Composable
internal fun QuickCreator(
    state: EditorUi,
    step: Int,
    registry: AssetRegistry,
    entitlements: Entitlements,
    modifier: Modifier = Modifier,
    onStep: (Int) -> Unit,
    onWear: (String) -> Unit,
    onColor: (String, String) -> Unit,
    onClear: () -> Unit,
    onFullEditor: () -> Unit,
    onSave: () -> Unit,
) {
    val tab = quickSteps[step.coerceIn(quickSteps.indices)]
    val stepState = state.copy(tab = tab)
    val last = step == quickSteps.lastIndex
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarImage(
                state.displayed,
                "Avatar at widget size",
                size = 48.dp,
                target = RenderTarget.COMPACT_WIDGET,
            )
            Column {
                Text(tab.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("quick-step"))
                Text("${step + 1} of ${quickSteps.size}", style = MaterialTheme.typography.bodySmall)
            }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.notice?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("notice")) }
            if (tab.palette != null) ColorRow(stepState, registry, onColor, onToggleLink = {}, showLinks = false)
            if (tab.categories.isNotEmpty()) ItemGrid(stepState, registry, entitlements, onWear, onClear)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { onStep(step - 1) }, enabled = step > 0, modifier = Modifier.testTag("quick-back")) { Text("Back") }
            TextButton(onClick = onFullEditor, modifier = Modifier.testTag("full-editor")) { Text("All options") }
        }
        Button(
            onClick = { if (last) onSave() else onStep(step + 1) },
            enabled = !state.blocked,
            modifier = Modifier.fillMaxWidth().padding(16.dp).testTag(if (last) "save" else "quick-next"),
        ) {
            Text(if (last) "This is me" else "Next")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorRow(
    state: EditorUi,
    registry: AssetRegistry,
    onColor: (String, String) -> Unit,
    onToggleLink: (String) -> Unit,
    showLinks: Boolean = true,
) {
    val tab = state.tab
    val slot = tab.colorSlot
    val swatches = tab.palette?.let { registry.defaults.palettes[it] }.orEmpty()
    if (slot != null && swatches.isNotEmpty()) {
        val selected = state.configuration.colorOverrides[slot]
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            swatches.forEach { swatch ->
                val on = selected.equals(swatch.hex, ignoreCase = true)
                Box(
                    Modifier
                        .size(36.dp)
                        .border(if (on) 3.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                        .padding(4.dp)
                        .background(parseHex(swatch.hex), CircleShape)
                        .clickable { onColor(slot, swatch.hex) }
                        .semantics { role = Role.RadioButton; this.selected = on; contentDescription = swatch.name }
                        .testTag("swatch:${swatch.id}"),
                )
            }
        }
    }
    if (showLinks && tab.unlinkSlots.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tab.unlinkSlots.forEach { link ->
                val linked = link !in state.configuration.unlinkedSlots
                FilterChip(
                    selected = linked,
                    onClick = { onToggleLink(link) },
                    label = { Text(if (linked) "Linked ${link.substringBefore('.')}" else "Unlinked ${link.substringBefore('.')}") },
                    modifier = Modifier.semantics { contentDescription = if (linked) "Linked $link" else "Unlinked $link" },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemGrid(
    state: EditorUi,
    registry: AssetRegistry,
    entitlements: Entitlements,
    onWear: (String) -> Unit,
    onClear: () -> Unit,
) {
    val sessionChoices = EditorSession(state.configuration, registry)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.tab.categories.any { !it.multiple }) {
            FilterChip(
                selected = state.tab.categories.none { category -> sessionChoices.wornIds().any { registry.asset(it)?.category == category } },
                onClick = onClear,
                label = { Text("None") },
                modifier = Modifier.testTag("item:none"),
            )
        }
        state.tab.categories.forEach { category ->
            sessionChoices.choices(category).forEach { asset ->
                ItemChip(asset, selected = asset.id in sessionChoices.wornIds(), locked = asset.tier == AssetTier.PREMIUM && !entitlements.owns(asset.id), onWear = onWear)
            }
        }
    }
}

@Composable
private fun ItemChip(asset: AssetDef, selected: Boolean, locked: Boolean, onWear: (String) -> Unit) {
    val label = if (locked) "${asset.accessibilityLabel}, locked" else asset.accessibilityLabel
    FilterChip(
        selected = selected,
        onClick = { onWear(asset.id) },
        label = { Text(if (locked) "${asset.accessibilityLabel} · lock" else asset.accessibilityLabel) },
        modifier = Modifier
            .semantics { contentDescription = label }
            .testTag("item:${asset.id}"),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpressionGrid(state: EditorUi, registry: AssetRegistry, onExpression: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        registry.allExpressions.sortedBy { it.label }.forEach { expression ->
            FilterChip(
                selected = expression.id == state.configuration.restingExpressionId,
                onClick = { onExpression(expression.id) },
                label = { Text(expression.label) },
                modifier = Modifier
                    .semantics { contentDescription = expression.label }
                    .testTag("expression:${expression.id}"),
            )
        }
    }
}

private fun parseHex(hex: String): Color = Color(hex.toColorInt())
