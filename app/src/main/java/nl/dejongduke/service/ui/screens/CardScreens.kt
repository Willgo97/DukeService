package nl.dejongduke.service.ui.screens

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nl.dejongduke.service.R
import nl.dejongduke.service.data.Catalog
import nl.dejongduke.service.data.MaintenanceCard
import nl.dejongduke.service.ui.Card
import nl.dejongduke.service.ui.languageName
import nl.dejongduke.service.ui.appLanguage
import nl.dejongduke.service.ui.count
import nl.dejongduke.service.ui.cardTitle
import nl.dejongduke.service.ui.ChipRow
import nl.dejongduke.service.ui.EmptyState
import nl.dejongduke.service.ui.Pill
import nl.dejongduke.service.ui.Route
import nl.dejongduke.service.ui.SectionHeader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.Arrangement

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

    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            ChipRow(
                options = listOf<Pair<String?, String>>(null to stringResource(R.string.all)) + withCards.map { it.id as String? to it.name },
                selected = filter,
                onSelect = onFilter,
            )
        }
        item {
            Text(
                stringResource(R.string.the_manufacturer_s_maintenance_card_step_by),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        if (cards.isEmpty()) {
            item { EmptyState(stringResource(R.string.no_card), stringResource(R.string.there_is_no_maintenance_card_for_this_machin)) }
        }
        maintenanceCards(catalog, cards, filter, onOpen)
        item { Spacer(Modifier.height(96.dp)) }
    }
}

/**
 * The maintenance cards, grouped by whatever is not already decided.
 *
 * Each card carries a machine, an interval and a build, and every card is
 * titled "Daily maintenance" or "Regular maintenance" — so the title line is
 * worthless and the grouping has to carry the meaning. With a machine picked,
 * the interval groups and the build names the row; with all machines in the
 * list, the machine groups and the interval goes on the row instead.
 */
fun LazyListScope.maintenanceCards(
    catalog: Catalog,
    cards: List<MaintenanceCard>,
    filter: String?,
    onOpen: (Route) -> Unit,
) {
    if (filter == null) {
        cards.groupBy { it.machines.firstOrNull().orEmpty() }.forEach { (id, group) ->
            item(key = "m:$id") { SectionHeader(catalog.machine(id)?.name ?: id, "${group.size}") }
            items(group, key = { it.id }) { card ->
                MaintenanceCardRow(catalog, card, showInterval = true, onOpen = onOpen)
            }
        }
    } else {
        cards.groupBy { it.interval }.forEach { (interval, group) ->
            item(key = "i:$interval") { SectionHeader(intervalName(interval), "${group.size}") }
            items(group, key = { it.id }) { card ->
                MaintenanceCardRow(catalog, card, showInterval = false, onOpen = onOpen)
            }
        }
    }
}

@Composable
fun MaintenanceCardRow(
    catalog: Catalog,
    card: MaintenanceCard,
    showInterval: Boolean,
    onOpen: (Route) -> Unit,
) {
    val build = card.codes.joinToString(", ") {
        catalog.variantLabel(card.machines.firstOrNull(), it)
    }
    Card(onClick = { onOpen(Route.MaintenanceCard(card.id)) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    build.ifEmpty { catalog.machineNames(card.machines) },
                    style = MaterialTheme.typography.titleMedium,
                )
                if (showInterval) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        intervalName(card.interval),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                count(R.plurals.n_steps, card.steps.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun intervalName(interval: String) = when (interval) {
    "daily" -> stringResource(R.string.daily)
    "weekly" -> stringResource(R.string.weekly)
    "monthly" -> stringResource(R.string.monthly)
    "quarterly" -> stringResource(R.string.quarterly)
    "yearly" -> stringResource(R.string.yearly)
    "regular" -> stringResource(R.string.regular_maintenance)
    else -> stringResource(R.string.other)
}

@Composable
fun CardDetail(catalog: Catalog, card: MaintenanceCard, onOpen: (Route) -> Unit) {
    // Half the cards open with an unnumbered block: what the sheet says before
    // step one, plus the safety notes. It is not a step, so it does not get a
    // number and it does not look like one.
    val intro = card.steps.firstOrNull()?.takeIf { it.number.isEmpty() }
    val steps = card.steps.drop(if (intro != null) 1 else 0)

    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(cardTitle(card.title), style = MaterialTheme.typography.headlineSmall,
                     fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    listOfNotNull(
                        catalog.machineNames(card.machines).ifEmpty { null },
                        card.codes.joinToString(", ") {
                            catalog.variantLabel(card.machines.firstOrNull(), it)
                        }.ifEmpty { null },
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // The sheets were printed in one language per machine; say
                // which, unless it is the one being read.
                if (card.language != appLanguage()) {
                    Spacer(Modifier.height(8.dp))
                    Pill(languageName(card.language))
                }
                Spacer(Modifier.height(14.dp))
                FilledTonalButton(onClick = { onOpen(Route.Steps("card", card.id)) }) {
                    Icon(Icons.Filled.PlayArrow, null, Modifier.height(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.step_by_step))
                }
            }
        }

        if (intro != null) {
            item {
                Card {
                    Column {
                        intro.points.forEach { point ->
                            Text(point, style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(8.dp))
                        }
                        if (intro.notes.isNotEmpty()) NoteBox(intro.notes)
                        // Whatever hangs under the intro is the maker's QR code
                        // to the user manual, not something you work from.
                        intro.images.forEach { image ->
                            Spacer(Modifier.height(8.dp))
                            AssetImage(image, maxHeight = 140.dp)
                        }
                    }
                }
            }
        }

        items(steps.size) { index ->
            val step = steps[index]
            Card {
                Column {
                    Row(verticalAlignment = Alignment.Top) {
                        if (step.number.isNotEmpty()) {
                            StepNumber(step.number)
                            Spacer(Modifier.width(14.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            step.points.forEachIndexed { at, point ->
                                if (at > 0) Spacer(Modifier.height(6.dp))
                                Text(
                                    // One instruction is a sentence, several are
                                    // a list; only the list needs bullets.
                                    if (step.points.size > 1) stringResource(R.string.text, point) else point,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    }
                    if (step.notes.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        NoteBox(step.notes)
                    }
                    step.images.forEach { image ->
                        Spacer(Modifier.height(12.dp))
                        AssetImage(image)
                    }
                }
            }
        }
        item {
            Text(
                stringResource(R.string.source_if_in_doubt_the_card_inside_the_machi, card.source),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            )
        }
    }
}

/** The step number, as it is printed on the card: in a circle beside the text. */
@Composable
private fun StepNumber(number: String) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            number,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/**
 * The warnings off the card, in one block.
 *
 * A daily card carries five of them, the same five every time. Five separate
 * panels is five times the weight the same boilerplate deserves.
 */
@Composable
private fun NoteBox(notes: List<String>) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        notes.forEach { note ->
            Text(
                note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
