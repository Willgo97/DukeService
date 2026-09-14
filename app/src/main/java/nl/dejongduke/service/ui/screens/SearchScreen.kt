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
import nl.dejongduke.service.ui.Tab

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    catalog: Catalog,
    query: String,
    results: SearchResult,
    recent: List<String>,
    pins: List<String>,
    language: String,
    onQuery: (String) -> Unit,
    onCommit: () -> Unit,
    onClearRecent: () -> Unit,
    onOpen: (Route) -> Unit,
    onTab: (Tab) -> Unit,
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
            homeSections(catalog, pins, onOpen, onTab)

            item { SectionHeader("Vaak nodig") }
            items(veelVoorkomend(catalog)) { group ->
                FaultCard(catalog, group, showMachines = false, language = language) {
                    onOpen(Route.Fault(group.message))
                }
            }

            catalogSummary(catalog)
            return@LazyColumn
        }

        if (results.empty) {
            item { EmptyState("Niets gevonden", "Probeer een deel van het woord, of zoek op onderdeelnummer.") }
            return@LazyColumn
        }

        if (results.faults.isNotEmpty()) {
            item { SectionHeader("Storingen", "${results.faults.size}") }
            items(results.faults, key = { it.message }) { group ->
                FaultCard(catalog, group, showMachines = true, language = language) {
                    openResult(Route.Fault(group.message))
                }
            }
        }

        if (results.procedures.isNotEmpty()) {
            item { SectionHeader("Procedures", "${results.procedures.size}") }
            items(results.procedures, key = { it.id }) { procedures ->
                Card(onClick = { openResult(Route.Procedure(procedures.id)) }) {
                    Column {
                        Text(procedures.title, style = MaterialTheme.typography.titleMedium)
                        if (procedures.intervalText.isNotEmpty()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                procedures.intervalText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Pill("${procedures.steps.size} stappen")
                            if (procedures.brewer.isNotEmpty()) Pill(procedures.brewer)
                        }
                    }
                }
            }
        }

        if (results.components.isNotEmpty()) {
            item { SectionHeader("Techniek", "${results.components.size}") }
            items(results.components, key = { it.id }) { c ->
                Card(onClick = { openResult(Route.Component(c.id)) }) {
                    Column {
                        Text(c.title, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            c.text.take(110).let { if (c.text.length > 110) "$it…" else it },
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
            items(results.menu, key = { it.id }) { m ->
                Card(onClick = { openResult(Route.MenuItem(m.id)) }) {
                    Column {
                        Text(m.title, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            m.path.ifEmpty { m.text.take(110) },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                }
            }
        }

        if (results.cards.isNotEmpty()) {
            item { SectionHeader("Onderhoudskaarten", "${results.cards.size}") }
            items(results.cards, key = { it.id }) { card ->
                Card(onClick = { openResult(Route.MaintenanceCard(card.id)) }) {
                    Column {
                        Text(card.title, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${catalog.machineNames(card.machines)}  ·  ${card.steps.size} stappen",
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
                        Text(machine.name, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            machine.summary,
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
                            if (part.available) {
                                PartNumber(part.number)
                            } else {
                                Pill("niet los leverbaar")
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                catalog.machine(part.machine)?.name ?: part.machine,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(part.description, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            buildString {
                                append(part.section)
                                if (part.pos.isNotEmpty()) append("  ·  pos ${part.pos}")
                                if (part.quantity.isNotEmpty()) append("  ·  ${part.quantity}×")
                                if (part.stock.isNotEmpty()) append("  ·  ${stockLabel(part.stock)}")
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

fun stockLabel(code: String) = when (code) {
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
