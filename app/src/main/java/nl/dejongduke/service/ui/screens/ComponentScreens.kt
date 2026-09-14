@file:OptIn(ExperimentalLayoutApi::class)

package nl.dejongduke.service.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.dejongduke.service.R
import nl.dejongduke.service.data.Catalog
import nl.dejongduke.service.data.Component
import nl.dejongduke.service.ui.Card
import nl.dejongduke.service.ui.languageName
import nl.dejongduke.service.ui.appLanguage
import nl.dejongduke.service.ui.decodeAsset
import nl.dejongduke.service.ui.ChipRow
import nl.dejongduke.service.ui.EmptyState
import nl.dejongduke.service.ui.Pill
import nl.dejongduke.service.ui.Route
import nl.dejongduke.service.ui.SectionHeader

/**
 * The order the machine is built in, not the order the books number things:
 * every manual chapters this differently, so the parser tags each section with
 * a subject and the list follows that.
 */
/** "4.1.16.2" -> 4001600200, so numbers sort the way the book reads. */
private fun number(number: String): Long =
    number.split('.').take(4).fold(0L) { acc, part -> acc * 100 + (part.toLongOrNull() ?: 0L) }

/** Water first, then what it flows into, then the electronics that drive it. */
private val GROUP_ORDER = listOf(
    "water", "brewer", "grinder", "mixer", "ingredients", "milk", "electronics", "other",
)

@Composable
private fun groupLabel(key: String): String = when (key) {
    "water" -> stringResource(R.string.watersysteem)
    "brewer" -> stringResource(R.string.brewer)
    "grinder" -> stringResource(R.string.molen)
    "mixer" -> stringResource(R.string.mixer)
    "ingredients" -> stringResource(R.string.ingredienten)
    "milk" -> stringResource(R.string.verse_melk)
    "electronics" -> stringResource(R.string.elektronica)
    else -> stringResource(R.string.overig)
}

@Composable
fun ComponentList(
    catalog: Catalog,
    filter: String?,
    variant: String?,
    onFilter: (String?) -> Unit,
    onOpen: (Route) -> Unit,
) {
    val documented = remember(catalog) {
        catalog.machines.filter { m -> catalog.components.any { m.id in it.machines } }
    }
    val shown = remember(catalog, filter, variant) {
        catalog.components.filter {
            (filter == null || filter in it.machines) && catalog.forVariant(it.codes, variant)
        }
    }
    val groups = remember(shown) {
        shown.groupBy { it.group }.toList()
            .sortedBy { (name, _) -> GROUP_ORDER.indexOf(name).let { if (it < 0) GROUP_ORDER.size else it } }
            // Books number the same subject differently, so sort on the number
            // itself; that keeps the water system running from inlet to boiler
            // even when two manuals are mixed.
            .map { (name, items) -> name to items.sortedBy { number(it.number) } }
    }

    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(
                    stringResource(R.string.hoe_de_machine_werkt_watersysteem_boilers_ve),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            ChipRow(
                options = listOf<Pair<String?, String>>(null to stringResource(R.string.alle_machines)) +
                    documented.map { it.id as String? to it.name },
                selected = filter,
                onSelect = onFilter,
            )
        }
        item { Spacer(Modifier.height(4.dp)) }

        if (shown.isEmpty()) {
            item {
                EmptyState(
                    stringResource(R.string.geen_techniek),
                    stringResource(R.string.voor_deze_machine_staat_de_technische_handle),
                )
            }
        }

        groups.forEach { (group, items) ->
            item { SectionHeader(group, "${items.size}") }
            items(items, key = { it.id }) { c ->
                Card(onClick = { onOpen(Route.Component(c.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(c.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, false))
                                // Which brewer this section describes only matters
                                // while looking across machines.
                                if (filter == null && c.brewer.isNotEmpty()) {
                                    Spacer(Modifier.width(8.dp))
                                    Pill(c.brewer)
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                c.text.take(90).let { if (c.text.length > 90) "$it…" else it },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun ComponentDetail(catalog: Catalog, component: Component) {
    val machines = remember(component) {
        component.machines.mapNotNull { catalog.machine(it)?.name }
    }
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(component.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (component.brewer.isNotEmpty()) Pill(component.brewer, selected = true)
                    machines.forEach { Pill(it) }
                    if (component.page > 0) Pill(stringResource(R.string.pagina_x, component.page))
                    // Not every book exists in every language; this one is read
                    // in whichever came closest.
                    if (component.language != appLanguage()) Pill(languageName(component.language))
                }
            }
        }
        items(component.images, key = { it }) { image ->
            AssetImage(image)
            Spacer(Modifier.height(10.dp))
        }
        item {
            Text(
                component.text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        if (component.source.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.bron_x, component.source),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
fun AssetImage(path: String) {
    val context = LocalContext.current
    val screen = LocalConfiguration.current.screenWidthDp
    val density = LocalDensity.current.density
    val bitmap by produceState<ImageBitmap?>(null, path, screen) {
        value = withContext(Dispatchers.IO) {
            decodeAsset(context, path, (screen * density).toInt())
        }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val image = bitmap

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .then(if (image != null) Modifier.aspectRatio(image.width.toFloat() / image.height) else Modifier.height(220.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (image == null) {
            CircularProgressIndicator(Modifier.height(28.dp))
            return@Box
        }
        Image(
            bitmap = image,
            contentDescription = stringResource(R.string.pagina_uit_de_handleiding),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY)
                .pointerInput(path) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                },
        )
    }
}
