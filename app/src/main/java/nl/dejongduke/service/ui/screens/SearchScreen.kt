package nl.dejongduke.service.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import nl.dejongduke.service.data.Catalog
import nl.dejongduke.service.data.SearchResult
import nl.dejongduke.service.ui.Card
import nl.dejongduke.service.ui.EmptyState
import nl.dejongduke.service.ui.Pill
import nl.dejongduke.service.ui.PartNumber
import nl.dejongduke.service.ui.Route
import nl.dejongduke.service.ui.SectionHeader

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    catalog: Catalog,
    query: String,
    results: SearchResult,
    recent: List<String>,
    onQuery: (String) -> Unit,
    onCommit: () -> Unit,
    onClearRecent: () -> Unit,
    onOpen: (Route) -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current

    // Opening a result is the moment a search proved useful; that is when it is
    // worth remembering. Tapping the keyboard's search key counts too.
    val openResult: (Route) -> Unit = { route ->
        onCommit()
        onOpen(route)
    }

    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                placeholder = { Text("Melding, nummer of procedure") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Close, "Wissen") }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onCommit(); keyboard?.hide() }),
            )
        }

        if (query.length < 2) {
            if (recent.isNotEmpty()) {
                item { SectionHeader("Recent gezocht") }
                item {
                    // Wrapping, so every stored term shows and a long one cannot
                    // run off the edge of the screen.
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        recent.forEach { term -> Pill(term) { onQuery(term) } }
                    }
                }
                item {
                    TextButton(onClick = onClearRecent, modifier = Modifier.padding(start = 8.dp)) {
                        Text("Geschiedenis wissen")
                    }
                }
            }
            item { SectionHeader("Vaak nodig") }
            items(veelVoorkomend(catalog)) { group ->
                FaultCard(catalog, group, showMachines = false) { onOpen(Route.Fault(group.melding)) }
            }
            item { SectionHeader("Snel naar") }
            item {
                Card(onClick = { onOpen(Route.Components) }) {
                    Column {
                        Text("Techniek: hoe het werkt", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Watersysteem, boilers, ventielen, brewer, molen en elektronica — met schema's",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Card(onClick = { onOpen(Route.Servicemenu) }) {
                    Column {
                        Text("Servicemenu", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Wat elke functie doet, met wachtwoordniveaus — plus ontkalken en kalibreren",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Card(onClick = { onOpen(Route.Specs) }) {
                    Column {
                        Text("Technische gegevens", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Water, boiler, omgeving, beker- en kanmaten, typeplaatje",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Card(onClick = { onOpen(Route.Bronnen) }) {
                    Column {
                        Text("Waar komt dit vandaan?", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "De bronnen achter elke melding, procedure en onderdeelnummer",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 26.dp, vertical = 18.dp)) {
                    Text(
                        "Tip: typ een onderdeelnummer zoals 5KAF119, of een stuk van een schermmelding zoals \"brewer\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@LazyColumn
        }

        if (results.empty) {
            item { EmptyState("Niets gevonden", "Probeer een deel van het woord, of zoek op onderdeelnummer.") }
            return@LazyColumn
        }

        if (results.faults.isNotEmpty()) {
            item { SectionHeader("Storingen", "${results.faults.size}") }
            items(results.faults, key = { it.melding }) { group ->
                FaultCard(catalog, group, showMachines = true) { openResult(Route.Fault(group.melding)) }
            }
        }

        if (results.procedures.isNotEmpty()) {
            item { SectionHeader("Procedures", "${results.procedures.size}") }
            items(results.procedures, key = { it.id }) { proc ->
                Card(onClick = { openResult(Route.Procedure(proc.id)) }) {
                    Column {
                        Text(proc.titel, style = MaterialTheme.typography.titleMedium)
                        if (proc.intervalTekst.isNotEmpty()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                proc.intervalTekst,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Pill("${proc.stappen.size} stappen")
                            if (proc.brewer.isNotEmpty()) Pill(proc.brewer)
                        }
                    }
                }
            }
        }

        if (results.components.isNotEmpty()) {
            item { SectionHeader("Techniek", "${results.components.size}") }
            items(results.components, key = { it.nr }) { c ->
                Card(onClick = { openResult(Route.Component(c.nr)) }) {
                    Column {
                        Text(c.titel, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            c.tekst.take(110).let { if (c.tekst.length > 110) "$it…" else it },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                }
            }
        }

        if (results.menu.isNotEmpty()) {
            item { SectionHeader("Servicemenu", "${results.menu.size}") }
            items(results.menu, key = { it.nr }) { m ->
                Card(onClick = { openResult(Route.MenuItem(m.nr)) }) {
                    Column {
                        Text(m.titel, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            m.pad.ifEmpty { m.tekst.take(110) },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                }
            }
        }

        if (results.machines.isNotEmpty()) {
            item { SectionHeader("Machines", "${results.machines.size}") }
            items(results.machines, key = { it.id }) { machine ->
                Card(onClick = { openResult(Route.Machine(machine.id)) }) {
                    Column {
                        Text(machine.naam, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            machine.kort,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (results.parts.isNotEmpty()) {
            item { SectionHeader("Onderdelen", "${results.parts.size}") }
            items(results.parts.size) { index ->
                val part = results.parts[index]
                Card {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (part.leverbaar) {
                                PartNumber(part.nummer)
                            } else {
                                Pill("niet los leverbaar")
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                catalog.machine(part.machine)?.naam ?: part.machine,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(part.omschrijving, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            buildString {
                                append(part.sectie)
                                if (part.pos.isNotEmpty()) append("  ·  pos ${part.pos}")
                                if (part.aantal.isNotEmpty()) append("  ·  ${part.aantal}×")
                                if (part.voorraad.isNotEmpty()) append("  ·  ${voorraadLabel(part.voorraad)}")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Default,
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

fun voorraadLabel(code: String) = when (code) {
    "SE" -> "monteursvoorraad"
    "SW" -> "magazijnvoorraad"
    else -> code
}

/**
 * The handful of messages an engineer meets most on a call-out — a shortcut past
 * typing for the things that go wrong week in, week out.
 */
private fun veelVoorkomend(catalog: Catalog) = listOf(
    "Grinder blocked",
    "Brewer out of position",
    "Waste bucket full / Empty waste bucket",
    "Drip tray full",
    "No water connected / No water in boiler",
    "Rinse brewer with tablet",
).mapNotNull { catalog.faultGroup(it) }
