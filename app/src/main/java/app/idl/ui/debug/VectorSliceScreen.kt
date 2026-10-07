package app.idl.ui.debug

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.idl.AppContainer
import app.idl.avatar.EmojiSlice
import app.idl.domain.avatar.RenderTarget
import app.idl.ui.components.ChoiceChips
import app.idl.ui.components.IdlTopBar
import app.idl.ui.components.SectionTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun VectorSliceScreen(c: AppContainer, onBack: () -> Unit) {
    var hair by remember { mutableStateOf(EmojiSlice.hairSwatches.first().first) }
    var unlinkShadow by remember { mutableStateOf(false) }
    val rendered by produceState<Rendered?>(null, hair, unlinkShadow) {
        value = withContext(Dispatchers.Default) {
            render(c, hair, unlinkShadow)
        }
    }

    Scaffold(topBar = { IdlTopBar("Vector slice", onBack = onBack) }) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .testTag("vectorSliceScreen"),
        ) {
            SectionTitle("Hair")
            Text(
                "Changing the primary color also moves the derived shadow and highlight, unless the shadow is unlinked.",
                style = MaterialTheme.typography.bodySmall,
            )
            ChoiceChips(
                EmojiSlice.hairSwatches,
                EmojiSlice.hairSwatches.first { it.first == hair },
                { it.second },
                { choice -> if (choice != null) hair = choice.first },
                allowNone = false,
                testTagPrefix = "hairPrimary",
            )
            ListItem(
                headlineContent = { Text("Unlink hair.shadow") },
                supportingContent = { Text("Keep the asset's shadow while the primary changes") },
                trailingContent = {
                    Switch(checked = unlinkShadow, onCheckedChange = { unlinkShadow = it }, modifier = Modifier.testTag("unlinkHairShadow"))
                },
            )
            val shot = rendered
            Text(
                if (shot == null) "Rendering…" else "Last 512 px render: ${shot.renderMs} ms",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp).testTag("vectorRenderTime"),
            )
            shot?.rows?.forEach { row ->
                SectionTitle(row.label)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.bitmaps.forEach { (size, bitmap) ->
                        Column {
                            Text(
                                "$size px · ${if (size == 512) "bust" else "head"}",
                                style = MaterialTheme.typography.labelSmall,
                            )
                            val side = with(LocalDensity.current) { bitmap.width.toDp() }
                            Image(
                                bitmap.asImageBitmap(),
                                contentDescription = "${row.label}, $size pixels",
                                modifier = Modifier.size(side).testTag("slice:${row.id}:$size"),
                                filterQuality = FilterQuality.None,
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class Rendered(val rows: List<RowShots>, val renderMs: Long)

private data class RowShots(val id: String, val label: String, val bitmaps: List<Pair<Int, Bitmap>>)

private fun render(c: AppContainer, hair: String, unlinkShadow: Boolean): Rendered {
    val registry = c.assetRegistry
    val pictures = c.vectorPictures
    var renderMs = 0L
    val rows = EmojiSlice.recipes.map { recipe ->
        val bitmaps = EmojiSlice.sizes.map { size ->
            val started = if (size == 512) SystemClock.elapsedRealtimeNanos() else 0L
            val target = when (size) {
                48 -> RenderTarget.COMPACT_WIDGET
                128 -> RenderTarget.LARGE_WIDGET
                else -> RenderTarget.PROFILE
            }
            val bitmap = EmojiSlice.bitmap(registry, pictures, recipe, size, hair, unlinkShadow, target, c.expressionCatalog)
            if (size == 512) renderMs = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000
            size to bitmap
        }
        RowShots(recipe.id, recipe.label, bitmaps)
    }
    return Rendered(rows, renderMs)
}
