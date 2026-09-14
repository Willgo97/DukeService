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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import nl.dejongduke.service.data.Book
import nl.dejongduke.service.data.Catalog
import nl.dejongduke.service.data.MaintenanceCard
import nl.dejongduke.service.ui.Card
import nl.dejongduke.service.ui.ChipRow
import nl.dejongduke.service.ui.EmptyState
import nl.dejongduke.service.ui.Pill
import nl.dejongduke.service.ui.Route
import nl.dejongduke.service.ui.SectionHeader

/**
 * The manufacturer's maintenance sheets. These are the fold-out cards that hang
 * inside the machine: a numbered step, a picture, and what to do. One card per
 * machine and interval, so the list is by machine first.
 */
@Composable
fun CardList(
    catalog: Catalog,
    filter: String?,
    variant: String?,
    onFilter: (String?) -> Unit,
    onOpen: (Route) -> Unit,
) {
    val withCards = catalog.machines.filter { m -> catalog.cards.any { it.machines.contains(m.id) } }
    val cards = remember(catalog, filter, variant) { catalog.cardsFor(filter, variant) }
    val perInterval = cards.groupBy { it.interval }

    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            ChipRow(
                options = listOf<Pair<String?, String>>(null to "Alle") + withCards.map { it.id as String? to it.name },
                selected = filter,
                onSelect = onFilter,
            )
        }
        item {
            Text(
                "De onderhoudskaart van de fabrikant, stap voor stap met de tekeningen " +
                    "die erbij horen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        if (cards.isEmpty()) {
            item { EmptyState("Geen kaart", "Voor deze machine zit er geen onderhoudskaart in de app.") }
        }
        for ((interval, group) in perInterval) {
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
                                        append("  ·  " + card.codes.joinToString(", ") {
                                            catalog.variantLabel(card.machines.firstOrNull(), it)
                                        })
                                    }
                                    append("  ·  ${card.steps.size} stappen")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

fun intervalName(interval: String) = when (interval) {
    "dag" -> "Dagelijks"
    "week" -> "Wekelijks"
    "maand" -> "Maandelijks"
    "kwartaal" -> "Per kwartaal"
    "jaar" -> "Jaarlijks"
    "periodiek" -> "Periodiek onderhoud"
    else -> "Overig"
}

@Composable
fun CardDetail(catalog: Catalog, card: MaintenanceCard, onOpen: (Route) -> Unit) {
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(card.title, style = MaterialTheme.typography.headlineSmall,
                     fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(intervalName(card.interval))
                    Pill(catalog.machineNames(card.machines))
                    if (card.language != "nl") Pill(card.language.uppercase())
                }
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = { onOpen(Route.Steps("card", card.id)) }) {
                    Icon(Icons.Filled.PlayArrow, null, Modifier.height(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Stap voor stap")
                }
            }
        }
        items(card.steps.size) { index ->
            val step = card.steps[index]
            Card {
                Column {
                    Row(verticalAlignment = Alignment.Top) {
                        if (step.number.isNotEmpty()) {
                            Text(
                                step.number,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(14.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            step.points.forEach { punt ->
                                Text("• $punt", style = MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.height(4.dp))
                            }
                            step.notes.forEach { note ->
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    note,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                    }
                    step.images.forEach { image ->
                        Spacer(Modifier.height(10.dp))
                        AssetImage(image)
                    }
                }
            }
        }
        item {
            Text(
                "Bron: ${card.source}. Bij twijfel is de kaart in de machine leidend.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            )
        }
    }
}

/** Which books the app was built from, per machine. */
@Composable
fun BookList(catalog: Catalog, filter: String?, onFilter: (String?) -> Unit) {
    var kind by remember { mutableStateOf<String?>(null) }
    val merken = catalog.machines.filter { m -> catalog.books.any { it.brand == m.id } }
    val books = catalog.books.filter { b ->
        (filter == null || b.brand == filter) && (kind == null || b.kind == kind)
    }
    val soorten = catalog.books.map { it.kind }.distinct().sorted()

    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            ChipRow(
                options = listOf<Pair<String?, String>>(null to "Alle") + merken.map { it.id as String? to it.name },
                selected = filter,
                onSelect = onFilter,
            )
        }
        item {
            ChipRow(
                options = listOf<Pair<String?, String>>(null to "Alles") +
                    soorten.map { s -> s as String? to (catalog.books.first { it.kind == s }.kindName) },
                selected = kind,
                onSelect = { kind = it },
            )
        }
        item { SectionHeader("Boeken", "${books.size}") }
        items(books, key = { it.id }) { book -> BookRow(book) }
        item {
            Text(
                "De handleidingen zelf zitten niet in de app: ze zijn auteursrechtelijk " +
                    "beschermd. Wat je hier ziet is waar de informatie vandaan komt.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            )
        }
    }
}

@Composable
private fun BookRow(book: Book) {
    Card {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(book.kindName)
                Spacer(Modifier.width(8.dp))
                if (book.language.isNotEmpty()) Pill(book.language.uppercase())
                Spacer(Modifier.weight(1f))
                if (book.superseded) Pill("oude druk")
            }
            Spacer(Modifier.height(6.dp))
            Text(book.title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                buildString {
                    if (book.number.isNotEmpty()) append(book.number)
                    if (book.version.isNotEmpty()) {
                        if (isNotEmpty()) append("  ·  ")
                        append(book.version)
                    }
                    if (book.pages > 0) {
                        if (isNotEmpty()) append("  ·  ")
                        append("${book.pages} pagina's")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
