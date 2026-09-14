package nl.dejongduke.service.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nl.dejongduke.service.data.Catalog
import nl.dejongduke.service.ui.Card
import nl.dejongduke.service.ui.ChipRow
import nl.dejongduke.service.ui.EmptyState
import nl.dejongduke.service.ui.Pill
import nl.dejongduke.service.ui.Route
import nl.dejongduke.service.ui.SectionHeader
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.forLanguageTag("nl"))

/**
 * Maintenance: the manufacturer's own cards, per machine and per interval.
 *
 * The card is what hangs inside the machine — numbered steps with the drawing
 * that belongs to each one — so that is what the app shows, rather than a list
 * rewritten around it.
 */
@Composable
fun MaintenanceScreen(
    catalog: Catalog,
    filter: String?,
    today: LocalDate,
    onFilter: (String?) -> Unit,
    onOpen: (Route) -> Unit,
) {
    val documented = catalog.machines.filter { m -> catalog.cards.any { m.id in it.machines } }
    val cards = catalog.cardsFor(filter)
    val procedures = catalog.procedures.count { it.steps.isNotEmpty() }

    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            ChipRow(
                options = listOf<Pair<String?, String>>(null to "Alle machines") +
                    documented.map { it.id as String? to it.name },
                selected = filter,
                onSelect = onFilter,
            )
        }
        item {
            Text(
                dayFormat.format(today).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
        if (cards.isEmpty()) {
            item {
                EmptyState("Geen onderhoudskaart",
                           "Voor deze machine zit er geen kaart van de fabrikant in de app.")
            }
        }
        cards.groupBy { it.interval }.forEach { (interval, group) ->
            item { SectionHeader(intervalName(interval), "${group.size}") }
            items(group, key = { it.id }) { card ->
                Card(onClick = { onOpen(Route.MaintenanceCard(card.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(card.title, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                buildString {
                                    append(catalog.machineNames(card.machines))
                                    if (card.codes.isNotEmpty()) {
                                        append("  ·  ${card.codes.joinToString(", ")}")
                                    }
                                    append("  ·  ${card.steps.size} stappen")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Filled.ChevronRight, null,
                             tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item { SectionHeader("Procedures") }
        item {
            Card(onClick = { onOpen(Route.Procedures) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Alle procedures", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "$procedures stap-voor-stap instructies",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, null,
                         tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
