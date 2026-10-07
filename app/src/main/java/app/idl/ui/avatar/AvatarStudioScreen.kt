package app.idl.ui.avatar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import app.idl.domain.AvatarConfig
import app.idl.domain.AvatarPalette
import app.idl.domain.BodyAccessory
import app.idl.domain.avatar.AvatarWrite
import app.idl.domain.avatar.LoadedAvatar
import app.idl.domain.Expression
import app.idl.domain.FaceAccessory
import app.idl.domain.FaceStyle
import app.idl.domain.FrameStyle
import app.idl.domain.HeadAccessory
import app.idl.domain.Prop
import app.idl.domain.Scene
import app.idl.ui.components.ChoiceChips
import app.idl.ui.components.IdlTopBar
import app.idl.ui.components.SectionTitle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class AvatarStudioViewModel(private val c: AppContainer) : ViewModel() {
    val config = MutableStateFlow(AvatarConfig())
    val notice = MutableStateFlow<String?>(null)
    private var userId: String? = null
    private var blocked = false

    init {
        viewModelScope.launch {
            val me = c.session.me.filterNotNull().first()
            userId = me.userId
            when (val loaded = c.avatars.loadForEdit(me.userId)) {
                LoadedAvatar.Missing -> config.value = starterFor(me.username)
                LoadedAvatar.NeedsAppUpdate -> {
                    blocked = true
                    notice.value = AvatarWrite.NeedsAppUpdate.message.replaceFirstChar { it.uppercase() }
                }
                is LoadedAvatar.Editable -> config.value = loaded.config
            }
        }
    }

    fun update(cfg: AvatarConfig) { config.value = cfg }

    fun save(then: () -> Unit) = viewModelScope.launch {
        val id = userId ?: return@launch
        if (blocked) return@launch
        val saved = c.avatars.save(id, config.value)
        if (saved.exceptionOrNull()?.message == AvatarWrite.NeedsAppUpdate.message) {
            notice.value = AvatarWrite.NeedsAppUpdate.message.replaceFirstChar { it.uppercase() }
            return@launch
        }
        then()
    }

    companion object {
        fun starterFor(seed: String): AvatarConfig {
            val h = seed.hashCode() and 0x7FFFFFFF
            return AvatarConfig(
                bodyColor = AvatarPalette.body[h % AvatarPalette.body.size],
                themeColor = AvatarPalette.theme[h % AvatarPalette.theme.size],
                expression = Expression.HAPPY,
            )
        }
    }
}

@Composable
fun AvatarStudioScreen(
    c: AppContainer,
    firstRun: Boolean,
    onDone: () -> Unit,
    vm: AvatarStudioViewModel = viewModel { AvatarStudioViewModel(c) },
) {
    val cfg by vm.config.collectAsState()
    val notice by vm.notice.collectAsState()
    Scaffold(topBar = { IdlTopBar(if (firstRun) "Build your iDL" else "Avatar Studio", onBack = if (firstRun) null else onDone) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                AvatarImage(cfg, "Your avatar preview", size = 168.dp)
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                notice?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp)) }
                SectionTitle("Color")
                Swatches(AvatarPalette.body, cfg.bodyColor) { vm.update(cfg.copy(bodyColor = it)) }
                SectionTitle("Resting expression")
                ChoiceChips(Expression.entries, cfg.expression, { it.label }, { it?.let { e -> vm.update(cfg.copy(expression = e)) } }, allowNone = false)
                SectionTitle("Face")
                ChoiceChips(FaceStyle.entries, cfg.faceStyle, { it.label }, { it?.let { f -> vm.update(cfg.copy(faceStyle = f)) } }, allowNone = false)
                SectionTitle("Head")
                ChoiceChips(HeadAccessory.entries, cfg.headAccessory, { it.label }, { it?.let { h -> vm.update(cfg.copy(headAccessory = h)) } }, allowNone = false)
                SectionTitle("Eyewear")
                ChoiceChips(FaceAccessory.entries, cfg.faceAccessory, { it.label }, { it?.let { f -> vm.update(cfg.copy(faceAccessory = f)) } }, allowNone = false)
                SectionTitle("Outfit")
                ChoiceChips(BodyAccessory.entries, cfg.bodyAccessory, { it.label }, { it?.let { b -> vm.update(cfg.copy(bodyAccessory = b)) } }, allowNone = false)
                SectionTitle("Favorite prop")
                ChoiceChips(Prop.entries, cfg.handProp, { if (it.emoji.isEmpty()) it.label else "${it.emoji} ${it.label}" }, { it?.let { p -> vm.update(cfg.copy(handProp = p)) } }, allowNone = false)
                SectionTitle("Scene")
                ChoiceChips(Scene.entries, cfg.scene, { it.label }, { it?.let { s -> vm.update(cfg.copy(scene = s)) } }, allowNone = false)
                SectionTitle("Theme")
                Swatches(AvatarPalette.theme, cfg.themeColor) { vm.update(cfg.copy(themeColor = it)) }
                SectionTitle("Frame")
                ChoiceChips(FrameStyle.entries, cfg.frameStyle, { it.label }, { it?.let { f -> vm.update(cfg.copy(frameStyle = f)) } }, allowNone = false)
            }
            Button(onClick = { vm.save(onDone) }, enabled = notice == null, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(if (firstRun) "This is me" else "Save")
            }
        }
    }
}

@Composable
private fun Swatches(colors: List<Int>, selected: Int, onPick: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEachIndexed { i, c ->
            val isSel = c == selected
            Box(
                Modifier
                    .size(40.dp)
                    .border(if (isSel) 3.dp else 1.dp, if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                    .padding(4.dp)
                    .background(Color(c), CircleShape)
                    .clickable { onPick(c) }
                    .semantics { role = Role.RadioButton; this.selected = isSel; contentDescription = "Color ${i + 1}" },
            )
        }
    }
}
