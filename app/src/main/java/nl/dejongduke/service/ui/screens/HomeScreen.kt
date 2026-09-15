package nl.dejongduke.service.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import nl.dejongduke.service.R
import nl.dejongduke.service.data.Catalog
import nl.dejongduke.service.ui.Card
import nl.dejongduke.service.ui.count
import nl.dejongduke.service.ui.Pill
import nl.dejongduke.service.ui.Route
import nl.dejongduke.service.ui.SectionHeader
import nl.dejongduke.service.ui.Tab

/**
 * What the app shows before you have typed anything: the work in front of you,
 * the things you pinned, and a way into every corner of the catalog.
 */
@OptIn(ExperimentalLayoutApi::class)
fun LazyListScope.homeSections(
    catalog: Catalog,
    pins: List<String>,
    filter: String?,
    variant: String?,
    onFilter: (String?) -> Unit,
    onOpen: (Route) -> Unit,
    onTab: (Tab) -> Unit,
) {
    // --- the machine in front of you --------------------------------------
    // Scanning the type plate, or picking a machine anywhere in the app, points
    // everything at it. Say so on the way in, and offer the four things that
    // are then one tap away instead of four.
    val machine = filter?.let { catalog.machine(it) }
    if (machine != null) {
        item { SectionHeader(stringResource(R.string.this_machine)) }
        item {
            Card {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(machine.name, style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(2.dp))
                            val build = variant?.let { code ->
                                machine.variants.firstOrNull { it.code == code }
                            }
                            Text(
                                if (build != null) {
                                    listOfNotNull(
                                        "${build.brewer} ${build.cabinet}".trim(),
                                        build.code,
                                        build.doc.ifEmpty { null },
                                    ).joinToString("  ·  ")
                                } else {
                                    val brewers = machine.variants.map { it.brewer }
                                        .filter { it.isNotEmpty() }.distinct()
                                    listOfNotNull(
                                        count(R.plurals.n_variants, machine.variants.size)
                                            .takeIf { machine.variants.isNotEmpty() },
                                        brewers.joinToString(", ").ifEmpty { null },
                                    ).joinToString("  ·  ")
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                        }
                        TextButton(onClick = { onFilter(null) }) { Text(stringResource(R.string.all)) }
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tile(Icons.Filled.CleaningServices, stringResource(R.string.maintenance),
                         count(R.plurals.n_cards, catalog.cardsFor(machine.id, variant).size),
                         Modifier.weight(1f)) { onTab(Tab.Maintenance) }
                    Tile(Icons.Filled.Build, stringResource(R.string.parts),
                         count(R.plurals.n_rows, catalog.partCount(machine.id, variant)),
                         Modifier.weight(1f)) { onTab(Tab.Parts) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tile(Icons.Filled.WarningAmber, stringResource(R.string.faults),
                         count(R.plurals.n_messages, catalog.faultGroups.count { group ->
                             machine.id in group.machines &&
                                 group.variants.any { catalog.forVariant(it.codes, variant) }
                         }),
                         Modifier.weight(1f)) { onTab(Tab.Faults) }
                    Tile(Icons.Filled.Memory, stringResource(R.string.technical),
                         count(R.plurals.n_parts, catalog.components.count { machine.id in it.machines }),
                         Modifier.weight(1f)) { onOpen(Route.Components) }
                }
            }
        }
    }

    // --- pinned -----------------------------------------------------------
    val pinned = pins.mapNotNull { key ->
        val value = key.substringAfter(':')
        when {
            key.startsWith("fault:") -> catalog.faultGroup(value)?.let {
                Triple(Icons.Filled.WarningAmber, it.message, Route.Fault(it.message) as Route)
            }
            key.startsWith("part:") -> catalog.part(value)?.let {
                Triple(Icons.Filled.Build, "${it.number} · ${it.description}",
                    Route.PartSection(it.machine, it.variant, it.section) as Route)
            }
            key.startsWith("proc:") -> catalog.procedure(value)?.let {
                Triple(Icons.AutoMirrored.Filled.ListAlt, it.title, Route.Procedure(it.id) as Route)
            }
            else -> null
        }
    }
    if (pinned.isNotEmpty()) {
        item { SectionHeader(stringResource(R.string.pinned), "${pinned.size}") }
        items(pinned.size) { index ->
            val (icon, title, route) = pinned[index]
            Card(onClick = { onOpen(route) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                }
            }
        }
    }

    // --- the four ways in -------------------------------------------------
    item { SectionHeader(if (machine == null) stringResource(R.string.where_do_you_want_to_go) else stringResource(R.string.everything)) }
    item {
        Column(Modifier.padding(horizontal = 12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile(Icons.Filled.WarningAmber, stringResource(R.string.faults),
                    count(R.plurals.n_messages, catalog.faultGroups.size), Modifier.weight(1f)) {
                    onTab(Tab.Faults)
                }
                Tile(Icons.Filled.Memory, stringResource(R.string.technical),
                    count(R.plurals.n_parts, catalog.components.size), Modifier.weight(1f)) {
                    onOpen(Route.Components)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile(Icons.Filled.Tune, stringResource(R.string.service_menu),
                    count(R.plurals.n_topics, catalog.menu.size), Modifier.weight(1f)) {
                    onOpen(Route.ServiceMenu)
                }
                Tile(Icons.Filled.Build, stringResource(R.string.parts),
                    // The parts table is read after the app is already usable;
                    // "0 rows" would read as an empty book.
                    if (catalog.parts.isEmpty()) stringResource(R.string.working)
                    else count(R.plurals.n_rows, catalog.parts.size), Modifier.weight(1f)) {
                    onTab(Tab.Parts)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile(Icons.Filled.CleaningServices, stringResource(R.string.maintenance_cards),
                    count(R.plurals.n_cards, catalog.cards.size), Modifier.weight(1f)) {
                    onOpen(Route.Cards)
                }
                Tile(Icons.Filled.Straighten, stringResource(R.string.technical_data),
                    count(R.plurals.n_tables, catalog.specs.size), Modifier.weight(1f)) {
                    onOpen(Route.Specs)
                }
            }
        }
    }
}

@Composable
private fun Tile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(2.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A short "what is in here" line for the bottom of the home screen. */
@OptIn(ExperimentalLayoutApi::class)
fun LazyListScope.catalogSummary(catalog: Catalog) {
    item { SectionHeader(stringResource(R.string.in_this_app)) }
    item {
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Pill(count(R.plurals.n_faults, catalog.faultGroups.size))
            Pill(count(R.plurals.n_procedures, catalog.procedures.count { it.steps.isNotEmpty() }))
            Pill(count(R.plurals.n_maintenance_cards, catalog.cards.size))
            Pill(count(R.plurals.n_parts, catalog.components.size))
            Pill(count(R.plurals.n_topics, catalog.menu.size))
            Pill(count(R.plurals.n_parts, catalog.parts.size))
            Pill(stringResource(R.string.machines_2, catalog.machines.size))
        }
    }
    item {
        Text(
            stringResource(R.string.all_offline_built_from_the_manuals_spare_par),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        )
    }
}
