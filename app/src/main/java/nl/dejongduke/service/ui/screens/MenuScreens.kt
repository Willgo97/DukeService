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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nl.dejongduke.service.data.Catalog
import nl.dejongduke.service.data.LetOp
import nl.dejongduke.service.data.MenuItem
import nl.dejongduke.service.ui.Card
import nl.dejongduke.service.ui.ChipRow
import nl.dejongduke.service.ui.Pill
import nl.dejongduke.service.ui.Route
import nl.dejongduke.service.ui.SectionHeader
import nl.dejongduke.service.ui.WarnBanner

@Composable
fun MenuList(
    catalog: Catalog,
    filter: String?,
    onFilter: (String?) -> Unit,
    onOpen: (Route) -> Unit,
) {
    val chapters = listOf(
        "6" to "Servicemenu",
        "7" to "Klussen stap voor stap",
    )
    val documented = remember(catalog) {
        catalog.machines.filter { m -> catalog.menu.any { m.id in it.machines } }
    }
    // Every book documents the menu of its own machine; without a machine
    // picked the same topic shows up once per manual.
    val shown = remember(catalog, filter) {
        catalog.menu.filter { filter == null || filter in it.machines }
    }

    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(
                    "Wat er in het servicemenu zit en wat elke functie doet, plus de klussen die je " +
                        "via het menu uitvoert — zoals ontkalken, kalibreren en software laden.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            ChipRow(
                options = listOf<Pair<String?, String>>(null to "Alle machines") +
                    documented.map { it.id as String? to it.naam },
                selected = filter,
                onSelect = onFilter,
            )
        }
        item { Spacer(Modifier.height(4.dp)) }

        chapters.forEach { (nr, naam) ->
            val items = shown.filter { it.hoofdstuk == nr }
            if (items.isEmpty()) return@forEach
            item { SectionHeader(naam, "${items.size}") }
            items(items, key = { it.id }) { m ->
                Card(onClick = { onOpen(Route.MenuItem(m.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.titel, style = MaterialTheme.typography.titleMedium)
                            if (m.pad.isNotEmpty()) {
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    m.pad,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontFamily = FontFamily.Monospace,
                                )
                            } else {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    m.tekst.take(80).let { if (m.tekst.length > 80) "$it…" else it },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                )
                            }
                        }
                        if (m.niveau.isNotEmpty()) {
                            Pill("niveau ${m.niveau}")
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
fun MenuDetail(catalog: Catalog, item: MenuItem) {
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(item.titel, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(item.nr)
                    if (item.niveau.isNotEmpty()) {
                        Pill("wachtwoordniveau ${item.niveau}", tone = MaterialTheme.colorScheme.primary)
                    }
                    if (item.pagina > 0) Pill("pagina ${item.pagina}")
                }
            }
        }
        if (item.pad.isNotEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(14.dp),
                ) {
                    Text(
                        "IN HET MENU",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item.pad,
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        if (item.afb.isNotEmpty()) {
            item { Spacer(Modifier.height(8.dp)); AssetImage(item.afb) }
        }
        if (item.doel.isNotEmpty()) {
            item { SectionHeader("Waarom") }
            item {
                Text(
                    item.doel,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
        if (item.interval.isNotEmpty()) {
            item { SectionHeader("Wanneer") }
            item {
                Text(
                    item.interval,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
        if (item.nodig.isNotEmpty()) {
            item { SectionHeader("Nodig") }
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    item.nodig.forEach { n ->
                        Row(Modifier.padding(vertical = 3.dp)) {
                            Text("·  ", style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary)
                            Text(n, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
        if (item.stappen.isNotEmpty()) {
            item { SectionHeader("Stappen", "${item.stappen.size}") }
            items(item.stappen.size) { index ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(
                        "${index + 1}.",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 10.dp),
                    )
                    Text(item.stappen[index], style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        if (item.tekst.isNotEmpty()) {
            item {
                Text(
                    item.tekst,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }
        if (item.punten.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    item.punten.forEach { punt ->
                        Text(
                            punt,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(vertical = 3.dp),
                        )
                    }
                }
            }
        }

        items(item.opmerkingen.size) { index ->
            WarnBanner(
                LetOp("let op", item.opmerkingen[index]),
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        if (item.afbs.isNotEmpty()) {
            item { SectionHeader("Uit de handleiding", "${item.afbs.size} pagina's") }
            items(item.afbs.size) { index ->
                Column {
                    AssetImage(item.afbs[index])
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        if (item.bron.isNotEmpty()) {
            item {
                Text(
                    "Bron: ${item.bron}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}
