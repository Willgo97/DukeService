package nl.dejongduke.service.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import nl.dejongduke.service.data.Catalog
import nl.dejongduke.service.ui.Card
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
    ticks: Map<String, Set<Int>>,
    onOpen: (Route) -> Unit,
    onTab: (Tab) -> Unit,
) {
    // --- pinned -----------------------------------------------------------
    val pinned = pins.mapNotNull { key ->
        val value = key.substringAfter(':')
        when {
            key.startsWith("fault:") -> catalog.faultGroup(value)?.let {
                Triple(Icons.Filled.WarningAmber, it.melding, Route.Fault(it.melding) as Route)
            }
            key.startsWith("part:") -> catalog.parts.firstOrNull { it.nummer == value }?.let {
                Triple(Icons.Filled.Build, "${it.nummer} · ${it.omschrijving}",
                    Route.PartSection(it.machine, it.sectie) as Route)
            }
            key.startsWith("proc:") -> catalog.procedure(value)?.let {
                Triple(Icons.AutoMirrored.Filled.ListAlt, it.titel, Route.Procedure(it.id) as Route)
            }
            else -> null
        }
    }
    if (pinned.isNotEmpty()) {
        item { SectionHeader("Vastgezet", "${pinned.size}") }
        items(pinned.size) { index ->
            val (icon, titel, route) = pinned[index]
            Card(onClick = { onOpen(route) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(titel, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                }
            }
        }
    }

    // --- today's checklists ----------------------------------------------
    val lopend = catalog.schemas.filter { (ticks[it.id]?.size ?: 0) > 0 }
    if (lopend.isNotEmpty()) {
        item { SectionHeader("Vandaag bezig", "${lopend.size}") }
        items(lopend.size) { index ->
            val schema = lopend[index]
            val done = ticks[schema.id].orEmpty().size
            val total = schema.taken.size
            Card(onClick = { onOpen(Route.Schema(schema.id)) }) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(schema.titel, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(
                            "$done/$total",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (done == total) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${schema.brewer}  ·  ${catalog.machineNames(schema.machines)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { if (total == 0) 0f else done.toFloat() / total },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = if (done == total) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        drawStopIndicator = {},
                    )
                }
            }
        }
    }

    // --- the four ways in -------------------------------------------------
    item { SectionHeader("Waar wil je heen") }
    item {
        Column(Modifier.padding(horizontal = 12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile(Icons.Filled.WarningAmber, "Storingen",
                    "${catalog.faultGroups.size} meldingen", Modifier.weight(1f)) {
                    onTab(Tab.Storingen)
                }
                Tile(Icons.Filled.Memory, "Techniek",
                    "${catalog.components.size} onderdelen", Modifier.weight(1f)) {
                    onOpen(Route.Components)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile(Icons.Filled.Tune, "Servicemenu",
                    "${catalog.menu.size} onderwerpen", Modifier.weight(1f)) {
                    onOpen(Route.Servicemenu)
                }
                Tile(Icons.Filled.Build, "Onderdelen",
                    "${catalog.parts.size} regels", Modifier.weight(1f)) {
                    onTab(Tab.Onderdelen)
                }
            }
        }
    }
}

@Composable
private fun Tile(
    icon: ImageVector,
    titel: String,
    onder: String,
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
        Text(titel, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(2.dp))
        Text(
            onder,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A short "what is in here" line for the bottom of the home screen. */
@OptIn(ExperimentalLayoutApi::class)
fun LazyListScope.catalogSummary(catalog: Catalog) {
    item { SectionHeader("In deze app") }
    item {
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Pill("${catalog.faultGroups.size} storingen")
            Pill("${catalog.procedures.count { it.stappen.isNotEmpty() }} procedures")
            Pill("${catalog.schemas.size} checklists")
            Pill("${catalog.components.size} componenten")
            Pill("${catalog.menu.size} servicemenu")
            Pill("${catalog.parts.size} onderdelen")
            Pill("${catalog.machines.size} machines")
        }
    }
    item {
        Text(
            "Alles offline. Gebouwd uit de handleidingen, onderdelenboeken en technische documentatie.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        )
    }
}
