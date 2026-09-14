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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CleaningServices
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
    onOpen: (Route) -> Unit,
    onTab: (Tab) -> Unit,
) {
    // --- pinned -----------------------------------------------------------
    val pinned = pins.mapNotNull { key ->
        val value = key.substringAfter(':')
        when {
            key.startsWith("fault:") -> catalog.faultGroup(value)?.let {
                Triple(Icons.Filled.WarningAmber, it.message, Route.Fault(it.message) as Route)
            }
            key.startsWith("part:") -> catalog.parts.firstOrNull { it.number == value }?.let {
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
        item { SectionHeader("Vastgezet", "${pinned.size}") }
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
    item { SectionHeader("Waar wil je heen") }
    item {
        Column(Modifier.padding(horizontal = 12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile(Icons.Filled.WarningAmber, "Storingen",
                    "${catalog.faultGroups.size} meldingen", Modifier.weight(1f)) {
                    onTab(Tab.Faults)
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
                    onOpen(Route.ServiceMenu)
                }
                Tile(Icons.Filled.Build, "Onderdelen",
                    "${catalog.parts.size} regels", Modifier.weight(1f)) {
                    onTab(Tab.Parts)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile(Icons.Filled.CleaningServices, "Onderhoudskaarten",
                    "${catalog.cards.size} kaarten", Modifier.weight(1f)) {
                    onOpen(Route.Cards)
                }
                Tile(Icons.AutoMirrored.Filled.MenuBook, "Handleidingen",
                    "${catalog.books.size} boeken", Modifier.weight(1f)) {
                    onOpen(Route.Books)
                }
            }
        }
    }
}

@Composable
private fun Tile(
    icon: ImageVector,
    title: String,
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
        Text(title, style = MaterialTheme.typography.titleMedium)
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
            Pill("${catalog.procedures.count { it.steps.isNotEmpty() }} procedures")
            Pill("${catalog.cards.size} onderhoudskaarten")
            Pill("${catalog.books.size} handleidingen")
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
