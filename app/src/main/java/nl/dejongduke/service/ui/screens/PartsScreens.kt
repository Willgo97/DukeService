package nl.dejongduke.service.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nl.dejongduke.service.data.Catalog
import nl.dejongduke.service.data.Part
import nl.dejongduke.service.ui.Card
import nl.dejongduke.service.ui.ChipRow
import nl.dejongduke.service.ui.EmptyState
import nl.dejongduke.service.ui.PartNumber
import nl.dejongduke.service.ui.Pill
import nl.dejongduke.service.ui.Route
import nl.dejongduke.service.ui.SectionHeader

@Composable
fun PartsScreen(
    catalog: Catalog,
    filter: String?,
    onFilter: (String?) -> Unit,
    onOpen: (Route) -> Unit,
) {
    var term by remember { mutableStateOf("") }
    val documented = catalog.machines.filter { m -> catalog.parts.any { it.machine == m.id } }
    // Without a machine chosen the section list would mix five books together,
    // so default to the first documented machine instead of showing everything.
    val machine = filter?.takeIf { id -> documented.any { it.id == id } } ?: documented.firstOrNull()?.id

    val forMachine = remember(machine) { catalog.parts.filter { it.machine == machine } }
    val searching = term.trim().length >= 2
    val hits = remember(machine, term) { catalog.searchParts(machine, term) }
    val sections = remember(machine) { forMachine.groupBy { it.sectie }.toSortedMap() }

    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            ChipRow(
                options = documented.map { it.id as String? to it.naam },
                selected = machine,
                onSelect = onFilter,
            )
        }
        item {
            OutlinedTextField(
                value = term,
                onValueChange = { term = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                placeholder = { Text("Nummer of omschrijving") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (term.isNotEmpty()) {
                        IconButton(onClick = { term = "" }) { Icon(Icons.Filled.Close, "Wissen") }
                    }
                },
                singleLine = true,
            )
        }

        if (machine == null) {
            item { EmptyState("Geen onderdelenboek", "Voor deze machine staat nog geen onderdelenlijst in de app.") }
            return@LazyColumn
        }

        if (searching) {
            item { SectionHeader("Gevonden", "${hits.size}") }
            if (hits.isEmpty()) {
                item { EmptyState("Niets gevonden", "Probeer een deel van het nummer of een Engelse term, zoals \"boiler\".") }
            }
            items(hits.size) { index -> PartRow(hits[index], showSection = true) }
            item { Spacer(Modifier.height(24.dp)) }
            return@LazyColumn
        }

        item { SectionHeader("Tekeningen", "${sections.size}") }
        items(sections.keys.toList()) { sectie ->
            val count = sections[sectie]?.size ?: 0
            Card(onClick = { onOpen(Route.PartSection(machine, sectie)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(sectie, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "$count onderdelen",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun PartSectionDetail(catalog: Catalog, machine: String, sectie: String) {
    val parts = remember(machine, sectie) {
        catalog.parts.filter { it.machine == machine && it.sectie == sectie }
    }
    val tekening = catalog.drawing(machine, sectie)
    val balloons = remember(machine, sectie) { catalog.balloons(machine, sectie) }
    var gekozen by remember(machine, sectie) { mutableStateOf<String?>(null) }

    /** Position as the balloons write it: "05" in the table is "5" on the drawing. */
    fun kort(p: String) = p.trimStart('0').lowercase().ifEmpty { "0" }


    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(sectie, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(catalog.machine(machine)?.naam ?: machine)
                    Pill("${parts.size} onderdelen")
                    if (balloons.isNotEmpty()) Pill("${balloons.size} aanklikbaar")
                }
            }
        }
        if (tekening != null) {
            item {
                Column {
                    DrawingView(
                        path = tekening,
                        balloons = balloons,
                        geselecteerd = gekozen,
                        // No scrolling: the answer appears under the drawing, so
                        // the picture you are reading stays in view.
                        onSelect = { pos -> gekozen = if (gekozen == pos) null else pos },
                    )
                    val gekozenDelen = parts.filter { gekozen != null && kort(it.pos) == gekozen }
                    if (gekozenDelen.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        gekozenDelen.forEach { part ->
                            PartRow(part, showSection = false, actief = true)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (balloons.isEmpty())
                            "Knijp om in te zoomen. De nummers in de tekening zijn de posities hieronder."
                        else
                            "Tik een nummer in de tekening aan, of tik een onderdeel in de lijst.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        items(parts.size) { index ->
            val part = parts[index]
            PartRow(
                part = part,
                showSection = false,
                actief = gekozen != null && kort(part.pos) == gekozen,
                onClick = if (balloons.any { it.pos == kort(part.pos) }) {
                    { gekozen = kort(part.pos) }
                } else null,
            )
        }
        item {
            Text(
                "SE = monteursvoorraad · SW = magazijnvoorraad · pos verwijst naar het nummer in de tekening.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            )
        }
    }
}

@Composable
private fun PartRow(
    part: Part,
    showSection: Boolean,
    actief: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Card(onClick = onClick, highlight = actief) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (part.leverbaar) PartNumber(part.nummer) else Pill("niet los leverbaar")
                Spacer(Modifier.width(8.dp))
                if (part.voorraad.isNotEmpty()) {
                    Pill(
                        part.voorraad,
                        tone = if (part.voorraad == "SE") MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (part.pos.isNotEmpty()) {
                    Text(
                        "pos ${part.pos}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(part.omschrijving, style = MaterialTheme.typography.bodyLarge)
            if (showSection || part.aantal.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    buildString {
                        if (showSection) append(part.sectie)
                        if (part.aantal.isNotEmpty()) {
                            if (isNotEmpty()) append("  ·  ")
                            append("${part.aantal}× per machine")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
