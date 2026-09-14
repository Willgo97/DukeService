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
                options = listOf<Pair<String?, String>>(null to stringResource(R.string.alle)) + withCards.map { it.id as String? to it.name },
                selected = filter,
                onSelect = onFilter,
            )
        }
        item {
            Text(
                stringResource(R.string.de_onderhoudskaart_van_de_fabrikant_stap_voo),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        if (cards.isEmpty()) {
            item { EmptyState(stringResource(R.string.geen_kaart), stringResource(R.string.voor_deze_machine_zit_er_geen_onderhoudskaar)) }
        }
        for ((interval, group) in perInterval) {
            item { SectionHeader(intervalName(interval), "${group.size}") }
            items(group, key = { it.id }) { card ->
                Card(onClick = { onOpen(Route.MaintenanceCard(card.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(cardTitle(card.title), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                buildString {
                                    append(catalog.machineNames(card.machines))
                                    if (card.codes.isNotEmpty()) {
                                        append("  ·  " + card.codes.joinToString(", ") {
                                            catalog.variantLabel(card.machines.firstOrNull(), it)
                                        })
                                    }
                                    append("  \u00b7  ")
                                    append(count(R.plurals.n_steps, card.steps.size))
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

@Composable
fun intervalName(interval: String) = when (interval) {
    "dag" -> stringResource(R.string.dagelijks)
    "week" -> stringResource(R.string.wekelijks)
    "maand" -> stringResource(R.string.maandelijks)
    "kwartaal" -> stringResource(R.string.per_kwartaal)
    "jaar" -> stringResource(R.string.jaarlijks)
    "periodiek" -> stringResource(R.string.periodiek_onderhoud)
    else -> stringResource(R.string.overig)
}

@Composable
fun CardDetail(catalog: Catalog, card: MaintenanceCard, onOpen: (Route) -> Unit) {
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(cardTitle(card.title), style = MaterialTheme.typography.headlineSmall,
                     fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(intervalName(card.interval))
                    Pill(catalog.machineNames(card.machines))
                    // The sheets were printed in one language per machine;
                    // say which, unless it is the one being read.
                    if (card.language != appLanguage()) Pill(languageName(card.language))
                }
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = { onOpen(Route.Steps("card", card.id)) }) {
                    Icon(Icons.Filled.PlayArrow, null, Modifier.height(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.stap_voor_stap))
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
                                Text(stringResource(R.string.x, punt), style = MaterialTheme.typography.bodyLarge)
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
                stringResource(R.string.bron_x_bij_twijfel_is_de_kaart_in_de_machine, card.source),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            )
        }
    }
}
