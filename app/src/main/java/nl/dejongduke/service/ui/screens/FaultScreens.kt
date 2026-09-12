package nl.dejongduke.service.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nl.dejongduke.service.data.Catalog
import nl.dejongduke.service.data.Fault
import nl.dejongduke.service.data.FaultGroup
import nl.dejongduke.service.data.LetOp
import nl.dejongduke.service.ui.ActionRow
import nl.dejongduke.service.ui.Card
import nl.dejongduke.service.ui.ChipRow
import nl.dejongduke.service.ui.EmptyState
import nl.dejongduke.service.ui.Pill
import nl.dejongduke.service.ui.Route
import nl.dejongduke.service.ui.SectionHeader
import nl.dejongduke.service.ui.WarnBanner

@Composable
fun FaultsScreen(
    catalog: Catalog,
    filter: String?,
    taal: String,
    onFilter: (String?) -> Unit,
    onOpen: (Route) -> Unit,
) {
    var categorie by remember { mutableStateOf<String?>(null) }

    val documented = catalog.machines.filter { m -> catalog.faults.any { m.id in it.machines } }
    val byMachine = catalog.faultGroups.filter { filter == null || filter in it.machines }
    val categories = byMachine.map { it.eerste.cat }.distinct().sorted()
    // A category picked for one machine may not exist for the next one; leaving
    // it set would show an empty list with no chip to explain why.
    if (categorie != null && categorie !in categories) categorie = null
    val shown = byMachine.filter { categorie == null || it.eerste.cat == categorie }

    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            ChipRow(
                options = listOf<Pair<String?, String>>(null to "Alle machines") +
                    documented.map { it.id as String? to it.naam },
                selected = filter,
                onSelect = onFilter,
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
        item {
            ChipRow(
                options = listOf<Pair<String?, String>>(null to "Alles") + categories.map { it as String? to it },
                selected = categorie,
                onSelect = { categorie = it },
            )
        }
        item { SectionHeader("Schermmeldingen", "${shown.size}") }

        if (shown.isEmpty()) {
            val naam = filter?.let { catalog.machine(it)?.naam }
            item {
                EmptyState(
                    "Geen meldingen",
                    if (naam != null) "Voor de $naam staat nog geen storingslijst in de app."
                    else "Er staat nog geen storingslijst in de app.",
                )
            }
        }

        items(shown, key = { it.melding }) { group ->
            FaultCard(catalog, group, showMachines = filter == null, taal = taal) {
                onOpen(Route.Fault(group.melding))
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun FaultCard(
    catalog: Catalog,
    group: FaultGroup,
    showMachines: Boolean,
    taal: String = "nl",
    onClick: () -> Unit,
) {
    // Which line leads depends on what the machine in front of you displays.
    val kop = if (taal == "en") group.melding else group.eerste.nl
    val onder = if (taal == "en") group.eerste.nl else group.melding
    Card(onClick = onClick) {
        Column {
            Row {
                Box(
                    Modifier.padding(top = 7.dp).size(8.dp).clip(CircleShape)
                        .background(
                            if (group.zelf) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.error
                        )
                )
                Spacer(Modifier.width(10.dp))
                Text(kop, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                onder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 18.dp),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.padding(start = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Pill(group.eerste.cat)
                if (showMachines) Pill(catalog.machineNames(group.machines))
                if (!group.zelf) Pill("monteur", tone = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun FaultDetail(
    catalog: Catalog,
    group: FaultGroup,
    pinned: Boolean,
    taal: String,
    onPin: (String) -> Unit,
    onOpen: (Route) -> Unit,
) {
    val kop = if (taal == "en") group.melding else group.eerste.nl
    val onder = if (taal == "en") group.eerste.nl else group.melding
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(
                    "Op het scherm",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(kop, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    onder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(group.eerste.cat)
                    Pill(catalog.machineNames(group.machines))
                }
            }
        }

        item {
            ActionRow(
                pinKey = "fault:" + group.melding,
                pinned = pinned,
                onPin = onPin,
                deelTekst = buildString {
                    appendLine(group.melding)
                    appendLine(group.eerste.nl)
                    if (group.eerste.oorzaak.isNotEmpty()) appendLine("\nOorzaak: " + group.eerste.oorzaak)
                    group.eerste.oplossing.forEachIndexed { i, stap -> appendLine("${i + 1}. $stap") }
                    append("\n— DUKE Service")
                },
            )
        }

        group.varianten.forEachIndexed { index, variant ->
            if (group.varianten.size > 1) {
                item {
                    if (index > 0) {
                        HorizontalDivider(
                            Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    SectionHeader("Voor ${catalog.machineNames(variant.machines)}")
                }
            }
            faultBody(variant, catalog, onOpen)
        }
        item {
            val bron = group.varianten.mapNotNull { it.bron.ifEmpty { null } }.distinct()
            if (bron.isNotEmpty()) {
                Text(
                    "Bron: " + bron.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

/** The cause / steps / engineer-note block for one variant of a message. */
private fun androidx.compose.foundation.lazy.LazyListScope.faultBody(
    fault: Fault,
    catalog: Catalog,
    onOpen: (Route) -> Unit,
) {
    if (fault.oorzaak.isNotEmpty()) {
        item { SectionHeader("Oorzaak") }
        item {
            Text(
                fault.oorzaak,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
            )
        }
    }

    if (fault.oplossing.isNotEmpty()) {
        item { SectionHeader(if (fault.zelf) "Wat je doet" else "Wat er moet gebeuren") }
        items(fault.oplossing.size) { index ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                Box(
                    Modifier.size(24.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(fault.oplossing[index], style = MaterialTheme.typography.bodyLarge)
            }
        }
    }

    if (fault.monteur.isNotEmpty()) {
        item {
            Card {
                Column {
                    Text(
                        if (fault.zelf) "Als het blijft" else "Servicemelding",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(fault.monteur, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    if (fault.opmerking.isNotEmpty()) {
        item {
            WarnBanner(
                LetOp("let op", fault.opmerking),
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }

    val linked = fault.proc.mapNotNull { catalog.procedure(it) }
    if (linked.isNotEmpty()) {
        item { SectionHeader("Bijbehorende procedures") }
        items(linked, key = { fault.melding + it.id }) { proc ->
            Card(onClick = { onOpen(Route.Procedure(proc.id)) }) {
                Column {
                    Text(proc.titel, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${proc.stappen.size} stappen" +
                            if (proc.brewer.isNotEmpty()) "  ·  ${proc.brewer}" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
