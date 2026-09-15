package nl.dejongduke.service.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nl.dejongduke.service.R
import nl.dejongduke.service.data.Catalog
import nl.dejongduke.service.data.SafetyNote
import nl.dejongduke.service.data.MenuItem
import nl.dejongduke.service.ui.Card
import nl.dejongduke.service.ui.ChipRow
import nl.dejongduke.service.ui.Pill
import nl.dejongduke.service.ui.Route
import nl.dejongduke.service.ui.SectionHeader
import nl.dejongduke.service.ui.WarnBanner
import nl.dejongduke.service.ui.count

@Composable
fun MenuList(
    catalog: Catalog,
    filter: String?,
    variant: String?,
    onFilter: (String?) -> Unit,
    onOpen: (Route) -> Unit,
) {
    val chapters = listOf(
        "6" to stringResource(R.string.service_menu),
    )
    val documented = remember(catalog) {
        catalog.machines.filter { m -> catalog.menu.any { m.id in it.machines } }
    }
    // Every book documents the menu of its own machine; without a machine
    // picked the same topic shows up once per manual.
    val shown = remember(catalog, filter, variant) {
        catalog.menu.filter {
            (filter == null || filter in it.machines) && catalog.forVariant(it.codes, variant)
        }
    }

    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(
                    stringResource(R.string.what_is_in_the_service_menu_and_what_each_fu),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            ChipRow(
                options = listOf<Pair<String?, String>>(null to stringResource(R.string.all_machines)) +
                    documented.map { it.id as String? to it.name },
                selected = filter,
                onSelect = onFilter,
            )
        }
        item { Spacer(Modifier.height(4.dp)) }

        chapters.forEach { (number, name) ->
            val items = shown.filter { it.chapter == number }
            if (items.isEmpty()) return@forEach
            item { SectionHeader(name, "${items.size}") }
            items(items, key = { it.id }) { m ->
                Card(onClick = { onOpen(Route.MenuItem(m.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.title, style = MaterialTheme.typography.titleMedium)
                            if (m.path.isNotEmpty()) {
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    m.path,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontFamily = FontFamily.Monospace,
                                )
                            } else {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    m.text.take(80).let { if (m.text.length > 80) "$it…" else it },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                )
                            }
                        }
                        if (m.level.isNotEmpty()) {
                            Pill(stringResource(R.string.level, m.level))
                            Spacer(Modifier.height(0.dp))
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
fun MenuDetail(item: MenuItem) {
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(item.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(item.number)
                    if (item.level.isNotEmpty()) {
                        Pill(stringResource(R.string.password_level, item.level), tone = MaterialTheme.colorScheme.primary)
                    }
                    if (item.page > 0) Pill(stringResource(R.string.page_2, item.page))
                }
            }
        }
        if (item.path.isNotEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(14.dp),
                ) {
                    Text(
                        stringResource(R.string.in_the_menu),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item.path,
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        if (item.image.isNotEmpty()) {
            item { Spacer(Modifier.height(8.dp)); AssetImage(item.image) }
        }
        if (item.purpose.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.why)) }
            item {
                Text(
                    item.purpose,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
        if (item.interval.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.when_label)) }
            item {
                Text(
                    item.interval,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
        if (item.needed.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.needed)) }
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    item.needed.forEach { n ->
                        Row(Modifier.padding(vertical = 3.dp)) {
                            Text("·  ", style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary)
                            Text(n, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
        if (item.steps.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.steps), "${item.steps.size}") }
            items(item.steps.size) { index ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(
                        "${index + 1}.",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 10.dp),
                    )
                    Text(item.steps[index], style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        if (item.text.isNotEmpty()) {
            item {
                Text(
                    item.text,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }
        if (item.points.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    item.points.forEach { punt ->
                        Text(
                            punt,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(vertical = 3.dp),
                        )
                    }
                }
            }
        }

        items(item.notes.size) { index ->
            WarnBanner(
                SafetyNote("let op", item.notes[index]),
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        if (item.images.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.from_the_manual), count(R.plurals.n_pages, item.images.size)) }
            items(item.images.size) { index ->
                Column {
                    AssetImage(item.images[index])
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        if (item.source.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.source_3, item.source),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}
