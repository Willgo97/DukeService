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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nl.dejongduke.service.data.Catalog
import nl.dejongduke.service.data.Procedure
import nl.dejongduke.service.ui.Card
import nl.dejongduke.service.ui.ChipRow
import nl.dejongduke.service.ui.Pill
import nl.dejongduke.service.ui.Route
import nl.dejongduke.service.ui.SectionHeader
import nl.dejongduke.service.ui.WarnBanner

private val intervalOrder = listOf("dag", "week", "maand", "halfjaar", "nodig")

private fun intervalLabel(key: String) = when (key) {
    "dag" -> "Dagelijks"
    "week" -> "Wekelijks"
    "maand" -> "Maandelijks"
    "halfjaar" -> "Halfjaarlijks"
    else -> "Wanneer nodig"
}

@Composable
fun ProcedureList(
    catalog: Catalog,
    filter: String?,
    onFilter: (String?) -> Unit,
    onOpen: (Route) -> Unit,
) {
    val documented = catalog.machines.filter { m -> catalog.procedures.any { m.id in it.machines } }
    val shown = catalog.procedures
        .filter { filter == null || filter in it.machines }
        .filter { it.stappen.isNotEmpty() }

    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            ChipRow(
                options = listOf<Pair<String?, String>>(null to "Alle machines") +
                    documented.map { it.id as String? to it.naam },
                selected = filter,
                onSelect = onFilter,
            )
        }

        intervalOrder.forEach { interval ->
            val group = shown.filter { it.interval == interval }
            if (group.isEmpty()) return@forEach
            item { SectionHeader(intervalLabel(interval), "${group.size}") }
            items(group, key = { it.id }) { proc ->
                Card(onClick = { onOpen(Route.Procedure(proc.id)) }) {
                    Column {
                        Text(proc.titel, style = MaterialTheme.typography.titleMedium)
                        if (proc.doel.isNotEmpty()) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                proc.doel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Pill("${proc.stappen.size} stappen")
                            if (proc.brewer.isNotEmpty() && proc.brewer != "beide") Pill(proc.brewer)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun ProcedureDetail(catalog: Catalog, procedure: Procedure) {
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(procedure.titel, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(intervalLabel(procedure.interval))
                    if (procedure.brewer.isNotEmpty()) Pill(procedure.brewer)
                    Pill(catalog.machineNames(procedure.machines))
                }
            }
        }

        // The chip above already says "Dagelijks"; only spell the interval out
        // when the manual adds something the chip does not cover.
        if (procedure.intervalTekst.isNotEmpty() &&
            !procedure.intervalTekst.equals(intervalLabel(procedure.interval), ignoreCase = true)
        ) {
            item {
                Text(
                    procedure.intervalTekst,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                )
            }
        }

        if (procedure.doel.isNotEmpty()) {
            item { SectionHeader("Waarom") }
            item {
                Text(
                    procedure.doel,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }

        if (procedure.nodig.isNotEmpty()) {
            item { SectionHeader("Nodig") }
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    procedure.nodig.forEach { item ->
                        Row(Modifier.padding(vertical = 3.dp)) {
                            Box(
                                Modifier.padding(top = 8.dp).size(5.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(item, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }

        if (procedure.letOp.isNotEmpty()) {
            item { Spacer(Modifier.height(12.dp)) }
            items(procedure.letOp.size) { index ->
                WarnBanner(
                    procedure.letOp[index],
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }

        if (procedure.stappen.isNotEmpty()) {
            item { SectionHeader("Stappen", "${procedure.stappen.size}") }
            items(procedure.stappen.size) { index ->
                val stap = procedure.stappen[index]
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp)) {
                    Box(
                        Modifier.size(28.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stap.tekst, style = MaterialTheme.typography.bodyLarge)
                        stap.sub.forEach { sub ->
                            Spacer(Modifier.height(6.dp))
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .padding(10.dp),
                            ) {
                                Text(
                                    sub,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}
